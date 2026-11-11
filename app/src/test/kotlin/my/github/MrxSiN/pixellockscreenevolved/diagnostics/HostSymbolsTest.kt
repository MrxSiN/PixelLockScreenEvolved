package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

/** A name a build changed is met by fallback once located, and survives the cache file. */
class HostSymbolsTest {

    private val renamed = Signature(Lambda::class.java.name, Member.Method("onDoubleClick", emptyList()))
    private val found = Signature(Lambda::class.java.name, Member.Method("onDoubleTap", listOf("java.lang.Float", "java.lang.Float")))
    private val binding = Signature("host.Binder\$bind\$13\$1")
    private val bindingFound = Signature("host.Binder\$bind\$16\$1")

    @Test
    fun relocationsSurviveTheCacheFile() {
        val relocations = Relocations(mapOf(renamed to found, binding to bindingFound))

        assertEquals(relocations, Relocations.parse(relocations.format()))
    }

    @Test
    fun aRenamedMethodIsMetByFallback() {
        val relocations = Relocations(mapOf(renamed to found))
        val types = ReflectedHostTypes { name -> runCatching { Class.forName(name) }.getOrNull() }

        val result = ContractAnalyzer(types, relocations::relocate).analyze(listOf(HostContract(renamed, emptySet()))).results.single()

        assertEquals(ContractOutcome.FALLBACK, result.outcome)
        assertEquals(found, result.alternative)
    }

    @Test
    fun aRenumberedClassCarriesItsMembers() {
        val relocations = Relocations(mapOf(binding to bindingFound))

        assertEquals(Signature(bindingFound.owner, Member.Field("\$view")), relocations.relocate(Signature(binding.owner, Member.Field("\$view"))))
        assertEquals("other.Type", relocations.className("other.Type"))
    }

    @Suppress("unused")
    private class Lambda {
        fun onDoubleTap(x: Float?, y: Float?) = Unit
    }
}
