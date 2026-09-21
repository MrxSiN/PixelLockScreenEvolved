package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * Whether a feature can be used now, as its host last reported
 * ([HostSnapshot]), and how to say so beside its switch or on the
 * compatibility page.
 *
 * A host with no snapshot from this boot has not loaded the module, or not
 * yet; its features are then left usable, as nothing says otherwise.
 */
class FeatureAvailability(val snapshots: Map<Host, HostSnapshot>) {

    fun status(feature: Feature): FeatureStatus =
        snapshots[feature.host]?.status(feature) ?: FeatureStatus.AVAILABLE

    fun isAvailable(feature: Feature): Boolean = status(feature) in setOf(FeatureStatus.AVAILABLE, FeatureStatus.HOST_MANAGED)

    /** "Unavailable on SystemUI 37", with why and what to do, for a feature that is not available; null for one that is. */
    fun unavailableReason(feature: Feature): String? {
        val snapshot = snapshots[feature.host] ?: return null
        return when (snapshot.status(feature)) {
            FeatureStatus.AVAILABLE, FeatureStatus.HOST_MANAGED -> null
            FeatureStatus.INCOMPATIBLE ->
                "Unavailable on ${feature.host.title} ${snapshot.hostVersion} (${snapshot.build}). " +
                    "${feature.host.title} internals changed. Update Pixel Lock Screen Evolved."
            FeatureStatus.HELD_BY_SAFE_MODE ->
                "Turned off by Safe Mode after ${feature.host.title} crashed repeatedly."
            FeatureStatus.FAULTED ->
                "Stopped after repeated errors on ${feature.host.title} ${snapshot.hostVersion} (${snapshot.build}). " +
                    "It is tried again when ${feature.host.title} restarts."
        }
    }

    /** Where a feature was checked and what was found, for the compatibility page. */
    fun summary(feature: Feature): String {
        val snapshot = snapshots[feature.host] ?: return "${feature.host.title} has not loaded the module since the phone started"
        if (snapshot.status(feature) == FeatureStatus.HOST_MANAGED) {
            return "Handled by ${feature.host.title} ${snapshot.hostVersion}; no module hook is needed"
        }
        return unavailableReason(feature)
            ?: "Supported on ${feature.host.title} ${snapshot.hostVersion}\nStatus: ${ContractResult.PASS} All required host APIs found"
    }

    companion object {
        /** The snapshots [store] holds from the boot [bootedAt] names. */
        fun current(store: SnapshotStore, bootedAt: Long): FeatureAvailability =
            FeatureAvailability(
                Host.entries.mapNotNull { host -> store.read(host)?.takeIf { it.isFrom(bootedAt) }?.let { host to it } }.toMap(),
            )
    }
}
