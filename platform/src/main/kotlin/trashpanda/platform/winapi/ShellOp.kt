package trashpanda.platform.winapi

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.W32APITypeMapper

// shellapi.h SHFILEOPSTRUCTW flag constants (documented shell values)
private const val FOF_ALLOWUNDO = 0x0040
private const val FOF_NOCONFIRMATION = 0x0010
private const val FOF_SILENT = 0x0004
private const val FOF_NOERRORUI = 0x0400
private const val FO_DELETE = 0x0003

/**
 * Win32 SHFILEOPSTRUCTW — byte-compatible on x64.
 *
 * MUST be a public top-level class: JNA derives layouts by reflection over public
 * fields, and any reduced visibility explodes as IllegalAccessException at
 * deriveLayout time (caught the hard way in TrashRoundTripIT).
 * The UNICODE type mapper makes String fields wchar_t* and adds the terminator,
 * so `pFrom`'s own trailing NUL completes the required double-NUL sequence.
 */
@Structure.FieldOrder(
    "hWnd", "wFunc", "pFrom", "pTo",
    "fFlags", "fAnyOperationsAborted", "hNameMappings", "lpszProgressTitle",
)
class OpStruct : Structure(W32APITypeMapper.UNICODE) {
    @JvmField var hWnd: Pointer? = null
    @JvmField var wFunc: Int = FO_DELETE
    @JvmField var pFrom: String = ""
    @JvmField var pTo: String? = null
    @JvmField var fFlags: Int = FOF_ALLOWUNDO or FOF_NOCONFIRMATION or FOF_SILENT or FOF_NOERRORUI
    @JvmField var fAnyOperationsAborted: Boolean = false
    @JvmField var hNameMappings: Pointer? = null
    @JvmField var lpszProgressTitle: String? = null
}

/**
 * Windows Recycle Bin bridge (DESIGN §5.9): SHFileOperationW with FOF_ALLOWUNDO is the
 * ONLY move-to-trash primitive this app will ever call. A result without the undo flag
 * is treated as a hard failure, never a fallback — the safety invariant is structural.
 */
object ShellOp {

    private interface Shell32Lib : Library {
        fun SHFileOperationW(op: OpStruct): Int
    }

    private val shell32: Shell32Lib = Native.load("shell32", Shell32Lib::class.java)

    data class ShellResult(val code: Int, val aborted: Boolean) {
        val ok: Boolean get() = code == 0 && !aborted
    }

    /** Moves one path to the Recycle Bin (undo-able). Never minimizes, never hard-deletes. */
    fun recycleToBin(path: String): ShellResult {
        // pFrom is a doubly null-terminated list: path, NUL, NUL (terminator + the appended one).
        val op = OpStruct().apply { pFrom = path + "\u0000" }
        val code = shell32.SHFileOperationW(op)
        return ShellResult(code, op.fAnyOperationsAborted)
    }
}
