package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * Checks each [HostContract] against a host: the one that is running, or a
 * recorded description of a host build ([HostTypes]).
 *
 * Every lookup walks the class and its superclasses, declared members included,
 * because that is how the features themselves look members up: a member the
 * host declares privately on a base class is one a feature can reach.
 */
class ContractAnalyzer(
    private val types: HostTypes,
    /** A signature as this build renamed it ([HostSymbols]); found that way, a contract is met by fallback. */
    private val relocate: (Signature) -> Signature = { it },
) {

    fun analyze(contracts: List<HostContract>): ContractReport =
        ContractReport(contracts.map(::check))

    /** Whether the host has [signature] under the name the sources use. */
    fun has(signature: Signature): Boolean = find(signature)

    private fun check(contract: HostContract): ContractResult {
        if (find(contract.signature)) return ContractResult(contract, ContractOutcome.RESOLVED)

        (listOf(relocate(contract.signature)) + contract.alternatives).firstOrNull { it != contract.signature && find(it) }?.let { alternative ->
            return ContractResult(contract, ContractOutcome.FALLBACK, alternative)
        }

        val outcome = if (types.find(contract.signature.owner) == null) {
            ContractOutcome.MISSING_CLASS
        } else {
            ContractOutcome.MISSING_MEMBER
        }
        return ContractResult(contract, outcome)
    }

    private fun find(signature: Signature): Boolean {
        val type = types.find(signature.owner) ?: return false
        val member = signature.member ?: return true

        return runCatching { type.hierarchy().any { declares(it, member) } }.getOrDefault(false)
    }

    private fun declares(type: HostType, member: Member): Boolean = when (member) {
        is Member.Field -> member.name in type.fields
        is Member.Method -> type.methods.any { method ->
            method.name == member.name && (member.parameters == null || method.parameters == member.parameters)
        }
    }
}

enum class ContractOutcome { RESOLVED, FALLBACK, MISSING_MEMBER, MISSING_CLASS }

data class ContractResult(
    val contract: HostContract,
    val outcome: ContractOutcome,
    /** The alternative that met the contract, for [ContractOutcome.FALLBACK]. */
    val alternative: Signature? = null,
) {
    val isMet: Boolean get() = outcome == ContractOutcome.RESOLVED || outcome == ContractOutcome.FALLBACK

    /** ✓ met, ⚠ met by an alternative or optional absence, ✗ required and missing. */
    val mark: String
        get() = when (outcome) {
            ContractOutcome.RESOLVED -> PASS
            ContractOutcome.FALLBACK -> WARN
            else -> if (contract.isRequired) FAIL else WARN
        }

    /** What a person needs to know beyond the mark, or nothing when it resolved. */
    val explanation: List<String>
        get() = when (outcome) {
            ContractOutcome.RESOLVED -> emptyList()
            ContractOutcome.FALLBACK -> listOf("Missing", "Alternative found:", alternative?.describe().orEmpty())
            ContractOutcome.MISSING_MEMBER -> listOf(if (contract.isRequired) "Removed" else "Removed (optional)")
            ContractOutcome.MISSING_CLASS -> listOf(if (contract.isRequired) "Class removed" else "Class removed (optional)")
        }

    companion object {
        const val PASS = "✓"
        const val WARN = "⚠"
        const val FAIL = "✗"
    }
}

/** What the analyzer found, and what that means for each feature. */
class ContractReport(val results: List<ContractResult>) {

    val total: Int get() = results.size
    val resolved: Int get() = results.count(ContractResult::isMet)
    val fallbacks: Int get() = results.count { it.outcome == ContractOutcome.FALLBACK }
    val requiredTotal: Int get() = results.count { it.contract.isRequired }
    val requiredResolved: Int get() = results.count { it.contract.isRequired && it.isMet }
    val missingRequired: Int get() = requiredTotal - requiredResolved
    val optionalMissing: Int get() = results.count { !it.contract.isRequired && !it.isMet }

    /** A capability the current host implements itself instead of exposing the legacy hook path. */
    fun isHostManaged(feature: Feature): Boolean =
        feature.hostManagedWhenAbsent && results.none { feature in it.contract.features }

    /** Incompatible when any member the feature requires is missing. */
    fun isCompatible(feature: Feature): Boolean =
        results.none { feature in it.contract.features && it.contract.isRequired && !it.isMet }

    /** The report as text, for a log or a bug report; [hostVersion] names the build of the host it checked. */
    fun format(host: Host, hostVersion: String): String = buildString {
        appendLine("${host.title} version: $hostVersion")
        appendLine("Required APIs $requiredResolved / $requiredTotal, fallback signatures $fallbacks, optional unavailable $optionalMissing")
        appendLine()
        appendLine("Feature compatibility")
        for (feature in Feature.of(host)) {
            val state = when {
                isHostManaged(feature) -> "${ContractResult.PASS} ${feature.title} (host managed)"
                isCompatible(feature) -> "${ContractResult.PASS} ${feature.title}"
                else -> "${ContractResult.FAIL} ${feature.title}"
            }
            appendLine(state)
        }
        appendLine()
        appendLine("Required contracts")
        for (result in results.filter { it.contract.isRequired }) {
            appendLine("${result.mark} ${result.contract.signature.describe()}")
            result.explanation.forEach { appendLine("  $it") }
        }
        val optional = results.filterNot { it.contract.isRequired }
        if (optional.isNotEmpty()) {
            appendLine()
            appendLine("Optional probes")
        }
        for (result in optional) {
            appendLine("${result.mark} ${result.contract.signature.describe()}")
            result.explanation.forEach { appendLine("  $it") }
        }
    }.trimEnd()
}
