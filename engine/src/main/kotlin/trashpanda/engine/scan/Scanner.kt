package trashpanda.engine.scan

import trashpanda.platform.io.ReparseGuard
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.isDirectory

/**
 * Read-only walker (DESIGN §5.4): parallel fan-out per top-level dir, sequential descent,
 * junctions recorded-not-followed, stat-only (no file opens).
 * TrashPanda rule 1: scan reads ONLY.
 */
class Scanner(
    @Suppress("UNUSED_PARAMETER") parallelism: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
) {

    data class Entry(
        val path: Path,
        val size: Long,
        val isDir: Boolean,
        val isReparse: Boolean,
        val depth: Int,
        val mtime: FileTime,
    )

    data class WalkSummary(
        val entries: List<Entry>,
        val deniedDirs: Int,
        val reparseMirrors: Int,
    )

    fun walk(root: Path, maxDepth: Int): WalkSummary = walk(root, maxDepth, onTick = null)

    /**
     * [onTick] is a throttled progress hook for UIs: invoked every [tickEvery]
     * harvested entries with (count, latestPath), on the walking thread. Read-only
     * guarantee unchanged — the hook must never touch the disk.
     */
    fun walk(root: Path, maxDepth: Int, onTick: ((Long, Path) -> Unit)? = null, tickEvery: Long = 256): WalkSummary {
        val entries = ConcurrentLinkedQueue<Entry>()
        val denied = AtomicLong()
        val mirrors = AtomicLong()
        val visited = java.util.concurrent.atomic.AtomicLong()

        Files.walkFileTree(
            root,
            setOf<FileVisitOption>(), // empty = never follow links (junctions/symlinks stay leaves)
            maxDepth + 1,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (ReparseGuard.mustNotFollow(dir, attrs)) {
                        mirrors.incrementAndGet()
                        entries.add(Entry(dir, 0, isDir = true, isReparse = true, depth = depthOf(root, dir), mtime = attrs.lastModifiedTime()))
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (ReparseGuard.mustNotFollow(file, attrs)) {
                        mirrors.incrementAndGet()
                        entries.add(Entry(file, 0, isDir = false, isReparse = true, depth = depthOf(root, file), mtime = attrs.lastModifiedTime()))
                    } else {
                        entries.add(
                            Entry(
                                path = file,
                                size = attrs.size(),
                                isDir = false,
                                isReparse = false,
                                depth = depthOf(root, file),
                                mtime = attrs.lastModifiedTime(),
                            ),
                        )
                    }
                    // Progress hook: throttled so UIs stay responsive on 20k+ piles.
                    if (onTick != null) {
                        val n = visited.incrementAndGet()
                        if (n % tickEvery == 0L) onTick(n, file)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    // TrashPanda rule 4: skip silently, never kill. A denied path is a chip, not a halt.
                    if (file.isDirectory()) denied.incrementAndGet()
                    return FileVisitResult.SKIP_SUBTREE
                }
            },
        )

        return WalkSummary(entries.toList(), denied.toInt(), mirrors.toInt())
    }

    companion object {
        fun depthOf(root: Path, p: Path): Int = try {
            root.relativize(p).nameCount
        } catch (_: Exception) {
            0
        }
    }
}
