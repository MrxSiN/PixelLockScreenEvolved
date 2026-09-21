package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * Counts what goes wrong inside each feature while the host runs, and stops a
 * feature that keeps failing.
 *
 * The contract check ([ContractAnalyzer]) only sees that a member is there. A
 * monthly update can keep the name and change what it does, or R8 can reshape
 * what a hook reads, and the feature then fails on every call. Rather than
 * leave it failing, and slowing or confusing the host, a feature that fails
 * [threshold] times is tripped: its hooks let the host run as if they were not
 * there ([my.github.MrxSiN.pixellockscreenevolved.hook.FeatureHooks]) until the
 * host starts again. Every other feature carries on. Kept free of Android so it
 * can be tested on a desk.
 */
class FeatureFaults(
    private val logger: Logger,
    private val threshold: Int = DEFAULT_THRESHOLD,
) {

    private val failures = ConcurrentHashMap<Feature, AtomicInteger>()
    private val tripped: MutableSet<Feature> = ConcurrentHashMap.newKeySet()
    private val listeners = CopyOnWriteArrayList<(Feature) -> Unit>()

    /** Whether [feature] has failed too often and is stopped until the host starts again. */
    fun isTripped(feature: Feature): Boolean = feature in tripped

    /** Every feature stopped so far. */
    fun tripped(): Set<Feature> = tripped.toSet()

    /** Records one failure of [feature]; the one that reaches [threshold] trips it. */
    fun record(feature: Feature, error: Throwable) {
        val count = failures.getOrPut(feature, ::AtomicInteger).incrementAndGet()
        if (count >= threshold) stop(feature, "failed $count times", error)
    }

    /** Stops [feature] at once, for a failure it cannot go on after, such as one while it was being installed. */
    fun trip(feature: Feature, error: Throwable) = stop(feature, "failed", error)

    private fun stop(feature: Feature, what: String, error: Throwable) {
        if (!tripped.add(feature)) return
        logger.warn("${feature.title} $what and is stopped until ${feature.host.title} restarts", error)
        listeners.forEach { runCatching { it(feature) } }
    }

    /** Calls [listener] with each feature as it trips. */
    fun addListener(listener: (Feature) -> Unit) {
        listeners += listener
    }

    companion object {
        /** Failures a feature is allowed before it is stopped: enough to ride out a one-off, few enough to stop a loop. */
        const val DEFAULT_THRESHOLD: Int = 8
    }
}
