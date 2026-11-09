package trashpanda.engine.scan

import trashpanda.rules.LoadedCatalog
import trashpanda.rules.Rule
import trashpanda.rules.RuleAction
import trashpanda.rules.Risk
import trashpanda.rules.CategoryId
import trashpanda.rules.ScanConstraints
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerTest {

    private lateinit var root: Path

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("tp-fixture")
    }

    @AfterTest
    fun teardown() {
        root.toFile().deleteRecursively()
    }

    private fun rule(
        id: String = "T1",
        pattern: String,
        risk: Risk = Risk.GREEN,
        action: RuleAction = RuleAction.RECYCLE,
        minSizeMB: Long = 0,
        minAgeDays: Long = 0,
        dropWhenPaths: List<String> = emptyList(),
    ) = Rule(
        id = id,
        title = "test $id",
        explanation = "test explanation for $id",
        risk = risk,
        category = CategoryId.WINDOWS,
        patterns = listOf(pattern),
        constraints = ScanConstraints(minAgeDays = minAgeDays, minSizeMB = minSizeMB),
        action = action,
        dropWhenPaths = dropWhenPaths,
    )

    private fun catalog(vararg rules: Rule) = LoadedCatalog(rules.toList(), emptyList())

    @Test
    fun `plans nothing - scan is read-only`() {
        val junk = root.resolve("Temp/junk.txt")
        junk.parent.toFile().mkdirs()
        junk.writeBytes(ByteArray(100))

        val before = junk.toFile().length()
        Planner(catalog(rule(pattern = "${root.toString().replace('\\', '/')}/Temp/**"))).planFromDisk(listOf(root))
        val after = junk.toFile().length()

        assertEquals(before, after)
        assertTrue(junk.toFile().exists(), "scan must never delete (rule 1)")
    }

    @Test
    fun `junction is mirrored and never descended`() {
        // Real target with junk inside
        val target = root.resolve("real-target")
        target.toFile().mkdirs()
        val secret = target.resolve("precious.txt")
        secret.writeBytes(ByteArray(10))

        // Junction planted as a trap (GitHub windows runners allow junctions without admin)
        val junction = root.resolve("trap-junction")
        val proc = ProcessBuilder("cmd", "/c", "mklink", "/J", junction.toString(), target.toString())
            .redirectErrorStream(true).start()
        val ok = proc.waitFor() == 0 && junction.toFile().exists()
        if (!ok) {
            println("junction creation unavailable on this host — skipping trap test")
            return
        }

        // Walk the parent; the junction must be recorded as a mirror, not descended.
        val summary = Scanner().walk(root, maxDepth = 4)
        val trap = summary.entries.filter { it.path.endsWith("trap-junction") }
        assertTrue(trap.any { it.isReparse }, "junction must be flagged as reparse")
        assertTrue(summary.entries.none { it.path.toString().contains("trap-junction\\") || it.path.toString().contains("trap-junction/") },
            "junction must never be descended (rule 5)")
        junction.toFile().delete()
    }

    @Test
    fun `minSizeMB gates small files out`() {
        val small = root.resolve("small.bin")
        small.writeBytes(ByteArray(50))
        val big = root.resolve("big.bin")
        big.writeBytes(ByteArray(2_000_000))

        val plan = Planner(catalog(rule(pattern = "**/*.bin", minSizeMB = 1))).planFromDisk(listOf(root))
        val planned = plan.items.map { it.path }
        assertTrue(big in planned, "big file should be planned")
        assertTrue(small !in planned, "small file must be gated out")
    }

    @Test
    fun `protected token drops the match`() {
        val git = root.resolve("repo/.git/config")
        git.parent.toFile().mkdirs()
        git.writeBytes(ByteArray(10))

        val plan = Planner(catalog(rule(pattern = "**/config", dropWhenPaths = listOf(".git"))))
            .planFromDisk(listOf(root))
        assertTrue(plan.items.isEmpty(), ".git config must never match (config-kill protection)")
    }

    @Test
    fun `never-action rules produce no items`() {
        val f = root.resolve("WinSxS-thing")
        f.writeBytes(ByteArray(10))
        val plan = Planner(catalog(rule(pattern = "**/WinSxS*", action = RuleAction.NEVER))).planFromDisk(listOf(root))
        assertTrue(plan.items.isEmpty())
    }
}
