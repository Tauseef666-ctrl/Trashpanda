package trashpanda.platform.io

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * Reads the Recycle Bin's on-disk format so receipts can be undone (DESIGN §4.1 004/006).
 *
 * Each binned item is a pair in `<drive>\$Recycle.Bin\<SID>\`:
 *   $I<id>.<ext> — metadata: header, size, deletion time, ORIGINAL path
 *   $R<id>.<ext> — the content itself
 * Undo = move the $R entry back to the $I-declared original path. This never empties
 * or overwrites anything: restore targets must be free, else "(2)" suffixes per DESIGN.
 */
object BinIndex {

    private const val BIN_DIR = "\$Recycle.Bin"
    private const val I_PREFIX = "\$I"
    private const val R_PREFIX = "\$R"

    data class Entry(
        val indexFile: Path,
        val contentFile: Path,
        val originalPath: String,
        val sizeBytes: Long,
        val deletedAt: Instant?,
        val isDirectory: Boolean,
    )

    /** Per-user (SID) bin folders on a drive that this process can actually read. */
    fun readableBinFolders(driveRoot: Path): List<Path> {
        val binRoot = driveRoot.resolve(BIN_DIR)
        if (!binRoot.isDirectory()) return emptyList()
        return runCatching {
            Files.list(binRoot).use { stream ->
                stream.filter { Files.isDirectory(it) && it.fileName.toString().startsWith("S-") }
                    .map { it as Path }
                    .toList()
            }
        }.getOrDefault(emptyList()).filter { folder ->
            runCatching { Files.list(folder).use { it.findAny().isPresent } }.getOrDefault(false)
        }
    }

    /** All restorable entries inside one bin folder. Unreadable/foreign-format $I files are skipped. */
    fun entries(binFolder: Path): List<Entry> =
        runCatching {
            Files.list(binFolder).use { stream ->
                stream.map { it as Path }
                    .filter { it.isRegularFile() && it.fileName.toString().startsWith(I_PREFIX) }
                    .toList()
            }
        }.getOrDefault(emptyList())
            .mapNotNull { iFile ->
                val content = iFile.resolveSibling(iFile.fileName.toString().replaceFirst(I_PREFIX, R_PREFIX, true))
                val parsed = runCatching { Files.readAllBytes(iFile) }
                    .map { parseIFile(it) }
                    .getOrNull()
                    ?: return@mapNotNull null
                Entry(
                    indexFile = iFile,
                    contentFile = content,
                    originalPath = parsed.originalPath,
                    sizeBytes = parsed.sizeBytes,
                    deletedAt = parsed.deletedAt,
                    isDirectory = content.isDirectory(),
                )
            }

    data class IParsed(val originalPath: String, val sizeBytes: Long, val deletedAt: Instant?)

    /**
     * $I format: v2 (Vista+) = [0..3 version=2][4..7 zero][8..15 size][16..23 FILETIME][24..27 path chars][28.. UTF-16LE path]
     * v1 (XP) = [0..3 version=1][4..7 zero][8..11 size u32][12..19 FILETIME][20..23 pad][24.. ANSI path, NUL-terminated]
     */
    fun parseIFile(bytes: ByteArray): IParsed? {
        if (bytes.size < 24) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return when (val version = buf.int) {
            2 -> {
                val originalNameBytes = bytes.size - 28
                if (originalNameBytes < 2) return null
                val size = buf.getLong(8)
                val deletedAt = fileTimeToInstant(buf.getLong(16))
                val path = String(bytes, 28, originalNameBytes, Charsets.UTF_16LE).trimEnd('\u0000')
                IParsed(path, size, deletedAt).takeIf { it.originalPath.isNotBlank() }
            }

            1 -> {
                val size = buf.getInt(8).toLong() and 0xFFFFFFFFL
                val deletedAt = fileTimeToInstant(buf.getLong(12))
                val path = String(bytes, 24, bytes.size - 24, Charsets.ISO_8859_1)
                    .substringBefore('\u0000')
                IParsed(path, size, deletedAt).takeIf { it.originalPath.isNotBlank() }
            }

            else -> null // unknown version: unsupported, skip
        }
    }

    /** Windows FILETIME (100-ns since 1601) to [Instant]. */
    fun fileTimeToInstant(fileTime: Long): Instant? {
        if (fileTime <= 0) return null
        return Instant.ofEpochMilli(fileTime / 10_000 - 11_644_473_600_000)
    }
}
