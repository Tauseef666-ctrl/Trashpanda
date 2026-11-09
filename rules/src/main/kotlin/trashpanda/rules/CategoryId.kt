package trashpanda.rules

/** Report grouping (DESIGN §4.1 screen 003). */
enum class CategoryId(val display: String) {
    AI_AGENTS("AI agents"),
    DEV_TOOLCHAINS("Dev toolchains"),
    BUILD_OUTPUTS("Build outputs"),
    WINDOWS("Windows temp"),
    BROWSERS("Browsers"),
    RECYCLE_BIN("Recycle Bin scope"),
    PERSONAL("Large personal files (review only)");

    companion object {
        fun parse(raw: String): CategoryId =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown category '$raw'")
    }
}
