package trashpanda.rules

/**
 * Risk triplet — identical meaning in every theme (DESIGN §3.1):
 * GREEN = auto-check ok · AMBER = user must tick · RED = show only.
 */
enum class Risk(val label: String) {
    GREEN("safe"),
    AMBER("you tick"),
    RED("look, don't touch");

    companion object {
        /** Strict parse — an unknown risk in TOML must fail loudly, never default. */
        fun parse(raw: String): Risk =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown risk '$raw' (expected green|amber|red)")
    }
}
