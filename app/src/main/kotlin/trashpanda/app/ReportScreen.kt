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
import kotlinx.coroutines.CoroutineDispatcher
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
import trashpanda.platform.AppDirs
import trashpanda.platform.io.BinIndex
import trashpanda.platform.winapi.ShellOp
import trashpanda.rules.Risk
import trashpanda.rules.RuleAction
import trashpanda.rules.Verdict
import java.nio.file.Path

/**
 * Report — DESIGN §4.1 screen 002. Read-only review of a real scan: every item shows its
 * rule, verdict chip and the rule's plain-language explanation; GREEN rules start
 * pre-checked, AMBER wait for your tick, RED are look-only. The move button runs the
 * same Executor the CLI uses (Recycle Bin only, receipt written), then offers Undo —
 * the GUI and the console go through the identical engine path.
 */

/** One rule's cluster of findings — a card with title, verdict chip, explanation, subtotal. */
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

/**
 * One trash category — a stack of rule groups. Carries the tick-all/clear one-click:
 * every GREEN-safe item in the whole category goes in one button press.
 */
private data class CategoryGroup(
    val id: trashpanda.rules.CategoryId,
    val display: String,
    val rules: List<Group>,
) {
    val items: List<Planner.Item> get() = rules.flatMap { it.items }
    val totalBytes: Long get() = items.sumOf { it.sizeBytes }
    val tickable: List<Planner.Item> get() = items.filter { selectable(it) }
}

private sealed interface Block {
    data class Category(val cat: CategoryGroup) : Block
    /** Rule head carries how many of its items are listed directly (preview vs expanded). */
    data class Rule(val group: Group, val shownCount: Int, val expanded: Boolean) : Block
    data class Entry(val item: Planner.Item) : Block
    /** "show all N items" / "collapse" row for big rules — 20k-list relief valve. */
    data class SeeAll(val group: Group, val expand: Boolean, val hiddenCount: Int) : Block
}

/** Items of a rule listed directly before "show all" — keeps huge rules (20k+ temp files) renderable. */
private const val PREVIEW_ITEMS = 3

/** Which items may ever be ticked — GUI mirror of the executor's own guards. */
private fun selectable(item: Planner.Item): Boolean =
    item.action == RuleAction.RECYCLE && item.rule.risk != Risk.RED

private fun prechecked(plan: Planner.Plan): Set<String> =
    plan.items.filter { selectable(it) && it.rule.risk == Risk.GREEN }
        .map { it.path.toString() }
        .toSet()

@Composable
fun ReportScreen(
    plan: Planner.Plan,
    onBack: () -> Unit,
    /** Where every Recycle Bin move really happens. Defaults to the real Windows shell call. */
    port: RecyclePort = { p -> ShellOp.recycleToBin(p).let { RecycleOut(it.code, it.aborted) } },
    /** Which Recycle Bin folders undo searches. Defaults to the real bin; tests inject fixtures. */
    binFolders: (Path) -> List<Path> = { BinIndex.readableBinFolders(it) },
    /** Where receipts are written. Defaults to %APPDATA%\Trashpanda\receipts. */
    receiptsDir: Path = AppDirs.receipts,
    /** Which thread performs scan-independent engine work. Defaults to Dispatchers.IO. */
    worker: CoroutineDispatcher = Dispatchers.IO,
) {
    val scope = rememberCoroutineScope()

    val store = remember(receiptsDir) { ReceiptStore(receiptsDir) }
    var selectedPaths by remember(plan) { mutableStateOf(prechecked(plan)) }
    var expandedRules by remember(plan) { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var confirmOpen by remember { mutableStateOf(false) }
    var receipt by remember { mutableStateOf<Receipt?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Remembered: filtering 20k+ items on every recomposition would make ticks feel dead.
    val chosen = remember(selectedPaths, plan) {
        plan.items.filter { it.path.toString() in selectedPaths && selectable(it) }
    }
    val cleanablePaths = remember(plan) {
        plan.items.filter { selectable(it) }.map { it.path.toString() }
    }

    val groups = remember(plan) {
        plan.items.groupBy { it.rule.id }
            .map { (_, items) ->
                Group(
                    id = items.first().rule.id,
                    title = items.first().rule.title,
                    explanation = items.first().rule.explanation,
                    verdict = items.first().rule.verdict,
                    risk = items.first().rule.risk,
                    items = items,
                )
            }
            .sortedByDescending { it.totalBytes }
    }
    val cats = remember(groups) {
        groups.groupBy { it.items.first().rule.category }
            .map { (category, rules) ->
                CategoryGroup(
                    id = category,
                    display = category.display,
                    rules = rules,
                )
            }
            .sortedByDescending { it.totalBytes }
    }
    val blocks = remember(cats, expandedRules) {
        cats.flatMap { c ->
            buildList {
                add(Block.Category(c))
                c.rules.forEach { g ->
                    val expanded = g.id in expandedRules
                    val shown = if (expanded) g.items.size else minOf(PREVIEW_ITEMS, g.items.size)
                    add(Block.Rule(g, shown, expanded))
                    g.items.take(shown).forEach { add(Block.Entry(it)) }
                    val hidden = g.items.size - shown
                    if (hidden > 0) {
                        add(Block.SeeAll(g, expand = true, hiddenCount = hidden))
                    } else if (expanded && g.items.size > PREVIEW_ITEMS) {
                        add(Block.SeeAll(g, expand = false, hiddenCount = 0))
                    }
                }
            }
        }
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
                receipt = withContext(worker) {
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
                receipt = withContext(worker) { Restorer(store, binFolders).restore(r) }
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
                        is Block.Category -> "cat-${b.cat.id}"
                        is Block.Rule -> "head-${b.group.id}"
                        is Block.Entry -> "item-${b.item.path}"
                        is Block.SeeAll -> "see-${b.group.id}"
                    }
                }) { index ->
                    when (val b = blocks[index]) {
                        is Block.Category -> CategoryHeadCard(
                            cat = b.cat,
                            tickedCount = b.cat.tickable.count { it.path.toString() in selectedPaths },
                            busy = busy,
                            onTickAll = {
                                selectedPaths = selectedPaths + b.cat.tickable.map { it.path.toString() }
                            },
                            onClear = {
                                selectedPaths = selectedPaths - b.cat.items.map { it.path.toString() }
                            },
                        )
                        is Block.Rule -> RuleHeadCard(
                            group = b.group,
                            shownCount = b.shownCount,
                            tickedCount = b.group.items.count { selectable(it) && it.path.toString() in selectedPaths },
                            tickableCount = b.group.items.count { selectable(it) },
                            busy = busy,
                            onToggleRule = {
                                val paths = b.group.items.filter { selectable(it) }.map { it.path.toString() }
                                selectedPaths =
                                    if (paths.isNotEmpty() && paths.all { it in selectedPaths }) {
                                        selectedPaths - paths.toSet()
                                    } else {
                                        selectedPaths + paths
                                    }
                            },
                            onSeeAll = {
                                expandedRules =
                                    if (b.group.id in expandedRules) expandedRules - b.group.id
                                    else expandedRules + b.group.id
                            },
                        )
                        is Block.SeeAll -> SeeAllRow(
                            group = b.group,
                            expand = b.expand,
                            hiddenCount = b.hiddenCount,
                            onToggle = {
                                expandedRules =
                                    if (b.group.id in expandedRules) expandedRules - b.group.id
                                    else expandedRules + b.group.id
                            },
                        )
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
            hasCleanable = cleanablePaths.isNotEmpty(),
            hasSelection = selectedPaths.isNotEmpty(),
            onSelectAll = { selectedPaths = selectedPaths + cleanablePaths },
            onClearAll = { selectedPaths = emptySet() },
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
    hasCleanable: Boolean,
    hasSelection: Boolean,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
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
            // The global one-click: every cleanable item in the whole plan, or a full reset.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onSelectAll, enabled = hasCleanable && !busy) {
                    Text(
                        "tick every cleanable item",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Tokens.primary,
                    )
                }
                TextButton(onClick = onClearAll, enabled = hasSelection && !busy) {
                    Text("clear everything", fontSize = 12.sp, color = Tokens.textDim)
                }
            }
            Text(
                "One-click clean: 'tick all' on a category sweeps it — GREEN items are pre-ticked already, trashpanda only asks twice.",
                fontSize = 11.sp,
                color = Tokens.textDim,
            )
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

/**
 * Category head — the one-click level. "tick all" ticks every GREEN- or AMBER-cleanable
 * item in the category (RED/report-only stay untouchable on every path); "clear" drops
 * the whole category from the selection. Look-only categories get no buttons at all.
 */
@Composable
private fun CategoryHeadCard(
    cat: CategoryGroup,
    tickedCount: Int,
    busy: Boolean,
    onTickAll: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.primary.copy(alpha = 0.07f))
            .border(1.dp, Tokens.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                cat.display,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Tokens.text,
                modifier = Modifier.weight(1f),
            )
            Text(Sizes.human(cat.totalBytes), fontSize = 13.5.sp, color = Tokens.text, fontFamily = FontFamily.Monospace)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val pile = if (tickedCount > 0) {
                "${cat.tickable.size} cleanable · $tickedCount ticked"
            } else {
                "${cat.items.size} item(s)"
            }
            Text(pile, fontSize = 11.5.sp, color = Tokens.textDim, modifier = Modifier.weight(1f))
            if (cat.tickable.isNotEmpty()) {
                TextButton(onClick = onTickAll, enabled = !busy) {
                    Text("tick all", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.primary)
                }
                TextButton(onClick = onClear, enabled = !busy) {
                    Text("clear", fontSize = 12.5.sp, color = Tokens.textDim)
                }
            } else {
                Chip(fg = Tokens.riskDanger, bg = Color(0x1FEF4444), label = "report only")
            }
        }
    }
}

/**
 * Rule head — now a selectable unit (the 20k relief): its checkbox ticks/unticks every
 * cleanable item of the rule in ONE press, plus click whole row to toggle. Caption shows
 * "N item(s) · M ticked" so a 12k-line preview is never needed to drive selection.
 */
@Composable
private fun RuleHeadCard(
    group: Group,
    shownCount: Int,
    tickedCount: Int,
    tickableCount: Int,
    busy: Boolean,
    onToggleRule: () -> Unit,
    onSeeAll: () -> Unit,
) {
    val canToggle = tickableCount > 0 && !busy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.surface.copy(alpha = 0.72f))
            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
            .then(if (canToggle) Modifier.clickable(onClick = onToggleRule) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tickableCount > 0) {
                TickBox(checked = tickedCount == tickableCount, enabled = !busy, onToggle = onToggleRule)
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(group.explanation, fontSize = 12.sp, color = Tokens.textDim, modifier = Modifier.weight(1f))
            val pile = if (tickableCount > 0 && tickedCount > 0) {
                "${group.items.size} item(s) · $tickedCount ticked"
            } else {
                "${group.items.size} item(s)"
            }
            Text(pile, fontSize = 11.sp, color = Tokens.textDim)
        }
    }
}

/** "show all 12,345 items" / "collapse" — keeps giant rules one row instead of 12k rows. */
@Composable
private fun SeeAllRow(group: Group, expand: Boolean, hiddenCount: Int, onToggle: () -> Unit) {
    Text(
        if (expand) "show all $hiddenCount more item(s)" else "collapse this rule",
        fontSize = 11.5.sp,
        color = Tokens.primary,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.cardInset)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
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
            "receipt file: %APPDATA%\\Trashpanda\\receipts\\${receipt.id}.json",
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
