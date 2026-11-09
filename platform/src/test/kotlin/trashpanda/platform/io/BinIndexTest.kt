package trashpanda.platform.io

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BinIndexTest {

    private val fakeDrive = Files.createTempDirectory("tp-drive")
    private val fakeBin = Files.createDirectories(
        fakeDrive.resolve("\$Recycle.Bin").resolve("S-1-5-21-10001-10002-10003-1004"),
    )

    @AfterTest
    fun cleanup() { fakeDrive.toFile().deleteRecursively() }

    @Test
    fun `parses v2 I-files - size, delete time, original path`() {
        val path = "C:\\Users\\tester\\AppData\\Local\\Temp\\junk.txt"
        val deletedAt = Instant.parse("2026-02-03T04:05:06Z")
        val bytes = v2I(path, size = 77_000, deletedAt = deletedAt)

        val parsed = BinIndex.parseIFile(bytes)

        assertEquals(path, parsed!!.originalPath)
        assertEquals(77_000, parsed.sizeBytes)
        assertEquals(deletedAt.toEpochMilli(), parsed.deletedAt!!.toEpochMilli())
    }

    @Test
    fun `parses legacy v1 I-files (ANSI path)`() {
        val path = "C:\\temp\\legacy.txt"
        val buf = ByteBuffer.allocate(24 + path.length + 1).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1) // version
        buf.putInt(0)
        buf.putInt(1200) // u32 size
        buf.putLong((Instant.now().toEpochMilli() + 11_644_473_600_000) * 10_000)
        buf.putInt(0) // pad
        buf.put(path.toByteArray(Charsets.ISO_8859_1))
        buf.put(0)

        val parsed = BinIndex.parseIFile(buf.array())
        assertEquals(path, parsed!!.originalPath)
        assertEquals(1200, parsed.sizeBytes)
    }

    @Test
    fun `unknown versions and truncated junk are skipped, not crashed on`() {
        assertNull(BinIndex.parseIFile(ByteArray(10)))
        val buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(3) // version nobody knows
        assertNull(BinIndex.parseIFile(buf.array()))
        assertNull(BinIndex.parseIFile(ByteArray(0)))
    }

    @Test
    fun `pairs every I with its R twin and reports the content kind`() {
        val i1 = fakeBin.resolve("\$Imark1.txt")
        val r1 = i1.resolveSibling("\$Rmark1.txt")
        Files.write(i1, v2I("C:\\gone\\file-a.txt"))
        Files.write(r1, "abc".toByteArray())
        Files.write(fakeBin.resolve("random-file.txt"), "not-part-of-the-bin".toByteArray())

        val entries = BinIndex.entries(fakeBin)

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("C:\\gone\\file-a.txt", entry.originalPath)
        assertEquals("\$Rmark1.txt", entry.contentFile.fileName.toString())
        assertEquals(false, entry.isDirectory) // content $R was a file here
    }

    @Test
    fun `directory entries are recognized when the R twin is a directory`() {
        val i = fakeBin.resolve("\$Idir1.tmp")
        Files.write(i, v2I("C:\\gone\\some-folder"))
        Files.createDirectories(i.resolveSibling("\$Rdir1.tmp"))

        val entry = BinIndex.entries(fakeBin).single()
        assertTrue(entry.isDirectory, "content ${'$'}R is a directory")
    }

    @Test
    fun `readable sid folders are listed and entries() never throws on any of them`() {
        Files.write(fakeBin.resolve("\$Ireadable.tmp"), v2I("C:\\x"))
        Files.createDirectories(fakeDrive.resolve("\$Recycle.Bin").resolve("S-1-5-99-foreign"))

        val folders = BinIndex.readableBinFolders(fakeDrive)
        assertTrue(folders.any { it.fileName.toString().startsWith("S-1-5-21") }, "this user's bin folder is visible")
        // whatever it returns, parsing every folder must be exception-safe
        folders.forEach { BinIndex.entries(it) }
    }

    // ---- fixtures ---------------------------------------------------------

    private fun v2I(originalPath: String, size: Long = 5, deletedAt: Instant = Instant.now()): ByteArray {
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
