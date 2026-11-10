package trashpanda.platform.winapi

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Kernel32Util
import java.io.File

/** One ready volume with its true Windows storage type (display + deep-scan roots). */
data class DriveVolume(
    val root: String,       // "C:\" — Windows spelling (comes from GetLogicalDriveStrings)
    val totalBytes: Long,
    val freeBytes: Long,
    val kind: Kind,
) {
    enum class Kind(val label: String) {
        FIXED("Fixed"),
        REMOVABLE("Removable"),
        NETWORK("Network"),
        CD_ROM("Optical"),
        OTHER("Other"),
    }

    /** The ALL_DRIVES deep scan targets fixed disks only — never network mounts or USB. */
    val isScanTarget: Boolean get() = kind == Kind.FIXED
}

/**
 * True drive enumeration + STORAGE TYPE detection via the Windows API (user request:
 * "it doesn't detect the other drives' storage — with a proper check of the storage
 * type"). Types come straight from GetDriveTypeW, not guesses; empty card readers and
 * phantom units (totalSpace == 0) are dropped. Any failure yields [], never a throw.
 */
object Drives {

    fun volumes(): List<DriveVolume> {
        val roots: List<String> =
            runCatching { Kernel32Util.getLogicalDriveStrings() }.getOrDefault(emptyList())
        if (roots.isEmpty()) return emptyList()
        return roots.mapNotNull { root ->
            val file = File(root)
            val probe: DriveVolume? = runCatching {
                // GetDriveTypeW: 0 unknown, 1 no-root, 2 removable, 3 fixed, 4 remote, 5 cdrom, 6 ramdisk
                val kind = when (Kernel32.INSTANCE.GetDriveType(root)) {
                    3 -> DriveVolume.Kind.FIXED
                    2 -> DriveVolume.Kind.REMOVABLE
                    4 -> DriveVolume.Kind.NETWORK
                    5 -> DriveVolume.Kind.CD_ROM
                    else -> DriveVolume.Kind.OTHER
                }
                if (kind == DriveVolume.Kind.OTHER || file.totalSpace <= 0L) null
                else DriveVolume(root, file.totalSpace, file.freeSpace, kind)
            }.getOrNull()
            probe
        }
    }

    /** Fixed volumes only — the ALL_DRIVES scan scope. */
    fun fixedVolumes(): List<DriveVolume> = volumes().filter { it.isScanTarget }
}
