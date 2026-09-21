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

    /** Runs [before] with each call's arguments ahead of every call to [method], then lets the call proceed. */
    fun before(method: Method, before: (thisObject: Any?, args: List<Any?>) -> Unit)

    /**
     * Runs every call to [method] with the arguments [transform] makes of the
     * caller's instead. A [transform] that throws leaves the arguments as they were.
     */
    fun replaceArguments(method: Method, transform: (thisObject: Any?, args: List<Any?>) -> List<Any?>)

    /** Lets every call to [method] run, then runs [after] with its arguments. */
    fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit)

    /**
     * Lets every call to [method] run, then returns what [transform] makes of
     * its result instead. A [transform] that throws leaves the result as it was.
     */
    fun replaceResult(method: Method, transform: (thisObject: Any?, args: List<Any?>, result: Any?) -> Any?)
}

/**
 * [Hooks] over the modern Xposed API, as Vector implements it.
 *
 * Every hook carries an id made from the method it hooks and how many hooks of
 * this module that method already has, the same from one load of the module to
 * the next. A hot reload hooks again under the same ids, which replaces the
 * hooks of the code it replaces in place, and gathers them in [ids] so the ones
 * the new code no longer makes can be taken off.
 */
class XposedHooks(
    private val xposed: XposedInterface,
    private val logger: Logger,
    /** The ids of every hook made, shared by every [XposedHooks] of one load of the module. */
    val ids: MutableSet<String> = mutableSetOf(),
) : Hooks {

    override fun before(method: Method, before: (thisObject: Any?, args: List<Any?>) -> Unit) {
        hook(method).intercept { chain ->
            guarded(method) { before(chain.thisObject, chain.args) }
            chain.proceed()
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    override fun replaceArguments(method: Method, transform: (thisObject: Any?, args: List<Any?>) -> List<Any?>) {
        hook(method).intercept { chain ->
            val args = runCatching { transform(chain.thisObject, chain.args) }
                .onFailure { logger.warn("Hook body failed for ${method.declaringClass.simpleName}.${method.name}", it) }
                .getOrDefault(chain.args)
            chain.proceed(args.toTypedArray())
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    override fun after(method: Method, after: (thisObject: Any?, args: List<Any?>) -> Unit) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            guarded(method) { after(chain.thisObject, chain.args) }
            result
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    override fun replaceResult(method: Method, transform: (thisObject: Any?, args: List<Any?>, result: Any?) -> Any?) {
        hook(method).intercept { chain ->
            val result = chain.proceed()
            runCatching { transform(chain.thisObject, chain.args, result) }
                .onFailure { logger.warn("Hook body failed for ${method.declaringClass.simpleName}.${method.name}", it) }
                .getOrDefault(result)
        }
        logger.info("Hooked ${method.declaringClass.simpleName}.${method.name}")
    }

    private fun hook(method: Method): XposedInterface.HookBuilder {
        val signature = "${method.declaringClass.name}#${method.name}(${method.parameterTypes.joinToString { it.name }})"
        val id = generateSequence(0) { it + 1 }.map { "$signature#$it" }.first { synchronized(ids) { ids.add(it) } }
        return xposed.hook(method).setId(id)
    }

    private inline fun guarded(method: Method, body: () -> Unit) {
        runCatching(body)
            .onFailure { logger.warn("Hook body failed for ${method.declaringClass.simpleName}.${method.name}", it) }
    }
}
