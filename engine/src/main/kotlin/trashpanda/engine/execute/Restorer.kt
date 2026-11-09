package trashpanda.engine.execute

import trashpanda.platform.AppDirs
import trashpanda.platform.io.BinIndex
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * Undo (DESIGN §4.1 004/006): restores receipt items from the Recycle Bin to their
 * original paths. Reads the bin's $I metadata and insists the bin's recorded original
 * path matches the receipt's — a bin entry is only ever moved back to where it claims
 * (and the receipt says) it came from. Collisions get "(2)" suffixes, never overwrites.
 *
 * Order is parent-first: a folder and a file inside it can share one receipt, and the
 * folder must be home before the child is moved into it.
 */
class Restorer(
    private val store: ReceiptStore = ReceiptStore(AppDirs.receipts),
    /** Which bin folders to search on a drive. Defaults to the real bin; tests inject a fixture. */
    private val binFolders: (Path) -> List<Path> = { BinIndex.readableBinFolders(it) },
) {

    fun restore(receipt: Receipt): Receipt {
        val restoreOrder = receipt.items
            .withIndex()
            .filter { it.value.status == Status.RECYCLED }
            .sortedBy { ( _, item) -> depth(item.originalPath) } // parents before children

        val updated = mutableMapOf<Int, ReceiptItem>()
        for ((index, item) in restoreOrder) {
            updated[index] = restoreItem(item)
        }

        val newItems = receipt.items.mapIndexed { i, item -> updated[i] ?: item }
        val newReceipt = receipt.copy(items = newItems)
        store.update(newReceipt)
        return newReceipt
    }

    private fun restoreItem(item: ReceiptItem): ReceiptItem {
        val original = Path.of(item.originalPath)

        // The bin keeps its own record of where this entry came from; both must agree.
        val entry = findBinEntry(original)
        if (entry == null) {
            return item.copy(status = Status.FAILED, error = "not in the Recycle Bin (bin emptied or foreign bin)")
        }
        if (!entry.contentFile.exists()) {
            return item.copy(status = Status.FAILED, error = "bin metadata found but its contents are gone")
        }

        val target = freeTarget(original)
        return runCatching {
            Files.createDirectories(target.parent)
            // REPLACE only matches our freeTarget contract (target was just verified free).
            Files.move(entry.contentFile, target, StandardCopyOption.REPLACE_EXISTING)
        }.fold(
            onSuccess = {
                // The $I stub is now an orphan; removing it is housekeeping inside the bin.
                runCatching { Files.deleteIfExists(entry.indexFile) }
                item.copy(status = Status.RESTORED, restoredTo = target.toString(), error = null)
            },
            onFailure = {
                item.copy(status = Status.FAILED, error = "restore failed: ${it.message?.take(160) ?: "unknown"}")
            },
        )
    }

    /** First free "original (n)" slot — never overwrites anything, per DESIGN §4.1 006. */
    private fun freeTarget(original: Path): Path {
        if (!original.exists()) return original
        val parent = original.parent
        val base = original.name
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        for (n in 2..999) {
            val candidate = parent.resolve("$stem ($n)$ext")
            if (!candidate.exists()) return candidate
        }
        return parent.resolve("$stem (collisions too high)$ext")
    }

    private fun findBinEntry(original: Path): BinIndex.Entry? {
        val driveText = original.toString()
        if (driveText.length < 3 || driveText[1] != ':') return null // UNC/relative: out of scope v0.2
        val drive = Path.of(driveText.take(3))
        val wanted = canonicalText(driveText)
        return binFolders(drive)
            .asSequence()
            .flatMap { BinIndex.entries(it) }
            .firstOrNull { canonicalText(it.originalPath) == wanted }
    }

    /**
     * The recycle bin records LONG paths; callers may hand us 8.3 short forms
     * ( TAUSEE~1 …). Canonicalization resolves short names and case on the
     * EXISTING parent, then re-attaches the leaf. Falls back to the raw text
     * when parents are gone (e.g. mid-restore of nested chains).
     */
    private fun canonicalText(path: String): String {
        val trimmed = path.trim()
        val p = runCatching { Path.of(trimmed) }.getOrNull() ?: return normalize(trimmed)
        val parent = p.parent
        if (parent == null) return normalize(trimmed)
        val realParent = runCatching { parent.toRealPath() }.getOrDefault(parent)
        return normalize(realParent.resolve(p.fileName).toString())
    }

    private fun normalize(path: String): String =
        path.trim().replace('/', '\\').lowercase().trimEnd('\\') // case + separator insensitive, like Windows

    private fun depth(path: String): Int = path.count { it == '\\' || it == '/' }
}
