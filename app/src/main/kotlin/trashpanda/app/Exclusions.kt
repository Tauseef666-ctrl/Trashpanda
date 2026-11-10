package trashpanda.app

import trashpanda.engine.scan.Planner
import java.nio.file.Path

/**
 * User exclusions ("never touch"): paths the user forbids, no matter what a rule
 * thinks. Matching is Windows-flavored: case-insensitive, both separators, and a
 * prefix match only on whole components — "D:\Games" excludes "D:\Games\save.dat"
 * but never "D:\Games2\save.dat". Sanity guard: an exclusion may never swallow a
 * ban-list surface (the rules already refuse those; this keeps a typo like "C:"
 * from turning the whole app into a no-op silently — it's still allowed, just
 * honestly has no effect on protected roots because rules carry their own ban list).
 *
 * Pure functions: applied to every merged plan BEFORE the report is rendered, so an
 * excluded path can never be listed or moved.
 */
internal fun exclusionKey(raw: String): String =
    raw.trim().replace('/', '\\').trimEnd('\\').lowercase()

internal fun isExcludedPath(path: Path, exclusions: Collection<String>): Boolean {
    if (exclusions.isEmpty()) return false
    val key = exclusionKey(path.toString())
    if (key.isEmpty()) return false
    for (raw in exclusions) {
        val ex = exclusionKey(raw)
        if (ex.isEmpty()) continue
        if (key == ex || key.startsWith("$ex\\")) return true
    }
    return false
}

/** Honest filter: drops excluded items, keeps every real counter coherent. */
internal fun applyExclusions(plan: Planner.Plan, exclusions: List<String>): Planner.Plan =
    if (exclusions.isEmpty()) plan
    else plan.copy(items = plan.items.filter { !isExcludedPath(it.path, exclusions) })
