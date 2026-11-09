package trashpanda.engine.report

import trashpanda.engine.scan.Planner
import trashpanda.rules.CategoryId
import trashpanda.rules.Risk
import trashpanda.rules.RuleAction

/** Plain-text report renderer — the P1 console face of the report screen (DESIGN §4.1 003). ASCII-safe. */
object ConsoleReport {

    private fun chip(risk: Risk): String = when (risk) {
        Risk.GREEN -> "[GREEN safe]"
        Risk.AMBER -> "[AMBER you-tick]"
        Risk.RED -> "[RED look-only]"
    }

    private fun actionTag(action: RuleAction): String = when (action) {
        RuleAction.RECYCLE -> "recyclable"
        RuleAction.REPORT_ONLY -> "report-only"
        RuleAction.NEVER -> "never"
    }

    fun render(plan: Planner.Plan, elapsedMs: Long): String {
        val sb = StringBuilder()
        val groups: Map<CategoryId, List<Planner.Item>> =
            plan.items.groupBy { it.rule.category }.toSortedMap(compareBy { it.ordinal })

        if (plan.items.isEmpty()) {
            sb.appendLine("Bin found nothing. Even trash pandas have standards.")
            return sb.toString()
        }

        groups.forEach { (cat, items) ->
            val total = items.sumOf { it.sizeBytes }
            sb.appendLine()
            sb.appendLine("== ${cat.display} - ${Sizes.human(total)} across ${items.size} item(s) ==")
            items.sortedByDescending { it.sizeBytes }.take(40).forEach { item ->
                sb.appendLine(
                    "  ${chip(item.rule.risk)} ${Sizes.human(item.sizeBytes).padEnd(9)} ${item.path}",
                )
                sb.appendLine("     -> [${item.rule.verdict.name.lowercase()}] ${item.why} (${actionTag(item.action)})")
            }
            if (items.size > 40) sb.appendLine("  ... and ${items.size - 40} more")
        }

        sb.appendLine()
        sb.appendLine("--------------------------------------------")
        sb.appendLine("Reclaimable (green+amber, recyclable): ${Sizes.human(plan.recyclableBytes)} in ${plan.recycleCount} item(s)")
        sb.appendLine("Report-only (red, shown not touchable): ${Sizes.human(plan.reportOnlyBytes)}")
        sb.appendLine("Junctions/symlinks mirrored (never followed): ${plan.reparseMirrors}")
        if (plan.deniedDirs > 0) sb.appendLine("In use / denied - skipped, not killed: ${plan.deniedDirs}")
        sb.appendLine("Scan took ${elapsedMs} ms - read-only - nothing was deleted")
        return sb.toString()
    }

    private val Planner.Plan.reportOnlyBytes: Long
        get() = items.filter { it.action == RuleAction.REPORT_ONLY }.sumOf { it.sizeBytes }
}
