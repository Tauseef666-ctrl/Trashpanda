package trashpanda.engine.scan

import java.io.File
import java.nio.file.Path

/**
 * Multi-drive awareness (user requirement, 09 Nov): scan the whole system,
 * every fixed drive, not just C:\.
 */
object Drives {

    data class Drive(val root: Path, val totalBytes: Long, val freeBytes: Long) {
        val label: String get() = root.toString()
    }

    /** Every fixed, usable drive root (C:\, D:\ …). Removable/network drives are skipped. */
    fun fixedDrives(): List<Drive> = File.listRoots()
        .map { File(it.path) }
        .filter { it.exists() }
        .mapNotNull { f ->
            runCatching {
                val total = f.totalSpace
                if (total > 0) Drive(f.toPath().toAbsolutePath(), total, f.usableSpace) else null
            }.getOrNull()
        }
}
