package trashpanda.app

import trashpanda.engine.scan.Planner
import trashpanda.rules.CategoryId
import trashpanda.rules.Rule
import trashpanda.rules.RuleAction
import trashpanda.rules.ScanConstraints
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * User exclusions ("never touch"): whole-component, Windows-flavored matching and
 * the honest plan filter. Pure-function tests — no disk, no GUI.
 */
class ExclusionsTest {

    private fun rule(id: String) = Rule(
        id = id,
        title = "test rule $id",
        explanation = "explains itself, per invariant",
        risk = trashpanda.rules.Risk.AMBER,
        category = CategoryId.WINDOWS,
        patterns = listOf("**/*"),
        constraints = ScanConstraints(),
        action = RuleAction.RECYCLE,
        verdict = trashpanda.rules.Verdict.TRASH,
    )

    private fun item(path: String, bytes: Long) = Planner.Item(
        rule = rule("X1"),
        path = Path.of(path),
        sizeBytes = bytes,
        why = "explains itself",
        action = RuleAction.RECYCLE,
    )

    @Test
    fun `whole-component prefix match wins`() {
        val ex = listOf("D:\\My Games")
        assertTrue(isExcludedPath(Path.of("D:\\My Games\\save.dat"), ex))
        assertTrue(isExcludedPath(Path.of("D:\\My Games\\saves\\deep\\x.tmp"), ex))
        // sibling that merely shares a text prefix must SURVIVE
        assertFalse(isExcludedPath(Path.of("D:\\My Games2\\save.dat"), ex))
        // partial component must not match
        assertFalse(isExcludedPath(Path.of("D:\\My Gam"), ex))
    }

    @Test
    fun `matching is case and separator insensitive like Windows`() {
        val ex = listOf("d:/my games")
        assertTrue(isExcludedPath(Path.of("D:\\MY GAMES\\a.txt"), ex))
        assertTrue(isExcludedPath(Path.of("d:\\my games\\b.txt"), ex))
        assertFalse(isExcludedPath(Path.of("C:\\My Games\\other.txt"), ex))
    }

    @Test
    fun `exact file path excludes just that file`() {
        val ex = listOf("C:\\Users\\me\\keep.txt")
        assertTrue(isExcludedPath(Path.of("C:\\Users\\me\\keep.txt"), ex))
        assertFalse(isExcludedPath(Path.of("C:\\Users\\me\\keep2.txt"), ex))
    }

    @Test
    fun `blank or empty exclusions exclude nothing`() {
        assertFalse(isExcludedPath(Path.of("C:\\Temp\\x.txt"), listOf("", "  ")))
        assertFalse(isExcludedPath(Path.of("C:\\Temp\\x.txt"), emptyList()))
    }

    @Test
    fun `filter drops excluded items and keeps counters coherent`() {
        val keep = item("D:\\somewhere\\junk.txt", 10)
        val drop = item("D:\\My Games\\save.dat", 5000)
        val plan = Planner.Plan(
            items = listOf(keep, drop),
            deniedDirs = 0,
            reparseMirrors = 0,
            scannedRoots = listOf(Path.of("D:\\")),
        )
        val filtered = applyExclusions(plan, listOf("D:\\My Games"))
        assertEquals(listOf(keep), filtered.items, "the excluded subtree item must vanish")
        assertEquals(10, filtered.recyclableBytes, "counters must track the surviving items only")
    }
}
