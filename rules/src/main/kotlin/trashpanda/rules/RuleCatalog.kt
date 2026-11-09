package trashpanda.rules

import com.akuleshov7.ktoml.Toml
import kotlinx.serialization.decodeFromString

/**
 * Loads and validates the built-in rule database from the classpath
 * (rules/src/main/resources/rules/builtin-v0.1.toml).
 *
 * Validation is fail-fast: a rule that cannot produce a human explanation, or that
 * tries to RECYCLE something on the never-touch list, breaks the whole catalog —
 * a bug here is a PR-blocker, not a warning (DESIGN §8.7).
 */
object RuleCatalog {

    const val RESOURCE = "/rules/builtin-v0.1.toml"

    /** Global config-kill protection (DESIGN §5.2 dropWhenPaths + §8.3). */
    val GLOBAL_NEVER_TOUCH: List<String> = listOf(
        ".git", ".ssh", "id_rsa", "id_ed25519", ".gnupg", "wallet", "keystore",
        "Credentials", "Cookies", "Passwords", "Login Data", "Local Storage", "IndexedDB",
        "workspaceStorage", "User Volume", "pagefile.sys", "hiberfil.sys", "swapfile.sys",
    )

    fun loadBuiltin(): LoadedCatalog {
        val text = RuleCatalog::class.java.getResourceAsStream(RESOURCE)?.use { stream ->
            stream.readBytes().decodeToString()
        } ?: throw IllegalStateException("built-in rule db missing: $RESOURCE")

        return parse(text)
    }

    fun parse(text: String): LoadedCatalog {
        val dto = Toml.decodeFromString<RuleFileDto>(text)
        require(dto.schema == 1) { "rule db schema ${dto.schema} not supported (want 1)" }

        val neverTouch = dto.neverTouch + GLOBAL_NEVER_TOUCH
        val rules = dto.rule.map { r ->
            Rule(
                id = r.id,
                title = r.title,
                explanation = r.explanation.trim(),
                risk = Risk.parse(r.risk),
                category = CategoryId.parse(r.category),
                patterns = r.patterns,
                constraints = ScanConstraints(r.minAgeDays, r.minSizeMB, r.maxDepth),
                action = RuleAction.parse(r.action),
                verdict = Verdict.parse(r.verdict),
                dropWhenPaths = r.dropWhenPaths + neverTouch,
            )
        }

        val ids = rules.map { it.id }
        require(ids.size == ids.distinct().size) { "duplicate rule ids in catalog" }

        // A rule that plans a RECYCLE against a never-touch token is a catalog bug.
        rules.filter { it.action == RuleAction.RECYCLE }.forEach { rule ->
            rule.patterns.forEach { p ->
                neverTouch.forEach { n ->
                    require(!(p.contains("\\$n\\") || p.endsWith("\\$n") || p == n)) {
                        "rule '${rule.id}' recycles a protected path ('$n'): $p"
                    }
                }
            }
        }

        return LoadedCatalog(rules, neverTouch)
    }
}

data class LoadedCatalog(
    val rules: List<Rule>,
    val neverTouch: List<String>,
)
