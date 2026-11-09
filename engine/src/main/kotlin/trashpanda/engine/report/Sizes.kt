package trashpanda.engine.report

/** Humanize sizes like the design says: "8.1 GB", never "8676171776 B" (DESIGN §3.4). */
object Sizes {
    private const val KB = 1_000.0
    private const val MB = KB * 1000
    private const val GB = MB * 1000
    private const val TB = GB * 1000

    fun human(bytes: Long): String = when {
        bytes >= TB -> "%.1f TB".format(bytes / TB)
        bytes >= GB -> "%.1f GB".format(bytes / GB)
        bytes >= MB -> "%.1f MB".format(bytes / MB)
        bytes >= KB -> "%.1f KB".format(bytes / KB)
        bytes == 1L -> "1 B"
        else -> "$bytes B"
    }
}
