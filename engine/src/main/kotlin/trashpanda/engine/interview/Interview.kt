package trashpanda.engine.interview

import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Planner
import trashpanda.rules.CategoryId
import trashpanda.rules.RuleAction

/**
 * The guided Q&A cleanup (user requirement, 09 Nov): TrashPanda explains each category
 * in plain words — what it is, whether it's important, trash, or regenerates — and asks
 * what to delete before anything is planned for the Recycle Bin.
 *
 * Interactive only; the UI and CLI both drive it through [out]/[read] hooks so the
 * engine stays testable. Nothing here touches the disk. ASCII-safe console output.
 */
class Interview(
    private val plan: Planner.Plan,
    private val out: (String) -> Unit,
    private val read: () -> String?,
) {

    data class Selection(
        val approved: List<Planner.Item>,
        val declined: List<Planner.Item>,
    ) {
        val approvedBytes: Long get() = approved.sumOf { it.sizeBytes }
    }

    fun run(): Selection {
        out("")
        out("Bin will ask about each category. Your call in every single one -")
        out("nothing is pre-approved beyond green rules, and nothing moves yet.")
        out("")

        val approved = mutableListOf<Planner.Item>()
        val declined = mutableListOf<Planner.Item>()

        val groups = plan.items
            .groupBy { it.rule.category }
            .entries
            .sortedByDescending { it.value.sumOf { i -> i.sizeBytes } }

        for ((category, items) in groups) {
            if (items.isEmpty()) continue
            val cleanable = items.filter { it.action == RuleAction.RECYCLE }
            if (cleanable.isEmpty()) {
                describeOnly(category, items)
                continue
            }
            val total = cleanable.sumOf { it.sizeBytes }
            out("--------------------------------------------")
            out("${category.display}: ${Sizes.human(total)} in ${cleanable.size} item(s)")
            cleanable.sortedByDescending { it.sizeBytes }.take(3).forEach { item ->
                out("  - ${item.path} (${Sizes.human(item.sizeBytes)})")
            }
            if (cleanable.size > 3) out("  ... and ${cleanable.size - 3} more")
            out("")
            // The plain-language verdict lines (the "is it important? is it trash?" answer):
            cleanable.map { it.rule }.distinctBy { it.id }.forEach { rule ->
                out("  ${rule.title}: ${rule.explanation}")
                out("      verdict: ${rule.verdict.name.lowercase()} - ${rule.verdict.guidance}")
            }
            out("")
            out("Clean $category? [a]ll ${Sizes.human(total)} / [r]eview each / [n]one (enter = none): ")
            when (read()?.trim()?.lowercase()) {
                "a", "all" -> approved += cleanable
                "r", "review" -> cleanable.sortedByDescending { it.sizeBytes }.forEach { item ->
                    out("  clean ${item.path} (${Sizes.human(item.sizeBytes)})? [y/N]: ")
                    val yes = read()?.trim()?.lowercase() in listOf("y", "yes")
                    if (yes) approved += item else declined += item
                }
                else -> declined += cleanable
            }
            out("")
        }

        out("--------------------------------------------")
        if (approved.isEmpty()) {
            out("Nothing selected. Bin approves of your restraint.")
        } else {
            out("Selected for Recycle Bin: ${Sizes.human(approved.sumOf { it.sizeBytes })} across ${approved.size} item(s)")
            out("Left alone: ${Sizes.human(declined.sumOf { it.sizeBytes })} across ${declined.size} item(s)")
        }
        return Selection(approved, declined)
    }

    /** Red/report-only categories get their explanation without any clean prompt. */
    private fun describeOnly(category: CategoryId, items: List<Planner.Item>) {
        val total = items.sumOf { it.sizeBytes }
        out("--------------------------------------------")
        out("${category.display}: ${Sizes.human(total)} - look, don't touch:")
        items.map { it.rule }.distinctBy { it.id }.forEach { rule ->
            out("  ${rule.title}: ${rule.explanation}")
            out("      verdict: ${rule.verdict.name.lowercase()} - ${rule.verdict.guidance}")
        }
        out("")
    }
}
