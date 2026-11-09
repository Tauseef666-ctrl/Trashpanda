package trashpanda.platform.io

/**
 * Long-path handling (DESIGN §5.4).
 *
 * Note on the JDK: `java.nio.file.Paths.get("\\?\C:\…")` strips the device prefix on
 * parse — NIO already handles long paths internally. The `\\?\` prefix is therefore
 * produced in the STRING domain only, for raw Win32 calls (JNA recycle / lock probes in P1+).
 */
object PathText {

    /** Literal `\\?\` */
    const val DEVICE_PREFIX = "\\\\?\\"
    private const val THRESHOLD = 240

    /** Pure string function: adds `\\?\` (or `\\?\UNC\`) exactly once. No filesystem IO. */
    fun prefix(rawPath: String): String {
        val s = rawPath.trim()
        if (s.startsWith(DEVICE_PREFIX, ignoreCase = true)) return s
        // UNC form: \\server\share → \\?\UNC\server\share
        if (s.startsWith("\\\\")) return "$DEVICE_PREFIX" + "UNC" + s.drop(1)
        return "$DEVICE_PREFIX$s"
    }

    /** Prefix only when the path would exceed the classic MAX_PATH budget. */
    fun maybePrefix(rawPath: String): String =
        if (rawPath.length > THRESHOLD) prefix(rawPath) else rawPath
}
