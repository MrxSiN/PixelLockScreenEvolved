package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Installs a feature's patches only when the host running now can take them,
 * and keeps one feature's failure to install from reaching any other.
 *
 * A feature whose required members are gone ([ContractReport.isCompatible]) is
 * left out rather than installed to fail later, and one Safe Mode holds off
 * ([held]) is left out until it is restored. Either way the reason is logged,
 * and the compatibility page says the same. A patch that throws as it installs
 * stops its feature ([FeatureFaults.trip]), which also quiets whatever hooks it
 * had already made, and the next patch installs as if nothing happened.
 */
class FeatureGate(
    private val report: ContractReport,
    private val held: Set<Feature>,
    private val logger: Logger,
    private val faults: FeatureFaults = FeatureFaults(logger),
) {

    /** Features already reported as left out, so each is logged once however many patches it has. */
    private val reported = mutableSetOf<Feature>()

    /** [patch], installed only while [feature] is allowed. */
    fun gate(feature: Feature, patch: HostPatch): HostPatch = HostPatch { classLoader ->
        if (!allows(feature)) return@HostPatch
        runCatching { patch.install(classLoader) }.onFailure { faults.trip(feature, it) }
    }

    fun allows(feature: Feature): Boolean {
        if (report.isHostManaged(feature)) return false
        val firstAsked = reported.add(feature)
        if (!report.isCompatible(feature)) {
            if (!firstAsked) return false
            val missing = report.results
                .filter { feature in it.contract.features && it.contract.isRequired && !it.isMet }
                .joinToString { it.contract.signature.describe() }
            logger.warn("${feature.title} is unavailable: this ${feature.host.title} lacks $missing")
            return false
        }
        if (feature in held) {
            if (firstAsked) logger.warn("${feature.title} is held off by Safe Mode")
            return false
        }
        return !faults.isTripped(feature)
    }
}
