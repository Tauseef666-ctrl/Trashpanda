package trashpanda.engine.execute

import kotlinx.serialization.json.Json
import trashpanda.engine.scan.Planner
import trashpanda.rules.CategoryId
import trashpanda.rules.Risk
import trashpanda.rules.Rule
import trashpanda.rules.RuleAction
import trashpanda.rules.ScanConstraints
import trashpanda.rules.Verdict
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The executor is the only disk-mutating phase, so its refusal invariants get tests
 * of their own: report-only items never reach it, and the Recycle Bin itself can
 * never be an execution target.
 */
class ExecutorTest {

    private val tempDir = Files.createTempDirectory("tp-executor-test")

    private val store = ReceiptStore(tempDir)

    @AfterTest
    fun cleanup() { tempDir.toFile().deleteRecursively() }

    private fun rule(id: String, action: RuleAction, verdict: Verdict = Verdict.TRASH) = Rule(
        id = id,
        title = "test rule $id",
        explanation = "explains itself, per invariant",
        risk = Risk.GREEN,
        category = CategoryId.WINDOWS,
        patterns = listOf("%TEMP%/x/**"),
        constraints = ScanConstraints(),
        action = action,
        verdict = verdict,
    )

    private fun item(path: String, action: RuleAction, verdict: Verdict = Verdict.TRASH) = Planner.Item(
        rule = rule("D1-$action", action, verdict),
        path = Path.of(path),
        sizeBytes = 42,
        why = "explains itself",
        action = action,
    )

    @Test
    fun `refuses to run on anything that is not a recyclable rule`() {
        listOf(RuleAction.REPORT_ONLY, RuleAction.NEVER).forEach { illegal ->
            try {
                Executor(RecyclePort { fail("shell must never be called") })
                    .execute(listOf(item("C:/whatever/leaf", illegal)))
                fail("executor accepted action=$illegal")
            } catch (expected: IllegalArgumentException) {
                assertTrue(
                    expected.message!!.contains(illegal.name.lowercase()),
                    "refusal must explain itself: ${expected.message}",
                )
            }
        }
    }

    @Test
    fun `refuses the recycle bin and system repair stores as targets`() {
        val port = RecordedPort()
        val executor = Executor(port)
        val receipt = executor.execute(
            listOf(
                item("C:/\$Recycle.Bin/S-1-5-21-1/x.txt", RuleAction.RECYCLE),
                item("C:/System Volume Information/b", RuleAction.RECYCLE),
                item("C:/Windows/WinSxS/nope", RuleAction.RECYCLE),
                item("C:/Windows/Installer/nope", RuleAction.RECYCLE),
                item("C:/Windows/System32/DriverStore/nope", RuleAction.RECYCLE),
            ),
        )
        assertEquals(0, port.calls.size, "protected scopes must never touch the shell")
        assertEquals(5, receipt.failed.size, "all refused with an explanation, not silently")
        receipt.items.forEach { assertTrue(it.error!!.contains("protected"), it.toString()) }
    }

    @Test
    fun `successful moves keep freed accounting and failed ones leave the file alone`() {
        val one = tempDir.resolve("one.txt")
        val two = tempDir.resolve("two.txt")
        Files.write(one, "1".toByteArray())
        Files.write(two, "2".toByteArray())
        val port = RecordedPort(okForAll = true)
        val receipt = Executor(port, store).execute(
            listOf(item(one.toString(), RuleAction.RECYCLE), item(two.toString(), RuleAction.RECYCLE)),
        )
        assertEquals(2, receipt.recycled.size)
        assertEquals(84, receipt.freedBytes)

        val json = store.dir.resolve("${receipt.id}.json").readText()
        assertTrue(Json.parseToJsonElement(json).toString().contains("recycled"))
    }

    @Test
    fun `failed moves are receipted as failures - skipped, not killed`() {
        val locked = tempDir.resolve("locked.txt")
        Files.write(locked, "in-use".toByteArray())
        val port = RecordedPort(okForAll = false)
        val receipt = Executor(port, store).execute(listOf(item(locked.toString(), RuleAction.RECYCLE)))
        assertEquals(1, receipt.failed.size)
        assertTrue(receipt.failed.first().error!!.contains("code"))
        assertEquals("failed", receipt.items.first().status)
    }

    @Test
    fun `vanished paths are receipted as failures with a plain-language reason`() {
        val receipt = Executor(RecordedPort()).execute(listOf(item("Q:/gone/fantasy.dll", RuleAction.RECYCLE)))
        assertEquals(1, receipt.failed.size)
        assertTrue(receipt.failed.first().error!!.contains("vanished"), receipt.failed.first().error)
    }

    /** Records every shell call so tests can assert "the disk was never touched". */
    private class RecordedPort(okForAll: Boolean = true) : RecyclePort {
        val calls = mutableListOf<String>()
        private val ok = okForAll
        override fun recycle(path: String): RecycleOut {
            calls += path
            return RecycleOut(if (ok) 0 else 124)
        }
    }
}
