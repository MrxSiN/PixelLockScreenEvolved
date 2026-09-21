package my.github.MrxSiN.pixellockscreenevolved.hook

import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureFaults

/**
 * [Hooks] that belong to one [feature], and stop once it has failed too often.
 *
 * Every body is run through [faults]: one that throws is counted against the
 * feature, then handed on to [delegate], which reports it and keeps the host's
 * call going. Once the feature is tripped its bodies are skipped altogether,
 * so the host runs as though it was never patched, while the other features'
 * hooks carry on untouched.
 */
class FeatureHooks(
    private val delegate: Hooks,
    private val feature: Feature,
    private val faults: FeatureFaults,
) : Hooks {

    override fun before(method: Method, before: (thisObject: Any?, args: List<Any?>) -> Unit) =
        delegate.before(method) { target, args -> if (!stopped()) counted { before(target, args) } }

    override fun replaceArguments(method: Method, transform: (thisObject: Any?, args: List<Any?>) -> List<Any?>) =
        delegate.replaceArguments(method) { target, args -> if (stopped()) args else counted { transform(target, args) } }

    override fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit) =
        delegate.after(method) { target, args -> if (!stopped()) counted { after(target, args) } }

    override fun replaceResult(method: Method, transform: (thisObject: Any?, args: List<Any?>, result: Any?) -> Any?) =
        delegate.replaceResult(method) { target, args, result -> if (stopped()) result else counted { transform(target, args, result) } }

    private fun stopped(): Boolean = faults.isTripped(feature)

    private inline fun <T> counted(body: () -> T): T = try {
        body()
    } catch (error: Throwable) {
        faults.record(feature, error)
        throw error
    }
}

/** These hooks, answering for [feature] to [faults]. */
fun Hooks.forFeature(feature: Feature, faults: FeatureFaults): Hooks = FeatureHooks(this, feature, faults)
