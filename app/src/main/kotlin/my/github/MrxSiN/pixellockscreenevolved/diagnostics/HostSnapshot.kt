package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * What one host found about itself as it last started: its build, the
 * framework it runs this module under, the contract report, and what Safe Mode
 * holds off there.
 *
 * Each host writes its own ([SnapshotStore]) so Wallpaper & style can show
 * SystemUI's on the compatibility page and grey out an option SystemUI cannot
 * honour, without looking into SystemUI itself.
 */
data class HostSnapshot(
    val host: Host,
    /** The host's `versionCode`. */
    val hostVersion: String,
    /** The system build the host ran on, `Build.ID`. */
    val build: String,
    /** When the device last booted, in wall-clock milliseconds, to tell a snapshot of this boot from an older one. */
    val bootedAt: Long,
    val xposedApiVersion: Int,
    val frameworkName: String,
    val frameworkVersion: String,
    val report: ContractReport,
    val held: Set<Feature>,
    val isAnswered: Boolean,
    /** Features stopped since the host started because they kept failing ([FeatureFaults]). */
    val faulted: Set<Feature> = emptySet(),
) {

    /** Whether the module was checked against this system build ([VerifiedBuilds]). */
    val isVerifiedBuild: Boolean get() = VerifiedBuilds.isVerified(build)

    fun status(feature: Feature): FeatureStatus = when {
        report.isHostManaged(feature) -> FeatureStatus.HOST_MANAGED
        !report.isCompatible(feature) -> FeatureStatus.INCOMPATIBLE
        feature in held -> FeatureStatus.HELD_BY_SAFE_MODE
        feature in faulted -> FeatureStatus.FAULTED
        else -> FeatureStatus.AVAILABLE
    }

    /** Whether Safe Mode holds something off that the person has not yet answered for. */
    val asksAboutSafeMode: Boolean get() = held.isNotEmpty() && !isAnswered

    /**
     * Whether this was written in the boot [bootedAt] names, so the host has the
     * module loaded now. Boot times read a moment apart differ by a little.
     */
    fun isFrom(bootedAt: Long): Boolean = kotlin.math.abs(this.bootedAt - bootedAt) <= BOOT_TIME_SLACK_MS

    /** The whole snapshot as text, for a log or a bug report. */
    fun format(): String = buildString {
        appendLine(report.format(host, hostVersion))
        if (!isVerifiedBuild) {
            appendLine()
            appendLine("Build $build is untested; each feature was checked on its own")
        }
        if (faulted.isNotEmpty()) {
            appendLine()
            appendLine("Stopped after repeated errors: ${faulted.joinToString { it.title }}")
        }
        if (held.isNotEmpty()) {
            appendLine()
            appendLine("Safe Mode holds off: ${held.joinToString { it.title }}")
        }
    }.trimEnd()

    private companion object {
        const val BOOT_TIME_SLACK_MS = 60_000L
    }
}

enum class FeatureStatus { AVAILABLE, HOST_MANAGED, INCOMPATIBLE, HELD_BY_SAFE_MODE, FAULTED }
