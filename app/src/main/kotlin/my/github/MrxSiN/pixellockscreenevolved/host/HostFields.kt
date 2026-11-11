package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostSymbols

/** This class's own field [name], opened for reading and writing whatever its visibility. */
internal fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }

/** The host class the sources call [name], under whatever name this build gave it ([HostSymbols]). */
internal fun ClassLoader.hostClass(name: String): Class<*> = Class.forName(HostSymbols.current.className(name), false, this)

/**
 * This class's own method the sources call [name] with [parameters], under
 * whatever name and parameters this build gave it ([HostSymbols]).
 */
internal fun Class<*>.hostMethod(owner: String, name: String, vararg parameters: Class<*>): Method {
    val renamed = HostSymbols.current.method(owner, name) ?: return getDeclaredMethod(name, *parameters)
    return declaredMethods.first { method ->
        method.name == renamed.name && method.parameterTypes.map(Class<*>::getName) == renamed.parameters
    }
}
