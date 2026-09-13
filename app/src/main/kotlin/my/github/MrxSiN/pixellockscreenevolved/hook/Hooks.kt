package my.github.MrxSiN.pixellockscreenevolved.hook

import java.lang.reflect.Method

import io.github.libxposed.api.XposedInterface

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The hooking primitives this module needs.
 *
 * Code that changes a host process depends on this rather than on the Xposed
 * API, so nothing outside this package knows which framework is underneath.
 * A body that throws is reported and swallowed: a broken tweak must never take
 * the host method down with it.
 */
interface Hooks {

    /** Runs [before] ahead of every call to [method], then lets the call proceed. */
    fun before(method: Method, before: (thisObject: Any?) -> Unit)

    /** Lets every call to [method] run, then runs [after] with its arguments. */
    fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit)
}

/** [Hooks] over the modern Xposed API, as Vector implements it. */
class XposedHooks(
    private val xposed: XposedInterface,
    private val logger: Logger,
) : Hooks {

    override fun before(method: Method, before: (thisObject: Any?) -> Unit) {
        xposed.hook(method).intercept { chain ->
            guarded(method) { before(chain.thisObject) }
            chain.proceed()
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    override fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit) {
        xposed.hook(method).intercept { chain ->
            val result = chain.proceed()
            guarded(method) { after(chain.thisObject, chain.args) }
            result
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    private inline fun guarded(method: Method, body: () -> Unit) {
        runCatching(body)
            .onFailure { logger.warn("Hook body failed for ${method.declaringClass.simpleName}.${method.name}", it) }
    }
}
