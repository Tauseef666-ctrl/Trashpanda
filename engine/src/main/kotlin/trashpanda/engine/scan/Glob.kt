package trashpanda.engine.scan

/**
 * Segment-based glob matcher for rule patterns.
 * `**` spans zero or more whole segments (`**` alone = everything) ·
 * `*` spans within one segment · `?` matches one char. Case-insensitive (Windows).
 * Backslashes in incoming paths are normalized to forward slashes before matching.
 */
object Glob {

    private val CACHE = HashMap<String, Regex>()

    fun matches(pattern: String, path: String): Boolean {
        val p = normalize(pattern)
        val t = normalize(path)
        if (t.isEmpty()) return false
        if (p.endsWith("/**") && t == p.removeSuffix("/**")) return false // the root itself is never the junk
        return toRegex(p).matches(t)
    }

    private fun normalize(s: String) = s.trim().replace('\\', '/').trim('/').trimEnd('/')

    private fun toRegex(pattern: String): Regex = CACHE.getOrPut(pattern) {
        val segments = pattern.split('/')
        val sb = StringBuilder("^")
        segments.forEachIndexed { i, seg ->
            when {
                seg == "**" && i == segments.size - 1 -> sb.append(".*")
                seg == "**" -> sb.append("(?:[^/]*/)*")
                else -> {
                    appendSegment(sb, seg)
                    if (i != segments.size - 1) sb.append('/')
                }
            }
        }
        sb.append('$')
        Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    private fun appendSegment(sb: StringBuilder, seg: String) {
        seg.forEach { c ->
            when (c) {
                '*' -> sb.append("[^/]*")
                '?' -> sb.append("[^/]")
                '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
        }
    }
}
