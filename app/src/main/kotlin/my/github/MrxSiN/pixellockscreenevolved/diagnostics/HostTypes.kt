package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * The classes of a host, as far as a contract check needs to see them.
 *
 * [ContractAnalyzer] reads a host through this rather than through a class
 * loader, so the same check runs inside the running host ([ReflectedHostTypes])
 * and, on a desk, against a description of a host build read off a device
 * ([HostDescriptor]).
 */
fun interface HostTypes {

    /** The class by binary name, or null when the host has none. */
    fun find(name: String): HostType?
}

/** One host class: what it extends and the members it declares itself. */
interface HostType {

    val name: String

    /** The class it extends, or null for `java.lang.Object` and interfaces. */
    val superclass: HostType?

    val fields: Set<String>

    val methods: List<MethodShape>

    /** This class, then each class above it. */
    fun hierarchy(): Sequence<HostType> = generateSequence(this) { it.superclass }
}

/** A declared method: its name and its parameters' binary type names. */
data class MethodShape(val name: String, val parameters: List<String>)

/**
 * The running host's classes, read by reflection.
 *
 * Each class is read once and its members only when asked for, since the same
 * base classes (a view, a coroutine) sit under many contracts.
 *
 * @param load the host's class by binary name, or null when it has none.
 */
class ReflectedHostTypes(private val load: (String) -> Class<*>?) : HostTypes {

    private val types = HashMap<Class<*>, HostType>()

    override fun find(name: String): HostType? = load(name)?.let(::typeOf)

    private fun typeOf(type: Class<*>): HostType = types.getOrPut(type) { Reflected(type) }

    private inner class Reflected(private val type: Class<*>) : HostType {
        override val name: String get() = type.name
        override val superclass: HostType? get() = type.superclass?.let(::typeOf)
        override val fields: Set<String> by lazy { type.declaredFields.mapTo(HashSet()) { it.name } }
        override val methods: List<MethodShape> by lazy {
            type.declaredMethods.map { method -> MethodShape(method.name, method.parameterTypes.map(Class<*>::getName)) }
        }
    }
}
