package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * What one host build calls the names [HostContracts.locators] found renamed:
 * a class under its new name, a method under its new name and parameters.
 * A name absent here is used as the sources write it.
 */
class Relocations(val found: Map<Signature, Signature> = emptyMap()) {

    /** [name] as this build names the class. */
    fun className(name: String): String = found[Signature(name)]?.owner ?: name

    /** The method [owner] declares as [name] in the sources, as this build declares it; null when not renamed. */
    fun method(owner: String, name: String): Member.Method? =
        found.entries.firstOrNull { (from, _) -> from.owner == owner && from.member?.name == name }?.value?.member as? Member.Method

    /** [signature] as this build has it: its class renamed, then its member if that was. */
    fun relocate(signature: Signature): Signature {
        found[signature]?.let { return it }
        val member = (signature.member as? Member.Method)?.let { method(signature.owner, it.name) } ?: signature.member
        return Signature(className(signature.owner), member)
    }

    /** One line per relocation, `from<TAB>to`, for the cache file, the log and a host description. */
    fun format(): List<String> = found.map { (from, to) -> "${encode(from)}\t${encode(to)}" }.sorted()

    override fun equals(other: Any?): Boolean = other is Relocations && other.found == found

    override fun hashCode(): Int = found.hashCode()

    companion object {

        fun parse(lines: List<String>): Relocations = Relocations(
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size != 2) null else decode(parts[0]) to decode(parts[1])
            }.toMap(),
        )

        private fun encode(signature: Signature): String = when (val member = signature.member) {
            null -> signature.owner
            is Member.Field -> "${signature.owner}#${member.name}"
            is Member.Method -> "${signature.owner}#${member.name}(${member.parameters?.joinToString(",").orEmpty()})"
        }

        private fun decode(text: String): Signature {
            val owner = text.substringBefore('#')
            val member = text.substringAfter('#', "").takeIf { it.isNotEmpty() } ?: return Signature(owner)
            if ('(' !in member) return Signature(owner, Member.Field(member))
            val parameters = member.substringAfter('(').removeSuffix(")").split(',').filter { it.isNotEmpty() }
            return Signature(owner, Member.Method(member.substringBefore('('), parameters))
        }
    }
}

/**
 * The running host's [Relocations], filled once per load before any feature
 * installs, so the contract check and every feature's lookups agree.
 */
object HostSymbols {

    // ponytail: one table per process; each scoped process loads exactly one host.
    @Volatile
    var current: Relocations = Relocations()
}
