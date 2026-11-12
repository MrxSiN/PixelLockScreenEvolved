package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * One member of a host a feature cannot work without.
 *
 * Kept free of Android and of any host class, so the list can be read and
 * checked on a desk as well as inside the host.
 */
data class HostContract(
    val signature: Signature,
    val features: Set<Feature>,
    /**
     * What the module falls back to when [signature] is missing, in the order
     * it tries them. A contract met by one of these still works, on an older or
     * newer build than the one the module prefers.
     */
    val alternatives: List<Signature> = emptyList(),
    /**
     * False for a member the feature reports and skips when it is missing. Its
     * absence is shown, but does not make the feature incompatible.
     */
    val isRequired: Boolean = true,
)

/** A class, or a member of one, named as the host's dex names it. */
data class Signature(
    /** The binary class name, with `$` before a nested class. */
    val owner: String,
    val member: Member? = null,
) {

    /** `ClockRegistry.availableClocks`, the way a person reads it. */
    fun describe(): String {
        val type = owner.substringAfterLast('.')
        return member?.let { "$type.${it.describe()}" } ?: type
    }
}

/**
 * How to find a class or method by what it is, on a build that renamed it:
 * Kotlin numbers its lambdas by position, so a new lambda earlier in a file
 * renames every one after it, and Google renames methods it rewrites.
 *
 * Searched in the host's dex (DexKit) only when the name misses, once per host
 * build ([HostSymbols]).
 */
sealed interface Locator {

    /**
     * The one class nested in [outer] that declares every one of [fields] (or, [onlyFields], those
     * fields and no others) and [methods], each of which calls [calling] when it is given. R8 merges
     * lambdas of one shape into a class switching on `$r8$classId`, so field names alone can match
     * several; what a method calls, or the exact fields, tells them apart.
     */
    data class NestedClass(
        val outer: String,
        val fields: Set<String>,
        val methods: Set<String> = emptySet(),
        val calling: MethodCalling? = null,
        val onlyFields: Boolean = false,
    ) : Locator

    /** The method of the signature's class that calls [method] of [owner]. */
    data class MethodCalling(val owner: String, val method: String) : Locator
}

sealed interface Member {

    val name: String

    fun describe(): String

    /**
     * A method, matched by name alone or by its parameters too.
     *
     * Null parameters match any overload. The module finds some methods by
     * shape rather than by signature, because the shrinker rewrites their
     * parameter types between releases; those are checked the same way here.
     */
    data class Method(override val name: String, val parameters: List<String>? = null) : Member {
        override fun describe(): String =
            parameters?.joinToString(prefix = "$name(", postfix = ")") { it.substringAfterLast('.') } ?: name
    }

    data class Field(override val name: String) : Member {
        override fun describe(): String = name
    }
}
