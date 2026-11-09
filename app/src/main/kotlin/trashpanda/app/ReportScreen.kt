package trashpanda.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import trashpanda.engine.execute.Executor
import trashpanda.engine.execute.Receipt
import trashpanda.engine.execute.ReceiptStore
import trashpanda.engine.execute.RecycleOut
import trashpanda.engine.execute.RecyclePort
import trashpanda.engine.execute.Restorer
import trashpanda.engine.execute.Status
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Planner
import trashpanda.platform.winapi.ShellOp
import trashpanda.rules.Risk
import trashpanda.rules.RuleAction
import trashpanda.rules.Verdict

/**
 * Report — DESIGN §4.1 screen 002. Read-only review of a real scan: every item shows its
 * rule, verdict chip and the rule's plain-language explanation; GREEN rules start
 * pre-checked, AMBER wait for your tick, RED are look-only. The move button runs the
 * same Executor the CLI uses (Recycle Bin only, receipt written), then offers Undo —
 * the GUI and the console go through the identical engine path.
 */

/** One rule's cluster of findings. */
private data class Group(
    val id: String,
    val title: String,
    val explanation: String,
    val verdict: Verdict,
    val risk: Risk,
    val items: List<Planner.Item>,
) {
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
}

private sealed interface Block {
    data class Head(val group: Group) : Block
    data class Entry(val item: Planner.Item) : Block
}

/** Which items may ever be ticked — GUI mirror of the executor's own guards. */
private fun selectable(item: Planner.Item): Boolean =
    item.action == RuleAction.RECYCLE && item.rule.risk != Risk.RED

private fun prechecked(plan: Planner.Plan): Set<String> =
    plan.items.filter { selectable(it) && it.rule.risk == Risk.GREEN }
        .map { it.path.toString() }
        .toSet()

@Composable
fun ReportScreen(plan: Planner.Plan, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    val store = remember { ReceiptStore() }
    var selectedPaths by remember(plan) { mutableStateOf(prechecked(plan)) }
    var busy by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Receipt?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    val chosen = plan.items.filter { it.path.toString() in selectedPaths && selectable(it) }

    val groups = remember(plan) {
        plan.items.groupBy { it.rule.id }
            .map { (id, items) ->
                Group(
                    id = id,
                    title = items.first().rule.title,
                    explanation = items.first().rule.explanation,
                    verdict = items.first().rule.verdict,
                    risk = items.first().rule.risk,
                    items = items,
                )
            }
            .sortedByDescending { it.totalBytes }
    }
    val blocks = remember(groups) {
        groups.flatMap { g -> listOf<Block>(Block.Head(g)) + g.items.map { Block.Entry(it) } }
    }
    val lookOnlyBytes = plan.items
        .filter { it.action != RuleAction.RECYCLE }
        .sumOf { it.sizeBytes }
    val listState = rememberLazyListState()

    // A fresh receipt is the news of the moment — bring it to the top.
    LaunchedEffect(receipt?.id) { if (receipt != null) listState.scrollToItem(0) }

    fun executeSelection() {
        if (busy || chosen.isEmpty()) return
        busy = true
        failure = null
        scope.launch {
            try {
                receipt = withContext(Dispatchers.IO) {
                    val port = RecyclePort { p ->
                        ShellOp.recycleToBin(p).let { RecycleOut(it.code, it.aborted) }
                    }
                    Executor(port, store).execute(chosen)
                }
                selectedPaths = emptySet()
            } catch (t: Throwable) {
                failure = "The move did not happen: ${t.message ?: t::class.simpleName}. Nothing left the disk."
            } finally {
                busy = false
            }
        }
    }

    fun undoSelection() {
        val r = receipt ?: return
        if (busy || r.recycled.isEmpty()) return
        busy = true
        failure = null
        scope.launch {
            try {
                receipt = withContext(Dispatchers.IO) { Restorer(store).restore(r) }
            } catch (t: Throwable) {
                failure = "Undo stopped: ${t.message ?: t::class.simpleName}. The bin entry is still there."
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .padding(vertical = 18.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← Home", color = Tokens.textDim, fontSize = 13.sp) }
                Spacer(Modifier.width(10.dp))
                Text("Report", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
            }
            Text(
                "${plan.items.size} item(s) · ${Sizes.human(plan.recyclableBytes)} cleanable · " +
                    "${Sizes.human(lookOnlyBytes)} shown for eyes only. Nothing has moved yet — " +
                    "Bin only acts on what you tick and confirm.",
                fontSize = 13.sp,
                color = Tokens.textDim,
            )
            failure?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.5.sp, color = Tokens.riskDanger, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Scrollable list (grouped, lazy) ──────────────────────────────────
        if (blocks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Nothing to report — this disk is smiling.", fontSize = 15.sp, color = Tokens.text)
                Text("The scan found no rule matches. Nothing was touched, either.", fontSize = 12.sp, color = Tokens.textDim)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                receipt?.let { r ->
                    item(key = "receipt-${r.id}") {
                        ReceiptPanel(receipt = r, busy = busy, onUndo = ::undoSelection)
                    }
                }
                items(blocks.size, key = { index ->
                    when (val b = blocks[index]) {
                        is Block.Head -> "head-${b.group.id}"
                        is Block.Entry -> "item-${b.item.path}"
                    }
                }) { index ->
                    when (val b = blocks[index]) {
                        is Block.Head -> HeadCard(b.group)
                        is Block.Entry -> EntryRow(
                            item = b.item,
                            checked = b.item.path.toString() in selectedPaths,
                            enabled = selectable(b.item) && !busy,
                            onToggle = {
                                val p = b.item.path.toString()
                                selectedPaths =
                                    if (p in selectedPaths) selectedPaths - p else selectedPaths + p
                            },
                        )
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(12.dp)) }
            }
        }

        // ── Footer CTA + explicit two-step confirm (GUI mirror of CLI --ask) ──
        FooterBar(
            chosenCount = chosen.size,
            chosenBytes = chosen.sumOf { it.sizeBytes },
            busy = busy,
            confirmOpen = confirmOpen,
            onAsk = { if (chosen.isNotEmpty() && !busy) { failure = null; confirmOpen = true } },
            onCancelConfirm = { confirmOpen = false },
            onConfirm = { executeSelection() },
        )
    }
}

// ────────────────────────────────────────────────────────────────── footer

@Composable
private fun FooterBar(
    chosenCount: Int,
    chosenBytes: Long,
    busy: Boolean,
    confirmOpen: Boolean,
    onAsk: () -> Unit,
    onCancelConfirm: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.surface.copy(alpha = 0.72f))
            .border(1.dp, Tokens.border, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (confirmOpen) {
            // Explicit confirmation, per DESIGN: say what moves, and say undo exists.
            Text(
                "Moving ${Sizes.human(chosenBytes)} across $chosenCount item(s) to the Recycle Bin. " +
                    "Undo anytime from the receipt.",
                fontSize = 13.5.sp,
                color = Tokens.text,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Windows only hides them from view — trashpanda can put every single one back. " +
                    "Nothing is ever deleted.",
                fontSize = 12.sp,
                color = Tokens.textDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancelConfirm) {
                    Text("Cancel — nothing moves", color = Tokens.textDim, fontWeight = FontWeight.SemiBold)
                }
                MittButton(
                    label = "Yes — move $chosenCount item(s)",
                    enabled = !busy,
                    onClick = onConfirm,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Selected for the Recycle Bin", fontSize = 12.sp, color = Tokens.textDim)
                    Text(
                        if (chosenCount == 0) "nothing ticked yet" else
                            "$chosenCount item(s) · ${Sizes.human(chosenBytes)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (chosenCount == 0) Tokens.textDim else Tokens.primary,
                    )
                }
                MittButton(
                    label = if (busy) "Bin is munching…" else "Move to Recycle Bin",
                    enabled = !busy,
                    onClick = onAsk,
                )
            }
        }
    }
}

@Composable
private fun MittButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tokens.primary,
            contentColor = Tokens.primaryOn,
            disabledContainerColor = Tokens.border,
        ),
        modifier = Modifier.height(44.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
    }
}

// ─────────────────────────────────────────────────────────── group + rows

@Composable
private fun HeadCard(group: Group) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.surface.copy(alpha = 0.72f))
            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                group.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Tokens.text,
                modifier = Modifier.weight(1f),
            )
            VerdictChip(group.verdict)
            Text(Sizes.human(group.totalBytes), fontSize = 13.sp, color = Tokens.text, fontFamily = FontFamily.Monospace)
        }
        Text(group.explanation, fontSize = 12.sp, color = Tokens.textDim)
    }
}

@Composable
private fun EntryRow(
    item: Planner.Item,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.cardInset)
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TickBox(checked = checked, enabled = enabled, onToggle = onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                item.path.fileName?.toString() ?: item.path.toString(),
                fontSize = 13.sp,
                color = if (enabled) Tokens.text else Tokens.textDim,
                fontWeight = FontWeight.Medium,
            )
            Text(
                item.path.parent?.toString() ?: item.path.toString(),
                fontSize = 10.5.sp,
                color = Tokens.textDim,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text(Sizes.human(item.sizeBytes), fontSize = 12.5.sp, color = Tokens.textDim, fontFamily = FontFamily.Monospace)
        if (!enabled) LookOnlyChip(item)
    }
}

/** Tiny hand-rolled checkbox in design tokens (GREEN pre-checked, disable for look-only). */
@Composable
private fun TickBox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    val fill = when {
        checked && enabled -> Tokens.primary
        checked -> Tokens.textDim.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val line = if (enabled) Tokens.textDim.copy(alpha = 0.9f) else Tokens.border
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(shape)
            .background(fill)
            .border(1.dp, line, shape)
            .then(if (enabled) Modifier.clickable(onClick = onToggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Tokens.primaryOn, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ──────────────────────────────────────────────────────────── chips

@Composable
private fun VerdictChip(verdict: Verdict) {
    val (fg, bg, label) = when (verdict) {
        Verdict.TRASH -> Triple(Tokens.riskSafe, Color(0x1F22C55E), "trash")
        Verdict.REGENERATES -> Triple(Tokens.focus, Color(0x1F818CF8), "regenerates")
        Verdict.REVIEW -> Triple(Tokens.riskReview, Color(0x1FF59E0B), "review")
        Verdict.KEEP -> Triple(Tokens.riskDanger, Color(0x1FEF4444), "keep")
    }
    Chip(fg = fg, bg = bg, label = label)
}

@Composable
private fun LookOnlyChip(item: Planner.Item) {
    val label =
        if (item.action != RuleAction.RECYCLE) "report only" else "look, don't touch"
    Chip(fg = Tokens.riskDanger, bg = Color(0x1FEF4444), label = label)
}

@Composable
private fun Chip(fg: Color, bg: Color, label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(label, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

// ────────────────────────────────────────────────────── receipt panel

@Composable
private fun ReceiptPanel(receipt: Receipt, busy: Boolean, onUndo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Tokens.surface)
            .border(1.dp, Tokens.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Receipt ${receipt.id}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tokens.primary)
            Text(receipt.state, fontSize = 11.5.sp, color = Tokens.textDim, fontFamily = FontFamily.Monospace)
        }
        Text(
            "moved to bin: ${receipt.recycled.size} item(s) · ${Sizes.human(receipt.freedBytes)}" +
                if (receipt.failed.isNotEmpty()) "  ·  left alone (moved refused): ${receipt.failed.size} item(s) · ${Sizes.human(receipt.failedBytes)}" else "",
            fontSize = 12.5.sp,
            color = Tokens.text,
        )
        if (receipt.recycled.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onUndo, enabled = !busy) {
                    Text(
                        if (busy) "undoing…" else "Undo this receipt — put all ${receipt.recycled.size} back",
                        color = if (busy) Tokens.textDim else Tokens.focus,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        receipt.items.sortedByDescending { it.sizeBytes }.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Tokens.bg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(status = item.status)
                Column(Modifier.weight(1f)) {
                    Text(
                        item.originalPath,
                        fontSize = 11.sp,
                        color = Tokens.text,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                    item.error?.let { Text(it, fontSize = 10.5.sp, color = Tokens.riskReview) }
                    item.restoredTo?.takeIf { it != item.originalPath }?.let {
                        Text("restored next door: $it", fontSize = 10.5.sp, color = Tokens.focus)
                    }
                }
                Text(Sizes.human(item.sizeBytes), fontSize = 11.sp, color = Tokens.textDim)
            }
        }
        Text(
            "receipt file: %APPDATA%\\TrashPanda\\receipts\\${receipt.id}.json",
            fontSize = 10.sp,
            color = Tokens.textDim,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun StatusChip(status: String) {
    val (fg, bg, label) = when (status) {
        Status.RECYCLED -> Triple(Tokens.primary, Color(0x1F34D399), "in bin")
        Status.RESTORED -> Triple(Tokens.focus, Color(0x1F818CF8), "restored")
        else -> Triple(Tokens.riskReview, Color(0x1FF59E0B), "left alone")
    }
    Chip(fg = fg, bg = bg, label = label)
}
