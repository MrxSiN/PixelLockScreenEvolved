package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * The system builds this module was tried on, as `Build.ID` names them.
 *
 * Nothing is refused on any other build: each feature is still checked on its
 * own ([ContractAnalyzer]) and stopped if it keeps failing ([FeatureFaults]).
 * Knowing the build only lets the compatibility page say whether what it shows
 * was seen working, or is the module finding its way on a build it has not met.
 * HOOK_NOTES.md records what was read off each.
 */
object VerifiedBuilds {

    private val builds = setOf(
        "CP2A.260805.005",
        "CP3A.260905.009",
    )

    fun isVerified(build: String): Boolean = build in builds
}
