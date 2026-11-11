package my.github.MrxSiN.pixellockscreenevolved.diagnostics

/**
 * A host build's classes written down as text: what each class the contracts
 * name extends and declares, and which of them the build does not have.
 *
 * Only names are kept, never code, so a description can live beside the tests
 * without shipping any of the host. The tests read the descriptions back
 * ([read]) and check every [HostContract] against each recorded build, so a
 * contract a refactor gets wrong fails the build before it reaches a phone.
 *
 * ```
 * build CP3A.260905.009
 * host com.android.systemui
 * class com.android.systemui.shared.clocks.ClockRegistry extends java.lang.Object
 *   field availableClocks
 *   method registerListeners()
 * absent com.android.systemui.keyguard.ui.view.layout.sections.transitions.ClockSizeTransition
 * relocated <owner>#onDoubleClick()<TAB><owner>#onDoubleTap(java.lang.Float,java.lang.Float)
 * ```
 *
 * `relocated` lines are what DexKit found renamed on that build
 * ([Relocations]); the classes they point at are described too.
 */
object HostDescriptor {

    /**
     * Describes [classes] as [types] sees them, with every class above each,
     * sorted so that two captures of one build are the same text.
     */
    fun write(build: String, host: Host, types: HostTypes, classes: Collection<String>, relocations: Relocations = Relocations()): String {
        val described = sortedMapOf<String, HostType>()
        val absent = sortedSetOf<String>()
        for (name in classes + relocations.found.values.map(Signature::owner)) {
            val type = types.find(name)
            if (type == null) absent += name else type.hierarchy().forEach { described.putIfAbsent(it.name, it) }
        }
        return buildString {
            appendLine(HEADER_COMMENT)
            appendLine("$BUILD $build")
            appendLine("$HOST ${host.packageName}")
            for (type in described.values) {
                appendLine("$CLASS ${type.name}" + type.superclass?.let { " $EXTENDS ${it.name}" }.orEmpty())
                // A class the host cannot fully read is kept with what it could.
                runCatching { type.fields.sorted() }.getOrDefault(emptyList()).forEach { appendLine("  $FIELD $it") }
                runCatching { type.methods.map(::describe).sorted() }.getOrDefault(emptyList()).forEach { appendLine("  $METHOD $it") }
            }
            absent.forEach { appendLine("$ABSENT $it") }
            relocations.format().forEach { appendLine("$RELOCATED $it") }
        }
    }

    /** A written description, read back. */
    fun read(text: String): DescribedHost {
        var build = ""
        var host: Host? = null
        val classes = mutableListOf<ClassRecord>()
        val absent = mutableSetOf<String>()
        val relocated = mutableListOf<String>()

        for (line in text.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val keyword = line.substringBefore(' ')
            val rest = line.substringAfter(' ')
            when (keyword) {
                BUILD -> build = rest
                HOST -> host = Host.of(rest)
                CLASS -> classes += ClassRecord(rest.substringBefore(' '), rest.substringAfter(" $EXTENDS ", "").ifEmpty { null })
                FIELD -> requireNotNull(classes.lastOrNull()) { "A field outside a class: $line" }.fields += rest
                METHOD -> requireNotNull(classes.lastOrNull()) { "A method outside a class: $line" }.methods += method(rest)
                ABSENT -> absent += rest
                RELOCATED -> relocated += rest
                else -> error("Unknown line in a host description: $line")
            }
        }
        return DescribedHost(build, requireNotNull(host) { "A host description names no host" }, classes, absent, Relocations.parse(relocated))
    }

    private fun describe(method: MethodShape): String = method.parameters.joinToString(",", "${method.name}(", ")")

    private fun method(text: String) = MethodShape(
        text.substringBefore('('),
        text.substringAfter('(').removeSuffix(")").split(',').filter(String::isNotEmpty),
    )

    /** One `class` line and the members under it. */
    internal class ClassRecord(val name: String, val superName: String?) {
        val fields = mutableSetOf<String>()
        val methods = mutableListOf<MethodShape>()
    }

    /**
     * One recorded host build. Asked for a class it recorded neither way, it
     * fails rather than answer: the build was captured before a contract named
     * that class, so its description has to be captured again.
     */
    class DescribedHost internal constructor(
        val build: String,
        val host: Host,
        records: List<ClassRecord>,
        private val absent: Set<String>,
        /** What DexKit found renamed on this build, as the host found it. */
        val relocations: Relocations,
    ) : HostTypes {

        private val classes: Map<String, HostType> = records.associate { it.name to Described(it) }

        override fun find(name: String): HostType? {
            classes[name]?.let { return it }
            check(name in absent) { "$build does not record $name; capture ${host.packageName} on $build again (scripts/capture-host-fixture.sh)" }
            return null
        }

        private inner class Described(private val record: ClassRecord) : HostType {
            override val name: String get() = record.name
            override val superclass: HostType? get() = record.superName?.let(::find)
            override val fields: Set<String> get() = record.fields
            override val methods: List<MethodShape> get() = record.methods
        }
    }

    private const val HEADER_COMMENT = "# Generated by scripts/capture-host-fixture.sh: class and member names only, no host code."
    private const val BUILD = "build"
    private const val HOST = "host"
    private const val CLASS = "class"
    private const val EXTENDS = "extends"
    private const val FIELD = "field"
    private const val METHOD = "method"
    private const val ABSENT = "absent"
    private const val RELOCATED = "relocated"
}
