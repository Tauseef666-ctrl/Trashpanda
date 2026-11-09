package trashpanda.engine.execute

import trashpanda.engine.scan.Planner
import trashpanda.platform.AppDirs
import trashpanda.rules.RuleAction
import java.nio.file.Files
import kotlin.io.path.exists

/**
 * Executes an interview-approved plan (DESIGN §4.1 003 → 004). The ONLY phase of
 * trashpanda that changes the disk, and even then: Recycle Bin moves only.
 *
 * Hard invariants (engine.execute in DESIGN §5.7):
 *  - refuses anything whose planned action is not RECYCLE (report-only / never never reach P2 here)
 *  - refuses the Recycle Bin itself, System Volume Information and the Windows repair stores
 *  - every result lands in a receipt, including failures ("skipped, not killed")
 */
class Executor(
    private val port: RecyclePort,
    private val store: ReceiptStore = ReceiptStore(AppDirs.receipts),
) {

    /** Recycle Bin locations and system repair stores are never execution targets. */
    private val banned = listOf(
        "\$recycle.bin",
        "system volume information",
        "\\windows\\winsxs",
        "\\windows\\installer",
        "\\windows\\system32\\driverstore",
    )

    fun execute(approved: List<Planner.Item>): Receipt {
        approved.forEach { item ->
            require(item.action == RuleAction.RECYCLE) {
                "refused ${item.path}: planned action is ${item.action.name.lowercase()} — " +
                    "only recyclable rules may reach the executor"
            }
        }

        val items = mutableListOf<ReceiptItem>()
        for (item in approved) {
            val text = item.path.toString().lowercase().replace('/', '\\')
            if (banned.any { text.contains(it) }) {
                items += toReceipt(item, Status.FAILED, error = "protected scope — execution refused")
                continue
            }
            if (!Files.exists(item.path)) {
                items += toReceipt(item, Status.FAILED, error = "path vanished since the scan — left alone")
                continue
            }
            val out = port.recycle(item.path.toString())
            items += if (out.ok) {
                toReceipt(item, Status.RECYCLED)
            } else {
                toReceipt(item, Status.FAILED, error = "move refused by Windows (code ${out.code})")
            }
        }
        return store.write(items)
    }

    fun executeAndPrint(approved: List<Planner.Item>, println: (String) -> Unit): Receipt {
        val receipt = execute(approved)
        println("Receipt ${receipt.id}: ${receipt.recycled.size} moved, ${receipt.failed.size} left alone")
        println("  restore all:  gradlew :engine:run --args=\"--undo ${receipt.id}\"")
        println("  restore one:  open the receipt JSON, keep only its entries, pass --undo again")
        return receipt
    }

    private fun toReceipt(item: Planner.Item, status: String, error: String? = null) = ReceiptItem(
        originalPath = item.path.toString(),
        sizeBytes = item.sizeBytes,
        ruleId = item.rule.id,
        ruleTitle = item.rule.title,
        verdict = item.rule.verdict.name.lowercase(),
        risk = item.rule.risk.name.lowercase(),
        status = status,
        error = error,
    )
}

/** Shell boundary: move one path to the Recycle Bin. Result without FOF_ALLOWUNDO = hard failure upstream. */
fun interface RecyclePort {
    fun recycle(path: String): RecycleOut
}

data class RecycleOut(val code: Int, val aborted: Boolean = false) {
    val ok: Boolean get() = code == 0 && !aborted
}
