package trashpanda.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import trashpanda.engine.execute.Receipt
import trashpanda.engine.execute.ReceiptStore
import trashpanda.engine.execute.Restorer
import trashpanda.engine.execute.Status
import trashpanda.engine.report.Sizes
import trashpanda.platform.AppDirs
import trashpanda.platform.io.BinIndex
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * History — every cleanup writes a receipt (`%APPDATA%\Trashpanda\receipts`); this screen
 * lists them newest-first and offers one-click Undo per receipt through the SAME Restorer
 * the CLI's `--undo` uses: bin metadata must match the receipt, collisions get "(2)"
 * suffixes, nothing is ever overwritten. Receipts whose items are already back home show
 * a quiet "undone" chip instead of a button.
 *
 * Test seams mirror ReportScreen: receiptsDir / binFolders / worker are injectable.
 */

/** True when at least one item of this receipt still sits in the bin (an Undo makes sense). */
private fun Receipt.undoAvailable(): Boolean = recycled.isNotEmpty()

private val stampFormat = DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    worker: CoroutineDispatcher = Dispatchers.IO,
    receiptsDir: Path = AppDirs.receipts,
    binFolders: (Path) -> List<Path> = { BinIndex.readableBinFolders(it) },
) {
    val store = remember(receiptsDir) { ReceiptStore(receiptsDir) }
    var receipts by remember { mutableStateOf<List<Receipt>?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val jobs = rememberCoroutineScope()

    fun reload() {
        jobs.launch {
            receipts = withContext(worker) { store.list() }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("History", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                Text(
                    "Every confirmed cleanup, newest first. Undo moves items back to where they came from — " +
                        "byte for byte, never overwriting.",
                    fontSize = 13.sp,
                    color = Tokens.textDim,
                )
            }
            TextButton(onClick = onBack) { Text("Home", color = Tokens.textDim, fontSize = 13.sp) }
        }

        message?.let {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tokens.cardInset)
                    .border(1.dp, Tokens.border, RoundedCornerShape(10.dp))
                    .clickable { message = null }
                    .padding(12.dp),
            ) {
                Text(it, fontSize = 13.sp, color = Tokens.textDim)
            }
        }

        when {
            receipts == null -> Text("reading receipts…", fontSize = 13.sp, color = Tokens.textDim)
            receipts!!.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No cleanups yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                Text(
                    "After a scan, confirm a clean here in the app and its receipt will appear in this list — ready to undo.",
                    fontSize = 13.sp,
                    color = Tokens.textDim,
                )
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(receipts!!, key = { it.id }) { receipt ->
                    ReceiptCard(
                        receipt = receipt,
                        busy = busyId == receipt.id,
                        onUndo = {
                            if (busyId == null) {
                                busyId = receipt.id
                                jobs.launch {
                                    try {
                                        val updated = withContext(worker) {
                                            Restorer(store, binFolders).restore(receipt)
                                        }
                                        val stillInBin = updated.recycled.size
                                        message = if (stillInBin == 0) {
                                            "Undo complete for ${updated.id} — everything is back home."
                                        } else {
                                            "Undo ran on ${updated.id}: some items could not be restored " +
                                                "(they are gone from the bin) — see their status on the receipt."
                                        }
                                        reload()
                                    } catch (t: Throwable) {
                                        message = "Undo stopped: ${t.message ?: t::class.simpleName}. Nothing was lost — " +
                                            "the bin entries are still there."
                                    } finally {
                                        busyId = null
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptCard(receipt: Receipt, busy: Boolean, onUndo: () -> Unit) {
    val restored = receipt.recycled.isEmpty() && receipt.failed.isEmpty()
    val chipColor = when {
        receipt.recycled.isEmpty() -> Tokens.riskSafe
        receipt.failed.isNotEmpty() -> Tokens.riskReview
        else -> Tokens.primary
    }
    val chipText = when {
        restored -> "undone"
        receipt.recycled.isEmpty() -> "nothing left the disk"
        receipt.failed.isNotEmpty() -> "in bin · some left alone"
        else -> "in bin"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.surface)
            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Receipt ${receipt.id}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.primary)
                Text(
                    "${stampFormat.format(Instant.parse(receipt.createdAtIso))}  ·  ${receipt.items.size} item(s)  ·  " +
                        "freed ${Sizes.human(receipt.freedBytes)}" +
                        if (receipt.failed.isNotEmpty()) "  ·  left alone ${Sizes.human(receipt.failedBytes)}" else "",
                    fontSize = 12.sp,
                    color = Tokens.textDim,
                )
            }
            Text(
                chipText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = chipColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Tokens.cardInset)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (receipt.undoAvailable()) {
                Button(
                    onClick = onUndo,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.primary,
                        contentColor = Tokens.primaryOn,
                        disabledContainerColor = Tokens.border,
                    ),
                    modifier = Modifier.padding(start = 10.dp),
                ) {
                    Text(if (busy) "moving back…" else "Undo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        PerRuleLines(receipt)
    }
}

/** One line per rule in the receipt — the human story without dumping 20k paths. */
@Composable
private fun PerRuleLines(receipt: Receipt) {
    val byRule = remember(receipt.id) {
        receipt.items
            .groupBy { it.ruleId to it.ruleTitle }
            .entries
            .sortedByDescending { it.value.sumOf { i -> i.sizeBytes } }
            .take(6)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        byRule.forEach { (key, items) ->
            val back = items.count { it.status != Status.RECYCLED }
            Text(
                "${key.second}  ·  ${items.size} item(s) · ${Sizes.human(items.sumOf { it.sizeBytes })}" +
                    if (back > 0) "  ·  $back back home" else "",
                fontSize = 12.sp,
                fontFamily = if (Tokens.mono) FontFamily.Monospace else FontFamily.Default,
                color = Tokens.text,
            )
        }
        if (receipt.items.size > 60) {
            Text(
                "…and ${(receipt.items.groupBy { it.ruleId to it.ruleTitle }).size - byRule.size} more rule(s) in the receipt file.",
                fontSize = 11.5.sp,
                color = Tokens.textDim,
            )
        }
    }
}
