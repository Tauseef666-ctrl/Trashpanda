package trashpanda.engine.scan

import trashpanda.rules.RuleCatalog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scan progress hook (user-friendliness pass): a UI must be able to say what the
 * read-only walker is doing. The hook is throttled, fires on the walking thread, and
 * must never disturb the plan (same items, same counters) or write anything.
 */
class ScanProgressTest {

    private fun sandbox(prefix: String): Path {
        val lad = System.getenv("LOCALAPPDATA")
        val temp = if (lad.isNullOrEmpty()) null else Path.of(lad, "Temp") ?: null
        val base = temp ?: Path.of(System.getProperty("java.io.tmpdir").trimEnd('\\'))
        Files.createDirectories(base)
        return base.resolve("$prefix-${java.util.UUID.randomUUID()}")
    }

    @Test
    fun `progress ticks fire during a real walk and never change the plan`() {
        val root = sandbox("tp-progress")
        try {
            // 700 files across nested dirs — enough for several 256-entry ticks.
            val deep = root.resolve("a").resolve("b").resolve("c")
            Files.createDirectories(deep)
            for (n in 1..700) Files.write(root.resolve("a").resolve("f$n.txt"), ByteArray(10))
            for (n in 1..700) Files.write(root.resolve("a").resolve("b").resolve("g$n.txt"), ByteArray(10))
            for (n in 1..700) Files.write(deep.resolve("h$n.txt"), ByteArray(10))

            val planner = Planner(RuleCatalog.loadBuiltin())
            val quiet = planner.planFromDisk(listOf(root), maxDepth = 12)

            data class Tick(val count: Long, val path: Path)
            val ticks = mutableListOf<Tick>()
            val verbose = planner.planFromDisk(listOf(root), maxDepth = 12, onTick = { n, p ->
                synchronized(ticks) { ticks.add(Tick(n, p)) }
            })

            assertEquals(
                quiet.items.map { it.path },
                verbose.items.map { it.path },
                "the hook must not change which items are matched",
            )
            assertEquals(quiet.items.sumOf { it.sizeBytes }, verbose.items.sumOf { it.sizeBytes })
            assertEquals(quiet.deniedDirs, verbose.deniedDirs)

            synchronized(ticks) {
                assertTrue(ticks.isNotEmpty(), "a 2,100-file walk must produce at least one tick")
                assertEquals(
                    ticks.map { it.count },
                    ticks.map { it.count }.sorted(),
                    "tick counts must increase monotonically",
                )
                assertTrue(ticks.all { it.count % 256L == 0L }, "ticks are throttled to the 256-entry grid")
                assertTrue(
                    ticks.all { it.path.startsWith(root) },
                    "every tick path must live inside the scanned root: ${ticks.take(3)}",
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `no hook means no behavior change at all`() {
        val root = sandbox("tp-quiet")
        try {
            Files.createDirectories(root.resolve("x"))
            repeat(300) { Files.write(root.resolve("x").resolve("n$it.txt"), ByteArray(4)) }
            val planner = Planner(RuleCatalog.loadBuiltin())
            val a = planner.planFromDisk(listOf(root), maxDepth = 12)
            val b = planner.planFromDisk(listOf(root), maxDepth = 12, onTick = null)
            assertEquals(a.items.map { it.path }, b.items.map { it.path })
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
