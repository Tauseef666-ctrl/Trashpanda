package trashpanda.engine.execute

import trashpanda.platform.io.BinIndex
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Undo invariants (DESIGN §8 test 5): bin entry -> restore -> byte-identical content
 * at the original path, nothing overwritten, receipt flipped to "undone".
 * Hermetic: the "bin" is a temp fixture with real $I/$R files, injected into the restorer.
 */
class RestorerTest {

    private val work = Files.createTempDirectory("tp-undo-work") // stands in for the original home dir
    private val fakeBin = Files.createTempDirectory("tp-undo-fakebin") // stands in for X:\$Recycle.Bin\<SID>
    private val store = ReceiptStore(Files.createTempDirectory("tp-undo-receipts"))
    private val tempReceipts: Path get() = store.dir

    @AfterTest
    fun cleanup() {
        work.toFile().deleteRecursively()
        fakeBin.toFile().deleteRecursively()
        tempReceipts.toFile().deleteRecursively()
    }

    /** Writes a real v2 $I file plus its $R twin. Empty content simulates a binned DIRECTORY. */
    private fun binEntry(id: String, original: String, content: ByteArray = ByteArray(0)) {
        val iFile = fakeBin.resolve("$I_PREFIX$id.tmp")
        val rFile = iFile.resolveSibling("$R_PREFIX$id.tmp")
        Files.write(iFile, v2IBytes(original, if (content.isEmpty()) 64 else content.size.toLong(), Instant.now()))
        if (content.isEmpty()) {
            Files.createDirectories(rFile)
        } else {
            Files.write(rFile, content)
        }
    }

    private fun receiptOf(vararg items: ReceiptItem): Receipt =
        Receipt("undo-test-1", Instant.now().toString(), items.sumOf { it.sizeBytes }, 0, items.toList())

    @Test
    fun `a recycled item goes home byte-identical`() {
        val content = "grrr - lazy trash".toByteArray()
        val original = work.resolve("old-stuff").resolve("readme.txt")
        Files.createDirectories(original.parent!!)
        binEntry(id = "1111111", original = original.toString(), content = content)

        val receipt = receiptOf(itemFor(original))
        val result = Restorer(store) { listOf(fakeBin) }.restore(receipt)

        val item = result.items.single()
        assertEquals(Status.RESTORED, item.status)
        assertEquals(original.toString(), item.restoredTo)
        assertTrue(original.readBytes().contentEquals(content), "bytes home exactly")
        assertNull(item.error, item.error ?: "no error on success")
        assertEquals(0, count(iPrefix = true), "the ${'$'}I stub was cleaned up after restore")
    }

    @Test
    fun `an item whose bin entry is missing fails with a plain explanation`() {
        val receipt = receiptOf(itemFor(work.resolve("nowhere").resolve("ghost.bin")))
        val result = Restorer(store) { listOf(fakeBin) }.restore(receipt)
        val item = result.items.single()
        assertEquals(Status.FAILED, item.status)
        assertTrue(item.error!!.contains("not in the Recycle Bin"), item.error)
    }

    @Test
    fun `taken slots come back with a (2) suffix - never overwritten`() {
        val content = "payload-2".toByteArray()
        val original = work.resolve("docs").resolve("same-name.txt")
        Files.createDirectories(original.parent!!)
        Files.write(original, "occupied".toByteArray()) // the destination slot is taken
        binEntry(id = "2222222", original = original.toString(), content = content)

        val result = Restorer(store) { listOf(fakeBin) }.restore(receiptOf(itemFor(original)))
        val item = result.items.single()
        assertEquals(Status.RESTORED, item.status)

        assertTrue(original.readBytes().contentEquals("occupied".toByteArray()), "occupant untouched - no overwrite")
        val neighbor = Path.of(item.restoredTo!!)
        assertTrue(neighbor.readBytes().contentEquals(content), "content restored beside it as (2)")
        assertTrue(neighbor.fileName.toString().contains("(2)"), "collision naming per DESIGN: ${neighbor}")
    }

    @Test
    fun `parent folders come home before their children`() {
        val parent = work.resolve("flakes")
        val child = parent.resolve("deep")
        binEntry(id = "3333333", original = parent.toString()) // parent binned as a DIRECTORY
        binEntry(id = "4444444", original = child.toString(), content = "child-data".toByteArray())

        // receipt lists the child FIRST; the restorer must flip the order itself
        val receipt = Receipt(
            id = "order-test",
            createdAtIso = Instant.now().toString(),
            freedBytes = 0,
            failedBytes = 0,
            items = listOf(itemFor(child), itemFor(parent)),
        )
        val result = Restorer(store) { listOf(fakeBin) }.restore(receipt)
        assertEquals(2, result.items.count { it.status == Status.RESTORED })
        assertTrue(Files.isDirectory(parent), "parent home as a directory")
        assertTrue(Files.exists(child), "child landed inside its restored parent")
        assertEquals("child-data".toByteArray().size.toLong(), Files.size(child))
    }

    @Test
    fun `the bin entry must declare the receipt's exact original path`() {
        // a bin entry whose $I points somewhere ELSE must never be moved to our target
        val decoy = work.resolve("unrelated").resolve("victim.data")
        Files.createDirectories(decoy.parent!!)
        Files.write(decoy, "real-data".toByteArray())
        binEntry(id = "5555555", original = decoy.toString(), content = "bin-copy".toByteArray())
        val wanted = work.resolve("wanted.txt")
        val receipt = receiptOf(itemFor(wanted))
        val result = Restorer(store) { listOf(fakeBin) }.restore(receipt)
        val item = result.items.single()
        assertEquals(Status.FAILED, item.status, "mismatched bin entry refused")
        assertTrue(!Files.exists(wanted), "nothing appeared at the wanted slot")
        assertEquals(1, count(iPrefix = false), "the decoy's ${'$'}R twin stayed untouched in the bin")
    }

    // ---- helpers ----------------------------------------------------------

    private fun itemFor(path: Path) = ReceiptItem(
        originalPath = path.toString(),
        sizeBytes = 10,
        ruleId = "D1",
        ruleTitle = "test",
        verdict = "trash",
        risk = "green",
        status = Status.RECYCLED,
    )

    private fun count(iPrefix: Boolean): Int = Files.list(fakeBin).use { stream ->
        stream.map { it as Path }
            .filter { it.fileName.toString().startsWith(if (iPrefix) I_PREFIX else R_PREFIX) }
            .count()
            .toInt()
    }

    companion object {
        const val I_PREFIX = "\$I"
        const val R_PREFIX = "\$R"

        /** v2 $I bytes: [0..3 ver=2][4..7 zero][8..15 size][16..23 FILETIME][24..27 chars][28.. UTF-16LE path+NUL] */
        fun v2IBytes(originalPath: String, size: Long, deletedAt: Instant): ByteArray {
            val path = originalPath + "\u0000"
            val utf16 = path.toByteArray(Charsets.UTF_16LE)
            val fileTime = (deletedAt.toEpochMilli() + 11_644_473_600_000) * 10_000
            val buf = ByteBuffer.allocate(28 + utf16.size).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(2)
            buf.putInt(0)
            buf.putLong(size)
            buf.putLong(fileTime)
            buf.putInt(path.length)
            buf.put(utf16)
            return buf.array()
        }
    }
}

/** Round-trips metadata through the real BinIndex parser, keeping both sides honest. */
class RestorerParserCrossTest {
    private val fakeBin = Files.createTempDirectory("tp-cross-bin")
    private val work = Files.createTempDirectory("tp-cross-work")

    @AfterTest
    fun cleanup() {
        fakeBin.toFile().deleteRecursively()
        work.toFile().deleteRecursively()
    }

    @Test
    fun `BinIndex reads a fixture $I the same way the restorer wrote it`() {
        val target = work.resolve("spirit").resolve("keepsake.md")
        val bytes = RestorerTest.v2IBytes(target.toString(), 511, Instant.parse("2026-01-02T03:04:05Z"))
        val iFile = fakeBin.resolve("\$Icross1.tmp")
        val rFile = iFile.resolveSibling("\$Rcross1.tmp")
        Files.write(iFile, bytes)
        Files.write(rFile, "content".toByteArray())

        val entry = BinIndex.entries(fakeBin).single()
        assertEquals(target.toString(), entry.originalPath)
        assertEquals(511, entry.sizeBytes)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z").toEpochMilli(), entry.deletedAt!!.toEpochMilli())
    }
}
