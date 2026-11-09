package trashpanda.engine.scan

import trashpanda.platform.io.EnvExpand
import trashpanda.rules.LoadedCatalog
import trashpanda.rules.Rule
import trashpanda.rules.RuleAction
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Turns a loaded catalog + a walked tree into a concrete plan.
 * Guard rails (DESIGN §5.3): plans touch nothing; every item carries its rule's
 * explanation; NEVER-action rules never produce items; protected tokens drop matches.
 */
class Planner(private val catalog: LoadedCatalog, private val env: Map<String, String> = System.getenv()) {

    data class Item(
        val rule: Rule,
        val path: Path,
        val sizeBytes: Long,
        val why: String,
        val action: RuleAction,
        val isReparse: Boolean = false,
    )

    data class Plan(
        val items: List<Item>,
        val deniedDirs: Int,
        val reparseMirrors: Int,
        val scannedRoots: List<Path>,
    ) {
        val recyclableBytes: Long get() = items.filter { it.action == RuleAction.RECYCLE }.sumOf { it.sizeBytes }
        val recycleCount: Int get() = items.count { it.action == RuleAction.RECYCLE }
    }

    fun planFromDisk(roots: List<Path>): Plan {
        val scanner = Scanner()
        val all = mutableListOf<Scanner.WalkSummary>()
        val walked = mutableListOf<Path>()
        for (root in roots) {
            if (!root.toFile().exists()) continue
            all.add(scanner.walk(root, maxDepth = 12))
            walked.add(root)
        }
        return planFromWalked(all, walked)
    }

    fun planFromWalked(summaries: List<Scanner.WalkSummary>, walkedRoots: List<Path>): Plan {
        val items = mutableListOf<Item>()
        summaries.flatMap { it.entries }.distinctBy { it.path }.forEach { entry ->
            for (rule in catalog.rules) {
                val item = match(rule, entry)
                if (item != null) {
                    items += item
                    break // first rule wins; keeps sizes single-counted
                }
            }
        }
        return Plan(
            items = items.sortedByDescending { it.sizeBytes },
            deniedDirs = summaries.sumOf { it.deniedDirs },
            reparseMirrors = summaries.sumOf { it.reparseMirrors },
            scannedRoots = walkedRoots,
        )
    }

    private fun match(rule: Rule, entry: Scanner.Entry): Item? {
        if (rule.action == RuleAction.NEVER) return null // hard-banned: produces no item ever

        val pathText = entry.path.toString()
        val hit = rule.patterns.any { Glob.matches(EnvExpand.expand(it, env), pathText) }
        if (!hit) return null

        // Size gate (MB = 10^6 bytes; humanization elsewhere uses the same base).
        if (rule.constraints.minSizeMB > 0 && entry.size < rule.constraints.minSizeMB * 1_000_000L) return null

        // Age gate applies to files and dirs alike (D1's "older than 24 h" promise).
        if (rule.constraints.minAgeDays > 0) {
            val age = Duration.between(entry.mtime.toInstant(), Instant.now())
            if (age < Duration.ofDays(rule.constraints.minAgeDays)) return null
        }

        // Global + per-rule protected tokens drop the match (config-kill protection, DESIGN §8.3).
        val protected = rule.dropWhenPaths.any { token ->
            val t = token.lowercase()
            val fileName = entry.path.fileName?.toString()?.lowercase() ?: ""
            pathText.contains("\\$token\\", ignoreCase = true) ||
                pathText.contains("/$token/", ignoreCase = true) ||
                fileName == t
        }
        if (protected) return null

        return Item(
            rule = rule,
            path = entry.path,
            sizeBytes = entry.size,
            why = rule.explanation,
            action = rule.action,
            isReparse = entry.isReparse,
        )
    }
}
