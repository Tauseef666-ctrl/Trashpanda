package trashpanda.platform

import java.nio.file.Files
import java.nio.file.Path

/**
 * TrashPanda's own data home (DESIGN §5.8):
 * %APPDATA%\TrashPanda\{receipts,logs} — nothing outside it is ever written.
 */
object AppDirs {

    val root: Path by lazy {
        val appData = System.getenv("APPDATA")
            ?: (System.getProperty("user.home") + "\\AppData\\Roaming")
        Path.of(appData, "TrashPanda")
    }

    val receipts: Path get() = root.resolve("receipts")
    val logs: Path get() = root.resolve("logs")

    /** Creates the layout on demand; callers must treat failures as fatal-to-feature, never as a prompt. */
    fun ensure(): Path {
        Files.createDirectories(receipts)
        Files.createDirectories(logs)
        return root
    }
}
