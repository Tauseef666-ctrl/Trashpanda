package trashpanda.rules

/**
 * A single junk rule (DESIGN §5.2). The explanation is not decoration — the engine
 * refuses to plan any item whose rule cannot produce its one-line human explanation:
 * **no explanation, no deletion.**
 */
data class Rule(
    val id: String,
    val title: String,
    val explanation: String,
    val risk: Risk,
    val category: CategoryId,
    /** Env-expanded-at-load globs, e.g. %LOCALAPPDATA%\Temp\** */
    val patterns: List<String>,
    val constraints: ScanConstraints,
    val action: RuleAction,
    /** Importance verdict shown to the user: trash | regenerates | review | keep. */
    val verdict: Verdict = Verdict.REVIEW,
    /** Config trees NEVER to touch: .git, .ssh, wallets… */
    val dropWhenPaths: List<String> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "rule id must not be blank" }
        require(title.isNotBlank()) { "rule '$id': title must not be blank" }
        // The literal rule invariant from DESIGN §6:
        require(!explanation.isBlank()) { "rule '$id': no explanation, no deletion" }
        require(patterns.isNotEmpty()) { "rule '$id': at least one pattern required" }
        // Safety invariant: something marked system-critical must never be planable.
        if (verdict == Verdict.KEEP) require(action != RuleAction.RECYCLE) {
            "rule '$id': verdict=keep with action=recycle is a catalog bug"
        }
    }
}
