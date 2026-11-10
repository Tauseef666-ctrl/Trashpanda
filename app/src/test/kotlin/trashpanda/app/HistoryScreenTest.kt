@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package trashpanda.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import trashpanda.engine.execute.Executor
import trashpanda.engine.execute.ReceiptStore
import trashpanda.engine.execute.RecycleOut
import trashpanda.engine.execute.RecyclePort
import trashpanda.engine.scan.Planner
import trashpanda.platform.winapi.ShellOp
import trashpanda.rules.RuleCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The History screen's promise, against the REAL Windows shell + REAL Recycle Bin
 * (same discipline as GuiFlowTest):
 *
 *   a real cleanup happens first (Executor → bin + receipt) → History lists the
 *   receipt with an "in bin" chip → the user clicks Undo → the file is back home,
 *   byte-identical, and the receipt reads "undone".
 *
 * Only the clicks are synthetic; everything else is the same engine path the CLI uses.
 */
class HistoryScreenTest {

    /**
     * Long-path sandbox — same lesson as GuiFlowTest: %TEMP% handed down by the shell
     * is the 8.3 short form and never matches D1's long Temp glob.
     */
    private fun sandbox(prefix: String): Path {
        val lad = System.getenv("LOCALAPPDATA")
        val temp = if (lad.isNullOrEmpty()) null else Path.of(lad, "Temp") ?: null
        val base = temp ?: Path.of(System.getProperty("java.io.tmpdir").trimEnd('\\'))
        Files.createDirectories(base)
        return base.resolve("$prefix-${java.util.UUID.randomUUID()}")
    }

    private val work = sandbox("tp-hist-work")
    private val receipts = sandbox("tp-hist-receipts")

    @AfterTest
    fun cleanup() {
        work.toFile().deleteRecursively()
        receipts.toFile().deleteRecursively()
    }

    @Test
    fun `history lists the receipt of a real cleanup and one-click undo restores byte-identical`() {
        val old = work.resolve("logs").resolve("history-me.txt")
        Files.createDirectories(old.parent)
        val payload = "history-screen-payload — every clean must be undoable".toByteArray()
        Files.write(old, payload)
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(3))))

        val plan = Planner(RuleCatalog.loadBuiltin()).planFromDisk(listOf(work))
        assertTrue(
            plan.items.any { it.path == old },
            "the sandbox file must match a cleanable rule before the flow can run",
        )

        // Act 1 — the real cleanup that produces the receipt (exact same path as ReportScreen).
        val store = ReceiptStore(receipts)
        val port: RecyclePort = { p -> ShellOp.recycleToBin(p).let { RecycleOut(it.code, it.aborted) } }
        val receipt = Executor(port, store).execute(plan.items)
        assertTrue(!Files.exists(old), "setup: the confirmed move must have taken the file to the bin")
        assertEquals(listOf(1), listOf(receipt.recycled.size), "setup: one item into the bin")

        // Act 2 — History lists it; the user clicks Undo.
        runComposeUiTest {
            setContent {
                HistoryScreen(
                    onBack = {},
                    worker = Dispatchers.Unconfined, // engine work runs inline: no wall-clock waits
                    receiptsDir = receipts,
                )
            }

            onNodeWithText("in bin", substring = true).assertExists()
            assertTrue(
                store.list().any { it.id == receipt.id },
                "setup sanity: the receipt file must exist under the receipts dir",
            )

            // One click puts everything back (Unconfined: the engine ran inline).
            onNodeWithText("Undo").performClick()
            onNodeWithText("undone", substring = true).assertExists()

            // The file is home, byte-identical — History's entire reason to exist.
            assertTrue(Files.exists(old), "undo must put the file back at its original path")
            assertEquals(payload.toList(), old.readBytes().toList(), "restored file must be byte-identical")
        }
    }
}
