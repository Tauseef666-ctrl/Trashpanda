@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package trashpanda.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import trashpanda.engine.report.Sizes
import trashpanda.engine.scan.Planner
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
 * The GUI's entire promise, driven interactively at the widget layer and against the
 * REAL Windows shell + REAL Recycle Bin (same discipline as TrashRoundTripIT):
 *
 *   scan → GREEN item arrives pre-checked → tick machinery works → confirm copy says
 *   undo exists → real move lands in the bin → receipt appears → Undo restores
 *   byte-identical.
 *
 * Only the shell clicks are synthetic; every node is clicked through Compose's test
 * DSL exactly like a user would.
 */
class GuiFlowTest {

    /**
     * Sandboxes must be LONG paths — D1's glob literally reads the long Temp form
     * (…/AppData/Local/Temp/anything), and %TEMP% handed down by the shell is the
     * 8.3 short form (TAUSEE~1-of-Users), which never matches it. Same lesson the
     * CLI e2e taught; the app itself always scans long roots.
     */
    private fun sandbox(prefix: String): Path {
        val lad = System.getenv("LOCALAPPDATA")
        val temp = if (lad.isNullOrEmpty()) null else Path.of(lad, "Temp") ?: null
        val base = temp ?: Path.of(System.getProperty("java.io.tmpdir").trimEnd('\\'))
        Files.createDirectories(base)
        return base.resolve("$prefix-${java.util.UUID.randomUUID()}")
    }

    private val work = sandbox("tp-gui-flow")
    private val receipts = sandbox("tp-gui-receipts")

    @AfterTest
    fun cleanup() {
        work.toFile().deleteRecursively()
        receipts.toFile().deleteRecursively()
    }

    @Test
    fun `scan tick confirm moves to the real bin and undo restores byte-identical`() {
        val old = work.resolve("logs").resolve("clean-me.txt")
        Files.createDirectories(old.parent)
        val payload = "gui-flow-payload-v0.2 — reversibility is the whole point".toByteArray()
        Files.write(old, payload)
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(3))))

        val plan = Planner(RuleCatalog.loadBuiltin()).planFromDisk(listOf(work))
        assertTrue(
            plan.items.any { it.path == old },
            "the D1 user-temp rule must catch the sandbox file before the GUI flow can run",
        )

        runComposeUiTest {
            setContent {
                ReportScreen(
                    plan = plan,
                    onBack = {},
                    receiptsDir = receipts,
                    worker = Dispatchers.Unconfined, // engine work runs inline: no wall-clock waits
                )
            }

            // The GREEN item arrives pre-checked.
            assertTrue(Files.exists(old), "sanity: sandbox file present before the run")

            // Tick machinery: un-tick → footer reflects it → re-tick.
            onNodeWithText("clean-me.txt").performClick()
            onNodeWithText("nothing ticked yet").assertExists()
            onNodeWithText("Undo anytime from the receipt.").assertDoesNotExist()
            onNodeWithText("clean-me.txt").performClick()
            onNodeWithText("nothing ticked yet").assertDoesNotExist()

            // Two-step confirm, explicitly saying undo exists (per DESIGN).
            onNodeWithText("Move to Recycle Bin").performClick()
            onNodeWithText("Undo anytime from the receipt.", substring = true).assertExists()
            onNodeWithText("Cancel — nothing moves").assertExists()

            // Cancel path must not move anything.
            onNodeWithText("Cancel — nothing moves").performClick()
            assertTrue(Files.exists(old), "cancel means the file stays put")
            onNodeWithText("Move to Recycle Bin").performClick()
            onNodeWithText("Undo anytime from the receipt.", substring = true).assertExists()
            onNodeWithText("Yes — move 1 item(s)").performClick()

            // The real shell move happened.
            assertTrue(!Files.exists(old), "file must be gone from disk after the confirmed move")

            // Receipt panel is the news of the moment, with undo one click away.
            onNodeWithText("Undo this receipt — put all 1 back", substring = true).assertExists()
            onNodeWithText("in bin").assertExists()

            // Undo through the GUI.
            onNodeWithText("Undo this receipt — put all 1 back", substring = true).performClick()
            onNodeWithText("restored").assertExists()
            onNodeWithText("undone").assertExists()

            // And the file is home, byte-identical.
            assertTrue(Files.exists(old), "undo must put the file back at its original path")
            assertEquals(payload.toList(), old.readBytes().toList(), "restored file must be byte-identical")
        }
    }

    @Test
    fun `sizes come back in plain human units`() {
        runComposeUiTest {
            val old = work.resolve("logs2").resolve("small.txt")
            Files.createDirectories(old.parent)
            Files.write(old, ByteArray(1_500_000))
            Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(2))))
            val plan = Planner(RuleCatalog.loadBuiltin()).planFromDisk(listOf(work))

            setContent {
                ReportScreen(plan = plan, onBack = {}, receiptsDir = receipts, worker = Dispatchers.Unconfined)
            }
            // "1.5 MB" shows exactly twice: once in the rule group's total, once on the item row.
            onAllNodesWithText("1.5 MB").assertCountEquals(2)
        }
    }
}
