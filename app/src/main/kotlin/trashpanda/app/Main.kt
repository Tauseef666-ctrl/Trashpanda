package trashpanda.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Planner
import trashpanda.rules.RuleCatalog
import trashpanda.rules.Risk
import java.nio.file.Files
import java.nio.file.Path

/**
 * trashpanda v0.1 — Home "Drive health" (DESIGN §4.1 screen 001), faithful to preview/home.png.
 * The demo scan button runs the REAL engine (read-only) over %LOCALAPPDATA%\Temp.
 */
/** Loads the bundled raccoon icon (falls back to default if missing). */
private fun windowIcon(): java.awt.Image? = runCatching {
    val url = Tokens::class.java.getResource("/icon.png") ?: return@runCatching null
    javax.swing.ImageIcon(url).image
}.getOrNull()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "trashpanda — eats your digital garbage. leaves your files alone.",
    ) {
        LaunchedEffect(Unit) {
            windowIcon()?.let { window.iconImage = it }
        }
        TrashPandaTheme {
            HomeScreen()
        }
    }
}

// Last-scan snapshot from the proof set (09 Nov 2026). Replaced by real scan cache in P2.
private data class Snapshot(
    val diskTotalGb: Double = 108.0,
    val diskFreeGb: Double = 24.0,
    val reclaimableBytes: Long = 18_600_000_000L,
    val junkItems: Int = 1204,
    val autoCheckPct: Int = 92,
    val needsTickPct: Int = 8,
)

private data class JunkRow(
    val title: String,
    val path: String,
    val sizeLabel: String,
    val risk: Risk,
)

private val topJunk = listOf(
    JunkRow("AI agents — session databases", "%USERPROFILE%\\.local\\share\\opencode\\opencode.db", "8.1 GB", Risk.AMBER),
    JunkRow("Windows temp", "%LOCALAPPDATA%\\Temp (older than 24 h)", "7.9 GB", Risk.GREEN),
    JunkRow("Agent-bundled browsers", "%LOCALAPPDATA%\\hermes\\tools\\chromium-*", "5.4 GB", Risk.AMBER),
    JunkRow("Build outputs", "\\u2026\\NewPipe\\desktopApp\\build", "3.0 GB", Risk.GREEN),
    JunkRow("Gradle caches", "%USERPROFILE%\\.gradle\\caches", "1.9 GB", Risk.GREEN),
    JunkRow("npm tools installed \u00d73", "%APPDATA%\\npm\\node_modules (opencode, claude, kilo\u2026)", "0.8 GB", Risk.AMBER),
)

@Composable
fun HomeScreen() {
    val snapshot = remember { Snapshot() }
    var demoRunning by remember { mutableStateOf(false) }
    var demoResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Header
        Column {
            Text("Drive health", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
            Text(
                "C:\\ — last scan 09 Nov, 21:14 · Bin found ${Sizes.human(snapshot.reclaimableBytes)} of garbage. " +
                    "Nothing has been touched yet — scanning never changes the disk.",
                fontSize = 13.sp,
                color = Tokens.textDim,
            )
        }

        // Hero: donut + stats
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
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                StorageDonut(
                    usedFraction = ((snapshot.diskTotalGb - snapshot.diskFreeGb) / snapshot.diskTotalGb).toFloat(),
                    reclaimFraction = 0.172f,
                    modifier = Modifier.size(190.dp),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("24 GB", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("free of 108 GB", fontSize = 12.sp, color = Tokens.textDim)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Reclaimable now", Sizes.human(snapshot.reclaimableBytes), Tokens.primary, Modifier.weight(1f))
                    StatCard("Junk items", snapshot.junkItems.toString(), Tokens.text, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Auto-checkable", "${snapshot.autoCheckPct}%", Tokens.riskSafe, Modifier.weight(1f))
                    StatCard("Needs your tick", "${snapshot.needsTickPct}%", Tokens.riskReview, Modifier.weight(1f))
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
                Text("tap a category to jump to its report section", fontSize = 12.sp, color = Tokens.textDim)
                topJunk.forEach { row -> JunkRowView(row) }
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

                // The CTA runs the REAL read-only engine over %LOCALAPPDATA%\Temp.
                Button(
                    onClick = {
                        if (!demoRunning) {
                            demoRunning = true
                            demoResult = null
                            CoroutineScope(Dispatchers.Main).launch {
                                demoResult = withContext(Dispatchers.IO) { runTempDemoScan() }
                                demoRunning = false
                            }
                        }
                    },
                    enabled = !demoRunning,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Tokens.primary,
                        contentColor = Color(0xFF06281C),
                        disabledContainerColor = Tokens.border,
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(
                        if (demoRunning) "Bin is sweeping…" else "Scan now (demo: Temp folder, read-only)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }

                demoResult?.let { result ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1420))
                            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text("Dry-run receipt (read-only)", fontSize = 12.sp, color = Tokens.primary, fontWeight = FontWeight.SemiBold)
                        Text(
                            result,
                            fontSize = 12.sp,
                            color = Tokens.textDim,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        Text(
            "trashpanda v0.1 · P0 scaffold · full scan + report + execute lands next (PLAN P1-P2)",
            fontSize = 11.sp,
            color = Tokens.textDim,
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0B0F14).copy(alpha = 0.5f))
            .border(1.dp, Tokens.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(label.uppercase(), fontSize = 11.sp, color = Tokens.textDim, letterSpacing = 0.8.sp)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = valueColor, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun JunkRowView(row: JunkRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0F14).copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Tokens.text)
            Text(row.path, fontSize = 11.sp, color = Tokens.textDim, fontFamily = FontFamily.Monospace)
        }
        Text(row.sizeLabel, fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Monospace)
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
    Canvas(modifier) {
        val stroke = 40f
        val inset = stroke
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        val track = Color(0xFF151D2B)
        val used = Color(0xFF3B4A61)
        val mint = Tokens.primary

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

/** Real engine dry-run over %LOCALAPPDATA%\Temp — read-only, prints a receipt string. */
private fun runTempDemoScan(): String {
    val temp = Path.of(System.getenv("LOCALAPPDATA"), "Temp")
    if (!Files.isDirectory(temp)) return "Temp not found — nothing to sweep."
    val catalog = RuleCatalog.loadBuiltin()
    val started = System.currentTimeMillis()
    val plan = Planner(catalog).planFromDisk(listOf(temp))
    val ms = System.currentTimeMillis() - started
    return buildString {
        appendLine("scanned: $temp")
        appendLine("reclaimable: ${Sizes.human(plan.recyclableBytes)} across ${plan.recycleCount} item(s)")
        appendLine("report-only: ${plan.items.count { it.action == trashpanda.rules.RuleAction.REPORT_ONLY }} · " +
            "junctions mirrored: ${plan.reparseMirrors} · denied (skipped, not killed): ${plan.deniedDirs}")
        appendLine("took ${ms} ms · read-only · nothing was deleted")
    }
}
