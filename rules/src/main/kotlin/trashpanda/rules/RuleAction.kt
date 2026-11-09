package trashpanda.rules

/**
 * What may happen to a matched item.
 * recycle = Recycle Bin only · reportOnly = informational · never = hard-banned from file ops.
 */
enum class RuleAction {
    RECYCLE,
    REPORT_ONLY,
    NEVER;

    companion object {
        fun parse(raw: String): RuleAction =
            entries.firstOrNull { it.name.equals(raw.replace(' ', '_'), ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown action '$raw' (expected recycle|report_only|never)")
    }
}
