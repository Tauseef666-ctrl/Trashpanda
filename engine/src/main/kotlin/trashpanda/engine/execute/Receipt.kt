package trashpanda.engine.execute

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import trashpanda.platform.AppDirs
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.io.path.listDirectoryEntries

/**
 * Receipts (DESIGN §4.1 005 + PLAN rule 7): every executed clean writes a human-readable
 * JSON record — per item: original path, size, rule, verdict, and later its restore
 * state. Receipts live under %APPDATA%\TrashPanda\receipts and are TrashPanda's only
 * claim on what it moved; undo reads them back verbatim.
 *
 * Instance-based on purpose (no global dir state that tests would have to reset).
 */
class ReceiptStore(private val baseDir: Path = AppDirs.receipts) {

    val dir: Path get() = baseDir

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault())

    fun write(items: List<ReceiptItem>): Receipt {
        val now = Instant.now()
        val id = "${stamp.format(now)}-${UUID.randomUUID().toString().take(4)}"
        val receipt = Receipt(
            id = id,
            createdAtIso = now.toString(),
            freedBytes = items.filter { it.status == Status.RECYCLED }.sumOf { it.sizeBytes },
            failedBytes = items.filter { it.status != Status.RECYCLED }.sumOf { it.sizeBytes },
            items = items.sortedByDescending { it.sizeBytes },
        )
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("$id.json"), json.encodeToString(Receipt.serializer(), receipt))
        return receipt
    }

    fun list(): List<Receipt> =
        runCatching { dir.listDirectoryEntries("*.json") }
            .getOrDefault(emptyList())
            .mapNotNull { p -> runCatching { read(p) }.getOrNull() }
            .sortedByDescending { it.createdAtIso }

    fun read(path: Path): Receipt? =
        runCatching { json.decodeFromString(Receipt.serializer(), Files.readString(path)) }.getOrNull()

    fun latest(): Receipt? = list().firstOrNull()

    fun update(receipt: Receipt) {
        Files.createDirectories(dir)
        val file = dir.resolve("${receipt.id}.json")
        if (Files.exists(file)) {
            Files.writeString(file, json.encodeToString(Receipt.serializer(), receipt))
        }
    }
}

/**
 * Status of ONE receipt item. A receipt's overall story is just the union of these:
 * recycled -> moved to the Recycle Bin, receipt written
 * restored -> back at its original location (or "(2)" neighbor if taken)
 * failed   -> Windows refused the move (locked/protected); the file was LEFT ALONE
 */
object Status {
    const val RECYCLED = "recycled"
    const val RESTORED = "restored"
    const val FAILED = "failed"
}

@Serializable
data class Receipt(
    val id: String,
    val createdAtIso: String,
    val freedBytes: Long,
    val failedBytes: Long,
    val items: List<ReceiptItem>,
) {
    val recycled: List<ReceiptItem> get() = items.filter { it.status == Status.RECYCLED }
    val failed: List<ReceiptItem> get() = items.filter { it.status == Status.FAILED }
    val restored: List<ReceiptItem> get() = items.filter { it.status == Status.RESTORED }

    /** Receipt as a whole, for humans scanning the history folder. */
    val state: String
        get() = when {
            items.isEmpty() -> "empty"
            recycled.isEmpty() && restored.isEmpty() -> "all failed - nothing left the disk"
            recycled.isEmpty() && failed.isEmpty() -> "undone"
            recycled.isEmpty() -> "partially undone"
            restored.isEmpty() && failed.isEmpty() -> Status.RECYCLED
            else -> "partially undone"
        }
}

@Serializable
data class ReceiptItem(
    val originalPath: String,
    val sizeBytes: Long,
    val ruleId: String,
    val ruleTitle: String,
    val verdict: String,
    val risk: String,
    val status: String,
    val restoredTo: String? = null,
    val error: String? = null,
)
