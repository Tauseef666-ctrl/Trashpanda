package trashpanda.rules

/** Scan guard rails attached to a rule (DESIGN §5.2). Junctions are never followed, globally. */
data class ScanConstraints(
    val minAgeDays: Long = 0,
    val minSizeMB: Long = 0,
    val maxDepth: Int = 8,
) {
    init {
        require(minAgeDays >= 0) { "minAgeDays must be >= 0" }
        require(minSizeMB >= 0) { "minSizeMB must be >= 0" }
        require(maxDepth >= 0) { "maxDepth must be >= 0" }
    }
}
