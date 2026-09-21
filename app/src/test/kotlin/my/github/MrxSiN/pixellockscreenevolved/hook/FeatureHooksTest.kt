package my.github.MrxSiN.pixellockscreenevolved.hook

import java.lang.reflect.Method

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureFaults

class FeatureHooksTest {

    private val method: Method = Any::class.java.getMethod("toString")

    @Test
    fun aFailingBodyIsCountedAndThenSkippedOnceTheFeatureIsStopped() {
        val faults = FeatureFaults(SilentLogger, threshold = 2)
        val hooks = RecordingHooks()
        var runs = 0
        hooks.forFeature(Feature.DEPTH_EFFECT, faults).after(method) { _, _ ->
            runs++
            throw IllegalStateException("moved")
        }

        repeat(4) { hooks.call() }

        assertTrue(faults.isTripped(Feature.DEPTH_EFFECT))
        assertEquals(2, runs)
    }

    @Test
    fun aStoppedFeatureLeavesTheHostsResultAlone() {
        val faults = FeatureFaults(SilentLogger).apply { trip(Feature.CLOCK_STYLES, IllegalStateException()) }
        val hooks = RecordingHooks()
        hooks.forFeature(Feature.CLOCK_STYLES, faults).replaceResult(method) { _, _, _ -> "changed" }

        assertEquals("host", hooks.result("host"))
    }

    /** Hooks that keep the body given and run it on demand, swallowing what it throws as XposedHooks does. */
    private class RecordingHooks : Hooks {
        private var after: ((Any?, List<Any?>) -> Unit)? = null
        private var transform: ((Any?, List<Any?>, Any?) -> Any?)? = null

        fun call() {
            runCatching { after?.invoke(null, emptyList()) }
        }

        fun result(host: Any?): Any? = runCatching { transform?.invoke(null, emptyList(), host) }.getOrDefault(host)

        override fun before(method: Method, before: (thisObject: Any?, args: List<Any?>) -> Unit) = Unit

        override fun replaceArguments(method: Method, transform: (thisObject: Any?, args: List<Any?>) -> List<Any?>) = Unit

        override fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit) {
            this.after = after
        }

        override fun replaceResult(method: Method, transform: (thisObject: Any?, args: List<Any?>, result: Any?) -> Any?) {
            this.transform = transform
        }
    }

    private object SilentLogger : Logger {
        override fun info(message: String) = Unit
        override fun warn(message: String, error: Throwable?) = Unit
    }
}
