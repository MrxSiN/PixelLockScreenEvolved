package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/** Answers one interface method, given the arguments it was called with. */
internal typealias MethodHandler = (args: Array<out Any?>) -> Any?

/** A handler for a callback this module has nothing to do with. */
internal val IgnoreCall: MethodHandler = { _ -> null }

/**
 * Implements host interfaces this module cannot link against.
 *
 * SystemUI's clock plugin interfaces live only in the host class loader, so
 * they are implemented by name. A method without a handler returns the zero of
 * its type, and is reported once: a SystemUI update that adds a method should
 * show up in the log, not crash the lock screen.
 */
internal class InterfaceProxy(
    private val classLoader: ClassLoader,
    private val logger: Logger,
) {

    private val reported = Collections.synchronizedSet(HashSet<String>())

    fun create(type: Class<*>, handlers: Map<String, MethodHandler>): Any =
        Proxy.newProxyInstance(classLoader, arrayOf(type)) { proxy, method, args ->
            val arguments: Array<out Any?> = args ?: NO_ARGS
            when (method.name) {
                "equals" -> proxy === arguments.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "${type.simpleName}@PixelLockScreenEvolved"
                else -> handle(type, method, handlers[method.name], arguments)
            }
        }

    private fun handle(type: Class<*>, method: Method, handler: MethodHandler?, args: Array<out Any?>): Any? {
        if (handler == null) {
            if (reported.add("${type.name}.${method.name}")) {
                logger.info("No handler for ${type.simpleName}.${method.name}; answering its default")
            }
            return zeroOf(method.returnType)
        }
        return handler(args) ?: zeroOf(method.returnType)
    }

    private companion object {
        val NO_ARGS = emptyArray<Any?>()

        fun zeroOf(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> 0.toChar()
            List::class.java -> emptyList<Any>()
            else -> null
        }
    }
}
