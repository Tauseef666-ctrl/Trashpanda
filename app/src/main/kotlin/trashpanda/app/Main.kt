package trashpanda.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Planner
import trashpanda.platform.winapi.Drives
import trashpanda.rules.RuleAction
import trashpanda.rules.RuleCatalog
import trashpanda.rules.Risk
import java.nio.file.Path

/**
 * Trashpanda v0.2 — Home "Drive health" (DESIGN §4.1 screen 001) + Report (002).
 * Scan runs the REAL read-only engine (Planner) over the machine's user-writable junk
 * hotspots; the report screen wires the same Executor the CLI uses — Recycle Bin only,
 * receipt, undo. GUI and console share one engine path.
 */
/** Loads the bundled raccoon icon (falls back to default if missing). */
private fun windowIcon(): java.awt.Image? = runCatching {
    val url = Tokens::class.java.getResource("/icon.png") ?: return@runCatching null
    javax.swing.ImageIcon(url).image
}.getOrNull()

/** Scan scope picked on Home: quick user-temp sweep vs the deep drive sweep the user PICKS. */
enum class ScanScope(val label: String) {
    FAST("temp + apps"),
    DEEP("drives"),
}

private enum class Screen { HOME, REPORT, HISTORY }

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Trashpanda — eats your digital garbage. Leaves your files alone.",
        state = rememberWindowState(width = 1120.dp, height = 800.dp),
    ) {
        LaunchedEffect(Unit) {
            windowIcon()?.let { window.iconImage = it }
        }

        var screen by remember { mutableStateOf(Screen.HOME) }
        var plan by remember { mutableStateOf<Planner.Plan?>(null) }
        var scanning by remember { mutableStateOf(false) }
        var theme by remember { mutableStateOf(Settings.loadTheme()) }
        var scope by remember { mutableStateOf(ScanScope.FAST) }
        var pickedDrives by remember { mutableStateOf(emptySet<String>()) }
        LaunchedEffect(theme) { Settings.saveTheme(theme) }
        val jobs = rememberCoroutineScope()

        fun scanAndOpen() {
            if (scanning) return
            scanning = true
            jobs.launch {
                try {
                    plan = withContext(Dispatchers.IO) { realScan(scope, pickedDrives) }
                    screen = Screen.REPORT
                } finally {
                    scanning = false
                }
            }
        }

        TrashPandaTheme(theme) {
            Box(Modifier.fillMaxSize()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        plan = plan,
                        scanning = scanning,
                        theme = theme,
                        onTheme = { theme = it },
                        scope = scope,
                        onScope = { scope = it },
                        pickedDrives = pickedDrives,
                        onPickDrive = { root, add ->
                            pickedDrives = if (add) pickedDrives + root else pickedDrives - root
                        },
                        onScan = ::scanAndOpen,
                        onReview = { screen = Screen.REPORT },
                        onHistory = { screen = Screen.HISTORY },
                    )
                    Screen.REPORT -> {
                        val current = plan
                        if (current == null) {
                            HomeScreen(
                                plan = null,
                                scanning = scanning,
                                theme = theme,
                                onTheme = { theme = it },
                                scope = scope,
                                onScope = { scope = it },
                                pickedDrives = pickedDrives,
                                onPickDrive = { root, add ->
                                    pickedDrives = if (add) pickedDrives + root else pickedDrives - root
                                },
                                onScan = ::scanAndOpen,
                                onReview = { screen = Screen.REPORT },
                                onHistory = { screen = Screen.HISTORY },
                            )
                        } else {
                            ReportScreen(current, onBack = { screen = Screen.HOME })
                        }
                    }
                    Screen.HISTORY -> HistoryScreen(onBack = { screen = Screen.HOME })
                }
                Scanlines()
            }
        }
    }
}

// Pre-first-scan the hero shows REAL drive facts (computed live from the machine).
// Junk lists and reclaim numbers exist only after a real scan — never invented.
// Drive volumes come from platform's Windows API bridge: true storage TYPES included.

private data class JunkRow(
    val title: String,
    val path: String,
    val sizeLabel: String,
    val risk: Risk,
)

@Composable
fun HomeScreen(
    plan: Planner.Plan?,
    scanning: Boolean,
    theme: ThemeName,
    onTheme: (ThemeName) -> Unit,
    scope: ScanScope,
    onScope: (ScanScope) -> Unit,
    pickedDrives: Set<String>,
    onPickDrive: (String, Boolean) -> Unit,
    onScan: () -> Unit,
    onReview: () -> Unit,
    onHistory: () -> Unit = {},
) {
    val drives = remember { Drives.volumes() }
    val totalBytes = drives.sumOf { it.totalBytes }
    val freeBytes = drives.sumOf { it.freeBytes }
    val reclaimable = plan?.recyclableBytes
    val junkCount = plan?.items?.size
    val autoPct = autoCheckableShare(plan)
    val tickPct = plan?.let { maxOf(0, 100 - (autoPct ?: 100)) }

    // Top junk categories straight from the real plan (fallback: none — say so honestly).
    val junkRows: List<JunkRow> = remember(plan) {
        if (plan == null) {
            emptyList()
        } else {
            plan.items.groupBy { it.rule.id }
                .map { (_, members) ->
                    val bytes = members.sumOf { it.sizeBytes }
                    bytes to JunkRow(
                        title = members.first().rule.title,
                        path = "${members.size} item(s) · e.g. ${members.first().path.fileName}",
                        sizeLabel = Sizes.human(bytes),
                        risk = members.first().rule.risk,
                    )
                }
                .sortedByDescending { it.first }
                .take(6)
                .map { it.second }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header
        Row {
            Column(Modifier.weight(1f)) {
                Text("Drive health", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                Text(
                    if (plan == null) {
                        "Not scanned yet. Every scan is read-only — it never changes the disk."
                    } else {
                        "Live read-only scan · found ${Sizes.human(plan.recyclableBytes)} across " +
                            "${plan.items.size} item(s) over ${plan.scannedRoots.size} root(s). Nothing has moved yet."
                    },
                    fontSize = 13.sp,
                    color = Tokens.textDim,
                )
            }
            TextButton(onClick = onHistory) {
                Text("History", color = Tokens.textDim, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
            }
            ThemeSwitch(current = theme, onPick = onTheme)
        }

        // Sweep scope — quick user-temp pass or the drives the user must PICK
        // (user requirement: nothing is scanned without an explicit switch).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sweep scope", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.textDim)
            ScopeSwitch(current = scope, onPick = onScope)
        }
        if (scope == ScanScope.DEEP) {
            val fixedRoots = remember { Drives.fixedVolumes().map { it.root } }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (pickedDrives.isEmpty()) "pick a drive:" else "sweeping:",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.textDim,
                )
                fixedRoots.forEach { root ->
                    val active = root in pickedDrives
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Tokens.primary.copy(alpha = 0.16f) else Color.Transparent)
                            .border(1.dp, if (active) Tokens.primary else Tokens.border, RoundedCornerShape(999.dp))
                            .clickable { onPickDrive(root, !active) }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Text(
                            root.trimEnd('\\'),
                            fontSize = 11.sp,
                            color = if (active) Tokens.primary else Tokens.textDim,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        // Hero: donut + stats — every ready drive stacked into one picture, real
        // numbers only (per-drive breakdown underneath; nothing invented pre-scan).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Tokens.surface.copy(alpha = 0.72f))
                .border(1.dp, Tokens.border, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            val reclaimFraction =
                if (totalBytes <= 0L) 0f
                else ((reclaimable ?: 0L) / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                StorageDonut(
                    usedFraction = if (totalBytes <= 0L) 0f else
                        ((totalBytes - freeBytes) / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f),
                    reclaimFraction = reclaimFraction,
                    modifier = Modifier.size(190.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (totalBytes <= 0L) "—" else Sizes.human(freeBytes),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tokens.text,
                    )
                    Text(
                        if (totalBytes <= 0L) "free space unknown" else "free of ${Sizes.human(totalBytes)}",
                        fontSize = 12.sp, color = Tokens.textDim,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Reclaimable now", reclaimable?.let { Sizes.human(it) } ?: "not scanned", Tokens.primary, Modifier.weight(1f))
                    StatCard("Junk items", junkCount?.toString() ?: "not scanned", Tokens.text, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Auto-checkable", autoPct?.let { "$it%" } ?: "not scanned", Tokens.riskSafe, Modifier.weight(1f))
                    StatCard("Needs your tick", tickPct?.let { "$it%" } ?: "not scanned", Tokens.riskReview, Modifier.weight(1f))
                }
            }
        }

        // Per-drive storage strip — the "stack" that shows every drive and its type.
        if (drives.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                drives.forEach { d ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tokens.cardInset)
                            .border(1.dp, Tokens.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column {
                            Text(
                                "${d.root.trimEnd('\\')} · ${d.kind.label.uppercase()}",
                                fontSize = 10.sp,
                                color = Tokens.textDim,
                                letterSpacing = 0.8.sp,
                            )
                            Text(
                                "${Sizes.human(d.freeBytes)} free of ${Sizes.human(d.totalBytes)}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Tokens.text,
                            )
                        }
                    }
                }
            }
        }

        // Top junk + side column
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tokens.surface.copy(alpha = 0.72f))
                    .border(1.dp, Tokens.border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Top junk categories", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                Text("real rules matched · biggest piles first", fontSize = 12.sp, color = Tokens.textDim)
                if (junkRows.isEmpty()) {
                    Text(
                        "Bin hasn't swept yet — press the mint button and it will read your junk piles (never touching them).",
                        fontSize = 12.5.sp,
                        color = Tokens.textDim,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                } else {
                    junkRows.forEach { row -> JunkRowView(row) }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tokens.surface.copy(alpha = 0.72f))
                        .border(1.dp, Tokens.border, RoundedCornerShape(16.dp))
                        .padding(18.dp),
                ) {
                    Text("The three promises", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Read-only by default — scanning never changes the disk",
                        "Reversible forever — Recycle Bin + receipt with restore",
                        "Explained, not implied — no explanation, no deletion",
                    ).forEach {
                        Text("✓  $it", fontSize = 12.5.sp, color = Tokens.textDim, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }

                // The CTA runs the REAL read-only engine, then opens the report.
                // Deep mode only arms once the user picked at least one drive.
                val ctaLabel = when {
                    scanning -> "Bin is sweeping… (read-only)"
                    scope == ScanScope.FAST -> "Scan now — temp + crash dumps (read-only)"
                    pickedDrives.isEmpty() -> "Pick a drive to sweep first"
                    else -> "Scan " + pickedDrives.sorted().joinToString(" + ") { it.trimEnd('\\') } +
                        " — deep sweep (read-only)"
                }
                Button(
                    onClick = onScan,
                    enabled = !scanning && (scope == ScanScope.FAST || pickedDrives.isNotEmpty()),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.primary,
                        contentColor = Tokens.primaryOn,
                        disabledContainerColor = Tokens.border,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(ctaLabel, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (plan != null && !scanning) {
                    TextButton(
                        onClick = onReview,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                    ) {
                        Text(
                            "Open the report → tick what goes",
                            color = Tokens.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        Text(
            "Trashpanda v0.2 · scan → report → Recycle Bin with receipts · undo from any receipt",
            fontSize = 11.sp,
            color = Tokens.textDim,
        )
    }
}

/** How much of the reclaimable pile GREEN rules own — the "auto-checkable" promise in %. */
private fun autoCheckableShare(plan: Planner.Plan?): Int? {
    plan ?: return null
    val recyclable = plan.items.filter { it.action == RuleAction.RECYCLE }
    val total = recyclable.sumOf { it.sizeBytes }
    if (total == 0L) return 100
    val green = recyclable.filter { it.rule.risk == Risk.GREEN }.sumOf { it.sizeBytes }
    return ((green * 100L) / total).toInt()
}

/**
 * Real read-only engine pass (user requirement: the scan covers EXACTLY what the user
 * picked — never a side-sweep of other drives). FAST: user junk hotspots (temp, crash
 * dumps, WER) at full walking depth. DEEP: for each PICKED drive only — its
 * Windows\Temp at full depth (depth 12) plus the drive root at depth 8 — merged
 * app-side (paths de-duplicated, counters summed).
 */
private fun realScan(scope: ScanScope, pickedDrives: Set<String>): Planner.Plan {
    val planner = Planner(RuleCatalog.loadBuiltin())
    if (scope == ScanScope.FAST) return planner.planFromDisk(userJunkRoots())
    if (pickedDrives.isEmpty()) {
        return Planner.Plan(items = emptyList(), deniedDirs = 0, reparseMirrors = 0, scannedRoots = emptyList())
    }

    val perDrive = pickedDrives.sorted().map { root ->
        val rootPath = Path.of(root)
        mergePlans(listOf(
            planner.planFromDisk(listOf(rootPath.resolve("Windows").resolve("Temp")), maxDepth = 12),
            planner.planFromDisk(listOf(rootPath), maxDepth = 8),
        ))
    }
    return mergePlans(perDrive)
}

/** User-writable junk hotspots every Windows machine has. planFromDisk skips missing roots. */
private fun userJunkRoots(): List<Path> {
    val lad = System.getenv("LOCALAPPDATA")
    return listOfNotNull(
        lad?.let { Path.of(it, "Temp") },
        lad?.let { Path.of(it, "CrashDumps") },
        lad?.let { Path.of(it, "Microsoft", "Windows", "WER", "ReportQueue") },
        lad?.let { Path.of(it, "Microsoft", "Windows", "WER", "ReportArchive") },
    )
}

/**
 * Merge already-matched plans into one: de-duplicate by path (case-insensitive, like
 * Windows), sum the counters, concatenate scanned roots. Never re-matches — each part
 * already went through the same first-rule-wins Planner.
 */
private fun mergePlans(parts: List<Planner.Plan>): Planner.Plan {
    val seen = HashSet<String>()
    val items = mutableListOf<Planner.Item>()
    for (part in parts) {
        for (item in part.items) {
            if (seen.add(item.path.toString().lowercase())) items += item
        }
    }
    return Planner.Plan(
        items = items.sortedByDescending { it.sizeBytes },
        deniedDirs = parts.sumOf { it.deniedDirs },
        reparseMirrors = parts.sumOf { it.reparseMirrors },
        scannedRoots = parts.flatMap { it.scannedRoots },
    )
}

/** Scope switcher pills — quick sweep vs every drive (mirrors the theme switcher style). */
@Composable
private fun ScopeSwitch(current: ScanScope, onPick: (ScanScope) -> Unit) {
    ScanScope.entries.forEach { s ->
        val active = s == current
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) Tokens.primary.copy(alpha = 0.16f) else Color.Transparent)
                .border(1.dp, if (active) Tokens.primary else Tokens.border, RoundedCornerShape(999.dp))
                .clickable { onPick(s) }
                .padding(horizontal = 11.dp, vertical = 5.dp),
        ) {
            Text(
                s.label,
                fontSize = 11.sp,
                color = if (active) Tokens.primary else Tokens.textDim,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Theme switcher pills — glass · milk · neon (DESIGN §3.1, live in the header). */
@Composable
private fun ThemeSwitch(current: ThemeName, onPick: (ThemeName) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemeName.entries.forEach { t ->
            val active = t == current
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (active) Tokens.primary.copy(alpha = 0.16f) else Color.Transparent)
                    .border(1.dp, if (active) Tokens.primary else Tokens.border, RoundedCornerShape(999.dp))
                    .clickable { onPick(t) }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(
                    t.label,
                    fontSize = 11.sp,
                    color = if (active) Tokens.primary else Tokens.textDim,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * CRT scanlines overlay — only the Terminal neon theme asks for it (DESIGN §3.1:
 * 4% black stripes). Sits on top of every screen, never catches input.
 */
@Composable
private fun Scanlines() {
    if (!Tokens.scanline) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        var y = 0f
        while (y < size.height) {
            drawRect(
                Color.Black.copy(alpha = 0.05f),
                topLeft = Offset(0f, y),
                size = Size(size.width, 1f),
            )
            y += 4f
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Tokens.cardInset)
            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), fontSize = 11.sp, color = Tokens.textDim, letterSpacing = 0.8.sp)
        if (value == "not scanned") {
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Tokens.textDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = valueColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun JunkRowView(row: JunkRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.cardInset)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
            Text(row.path, fontSize = 11.sp, color = Tokens.textDim, fontFamily = FontFamily.Monospace)
        }
        Text(row.sizeLabel, fontSize = 13.sp, color = Tokens.text, fontFamily = FontFamily.Monospace)
        RiskChip(row.risk)
    }
}

@Composable
private fun RiskChip(risk: Risk) {
    val (fg, bg) = when (risk) {
        Risk.GREEN -> Tokens.riskSafe to Color(0x1F22C55E)
        Risk.AMBER -> Tokens.riskReview to Color(0x1FF59E0B)
        Risk.RED -> Tokens.riskDanger to Color(0x1FEF4444)
    }
    val label = when (risk) {
        Risk.GREEN -> "GREEN · safe"
        Risk.AMBER -> "AMBER · you tick"
        Risk.RED -> "RED · look only"
    }
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

@Composable
private fun StorageDonut(usedFraction: Float, reclaimFraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(usedFraction, tween(700), label = "used")
    val track = Tokens.donutTrack
    val used = Tokens.donutUsed
    val mint = Tokens.primary
    Canvas(modifier) {
        val stroke = 40f
        val inset = stroke
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        drawArc(used, 0f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
        // reclaimable sector rides on top of the used arc (grayed → mint after scan)
        drawArc(
            mint,
            -90f + 360f * (animated - reclaimFraction),
            360f * reclaimFraction,
            false, topLeft, arcSize,
            style = Stroke(stroke, cap = StrokeCap.Butt),
        )
    }
}