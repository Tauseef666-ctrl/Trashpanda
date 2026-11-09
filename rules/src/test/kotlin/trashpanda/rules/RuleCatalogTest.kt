package trashpanda.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class RuleCatalogTest {

    private val catalog = RuleCatalog.loadBuiltin()

    @Test
    fun `builtin catalog loads with the v0_1 rule set`() {
        val ids = catalog.rules.map { it.id }
        assertTrue(ids.size >= 30, "expected the full v0.1 catalog, got ${ids.size} rules")
        assertTrue(
            ids.containsAll(
                listOf("A1", "A5", "A7", "B2", "B8", "C1", "D1", "D5", "D7", "D11", "E1", "F1"),
            ),
        )
        assertEquals(ids.size, ids.distinct().size, "rule ids must be unique")
    }

    @Test
    fun `every rule carries its human explanation`() {
        // The literal invariant from DESIGN §6: no explanation, no deletion.
        catalog.rules.forEach { rule ->
            assertTrue(rule.explanation.isNotBlank(), "rule ${rule.id} lost its explanation")
        }
    }

    @Test
    fun `importance verdicts stay on the designed side of safety`() {
        // "is it important? is it trash?" — user requirement 09 Nov.
        assertEquals(Verdict.TRASH, catalog.rules.first { it.id == "D1" }.verdict)
        assertEquals(Verdict.REGENERATES, catalog.rules.first { it.id == "A5" }.verdict)
        assertEquals(Verdict.REVIEW, catalog.rules.first { it.id == "A1" }.verdict)
        assertEquals(Verdict.KEEP, catalog.rules.first { it.id == "D5" }.verdict)
        // Your data is never "trash":
        assertEquals(Verdict.REVIEW, catalog.rules.first { it.id == "F1" }.verdict)
        assertEquals(Verdict.REVIEW, catalog.rules.first { it.id == "A3" }.verdict)
    }

    @Test
    fun `keep-verdict rules are never recyclable`() {
        catalog.rules.filter { it.verdict == Verdict.KEEP }.forEach { rule ->
            assertTrue(
                rule.action != RuleAction.RECYCLE,
                "rule ${rule.id}: keep-verdict must not be recyclable",
            )
        }
    }

    @Test
    fun `forbidden system stores are NEVER actions`() {
        val d5 = catalog.rules.first { it.id == "D5" }
        assertEquals(RuleAction.NEVER, d5.action)
        assertEquals(Risk.RED, d5.risk)
    }

    @Test
    fun `flagship agent rules keep their designed risks`() {
        assertEquals(Risk.AMBER, catalog.rules.first { it.id == "A1" }.risk)
        assertEquals(Risk.GREEN, catalog.rules.first { it.id == "A5" }.risk)
        assertEquals(Risk.RED, catalog.rules.first { it.id == "A7" }.risk)
    }

    @Test
    fun `no recyclable rule pattern may touch a protected token`() {
        // Red line: if this test can be made to fail by editing TOML, the catalog
        // validation must have thrown first. Belt and suspenders.
        val protected = listOf(".git", ".ssh", "id_rsa", "Cookies", "workspaceStorage")
        catalog.rules.filter { it.action == RuleAction.RECYCLE }.forEach { rule ->
            val joined = rule.patterns.joinToString("|") + "|" + rule.dropWhenPaths.joinToString("|")
            protected.forEach { token ->
                if (token in rule.dropWhenPaths) return@forEach
                assertTrue(
                    !rule.patterns.any { it.substringAfterLast('/').equals(token, ignoreCase = true) },
                    "rule ${rule.id} recycles a protected token: $token",
                )
            }
            assertTrue(joined.isNotBlank())
        }
    }

    @Test
    fun `a rule without explanation fails to parse`() {
        val bad = """
            schema = 1
            [[rule]]
            id = "X1"
            title = "broken"
            category = "windows"
            patterns = ["C:/nope/**"]
        """.trimIndent()
        try {
            RuleCatalog.parse(bad)
            fail("catalog accepted a rule with no explanation")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("explanation"), "wrong failure: ${e.message}")
        }
    }

    @Test
    fun `unknown risk strings fail loudly`() {
        val bad = """
            schema = 1
            [[rule]]
            id = "X2"
            title = "broken"
            explanation = "x"
            risk = "cyan"
            category = "windows"
            patterns = ["C:/nope/**"]
        """.trimIndent()
        try {
            RuleCatalog.parse(bad)
            fail("catalog accepted an unknown risk")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("risk"), "wrong failure: ${e.message}")
        }
    }
}
