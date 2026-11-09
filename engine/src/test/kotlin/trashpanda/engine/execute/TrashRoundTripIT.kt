package trashpanda.engine.execute

import trashpanda.engine.execute.Status.RECYCLED
import trashpanda.engine.execute.Status.RESTORED
import trashpanda.engine.report.Sizes
import trashpanda.platform.io.BinIndex
import trashpanda.platform.winapi.ShellOp
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DESIGN §8 invariant 5, executed for real: recycle a file -> find its bin entry ->
 * restore -> byte-identical content and preserved mtime. This is the one test that
 * would catch "FOF_ALLOWUNDO missing" where everything else would stay green.
 *
 * Soft-skip is allowed ONLY when the OS itself refuses the recycle (bin disabled) —
 * never on an assertion failure.
 */
class TrashRoundTripIT {

    private val work = Files.createTempDirectory("tp-roundtrip")

    @AfterTest
    fun cleanup() { work.toFile().deleteRecursively() }

    @Test
    fun `recycle then undo is byte-identical`() {
        val original = work.resolve("topic.txt")
        val content = "round trip through the bin at ${System.nanoTime()}".toByteArray()
        Files.write(original, content)
        val beforeMtime = Files.readAttributes(original, BasicFileAttributes::class.java).lastModifiedTime()

        // 1. move it into the real Recycle Bin, exactly as the executor would
        val shell = ShellOp.recycleToBin(original.toString())
        if (!shell.ok) {
            println("soft skip: OS refused recycle (code ${shell.code}, aborted ${shell.aborted}) - bin disabled?")
            return
        }
        assertTrue(!Files.exists(original), "file must be gone from disk after a successful bin move")

        // diagnostic: show what the bin actually recorded, right after the move
        val drive = Path.of(original.toString().take(3))
        BinIndex.readableBinFolders(drive)
            .asSequence()
            .flatMap { BinIndex.entries(it) }
            .filter { it.originalPath.contains("tp-roundtrip") }
            .forEach { println("bin recorded: ${it.originalPath}") }

        // 2. build a receipt as the executor would have
        val receiptDir = Files.createTempDirectory("tp-roundtrip-receipts")
        val store = ReceiptStore(receiptDir)
        val item = ReceiptItem(
            originalPath = original.toString(),
            sizeBytes = content.size.toLong(),
            ruleId = "D1",
            ruleTitle = "test",
            verdict = "trash",
            risk = "green",
            status = RECYCLED,
        )
        val receiptFile = store.write(listOf(item))

        // 3. restore from the bin
        val undone = Restorer(store).restore(receiptFile)
        val restored = undone.items.single()
        assertEquals(RESTORED, restored.status, restored.error ?: "restored cleanly")

        assertEquals(-1L, Files.mismatch(original, Path.of(restored.restoredTo!!)), "byte-identical")
        assertTrue(original.readBytes().contentEquals(content), "exact bytes home")
        val afterMtime = Files.readAttributes(original, BasicFileAttributes::class.java).lastModifiedTime()
        assertTrue(
            beforeMtime.toMillis() == afterMtime.toMillis(),
            "mtime preserved by the bin round trip (not a copy)",
        )
        println("round trip OK - ${Sizes.human(content.size.toLong())} went to the bin and came home")
        receiptDir.toFile().deleteRecursively()
    }
}
