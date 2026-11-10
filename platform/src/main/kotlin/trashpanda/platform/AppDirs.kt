package trashpanda.platform

import java.nio.file.Files
import java.nio.file.Path

/**
 * Trashpanda's own data home (DESIGN §5.8):
 * %APPDATA%\Trashpanda\{receipts,logs} — nothing outside it is ever written.
 * Windows paths are case-insensitive, so this is the same folder the app always used.
 */
object AppDirs {

    val root: Path by lazy {
        val appData = System.getenv("APPDATA")
            ?: (System.getProperty("user.home") + "\\AppData\\Roaming")
        Path.of(appData, "Trashpanda")
    }

    val receipts: Path get() = root.resolve("receipts")
    val logs: Path get() = root.resolve("logs")
    /** settings.json (DESIGN §3 "user-visible structure"): theme, exclusions, flags. */
    val settings: Path get() = root.resolve("settings.json")

    /** Creates the layout on demand; callers must treat failures as fatal-to-feature, never as a prompt. */
    fun ensure(): Path {
        Files.createDirectories(receipts)
        Files.createDirectories(logs)
        return root
    }
}
