package trashpanda.platform.io

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Junction / symlink guard (DESIGN §5.4, TrashPanda rule 5):
 * junctions and symlinks are NEVER followed — recorded as mirrors, not descended.
 */
object ReparseGuard {

    /**
     * True when [attrs]/[path] is a reparse point (junction, symlink, one-file mount…).
     * Walkers must record it but never descend into it.
     */
    fun mustNotFollow(path: Path, attrs: BasicFileAttributes? = null): Boolean {
        if (Files.isSymbolicLink(path)) return true
        val a = attrs ?: return false
        // On Windows, junctions surface as "other" or via the reparse attribute bucket.
        return a.isSymbolicLink || a.isOther
    }

    /**
     * Belt-and-suspenders containment check: the real path of [candidate] must stay
     * inside [root]. If resolution escapes the root, we're looking at a junction or
     * symlink — descend is forbidden even if attribute sniffing missed it.
     */
    fun escapesRoot(root: Path, candidate: Path): Boolean {
        val realRoot = root.toRealPath()
        val realCandidate = try {
            candidate.toRealPath()
        } catch (_: Exception) {
            return true // unresolvable = treat as escape, fail closed
        }
        return !realCandidate.startsWith(realRoot)
    }
}
