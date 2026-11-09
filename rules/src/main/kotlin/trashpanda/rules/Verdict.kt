package trashpanda.rules

/**
 * Importance verdict — the plain-language answer to "is this important? is it trash?"
 * (user requirement, 09 Nov). Shown next to every item, in human words, not just color:
 *
 *  TRASH       — this is garbage. Safe to clean. (green, auto-check ok)
 *  REGENERATES — tool re-downloads/rebuilds it on demand. Cleaning costs time, not data.
 *  REVIEW      — your data or real tools. TrashPanda asks; you decide. Never auto-checked.
 *  KEEP        — system-critical or dangerous to touch. Look, don't clean.
 */
enum class Verdict(val label: String, val guidance: String) {
    TRASH("trash", "This is garbage. Safe to clean."),
    REGENERATES("regenerates", "Tools re-download or rebuild this automatically. Costs time, not data."),
    REVIEW("review", "This is your data or a real tool. TrashPanda asks — you decide."),
    KEEP("keep", "System-critical. TrashPanda never cleans this. Look, don't touch.");

    companion object {
        fun parse(raw: String): Verdict =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown verdict '$raw' (expected trash|regenerates|review|keep)")
    }
}
