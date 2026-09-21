package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractAnalyzerTest {

    private val analyzer = ContractAnalyzer { name -> runCatching { Class.forName(name) }.getOrNull() }

    @Test
    fun aMethodWithMatchingParametersResolves() {
        assertEquals(ContractOutcome.RESOLVED, check(Member.Method("add", listOf("java.lang.Object"))).outcome)
    }

    @Test
    fun aMemberDeclaredOnASuperclassResolvesPrivateOrNot() {
        assertEquals(ContractOutcome.RESOLVED, check(Member.Field("modCount")).outcome)
    }

    @Test
    fun aMethodWithOtherParametersIsMissing() {
        assertEquals(ContractOutcome.MISSING_MEMBER, check(Member.Method("add", listOf("long"))).outcome)
    }

    @Test
    fun aMissingClassIsToldApartFromAMissingMember() {
        val contract = HostContract(Signature("com.example.Gone", Member.Method("run")), setOf(Feature.DEPTH_EFFECT))

        assertEquals(ContractOutcome.MISSING_CLASS, analyzer.analyze(listOf(contract)).results.single().outcome)
    }

    @Test
    fun anAlternativeThatExistsIsAFallback() {
        val report = analyzer.analyze(listOf(fallbackContract()))

        assertEquals(ContractOutcome.FALLBACK, report.results.single().outcome)
        assertEquals("ArrayList.trimToSize()", report.results.single().alternative?.describe())
        assertEquals(1, report.fallbacks)
        assertEquals(0, report.missingRequired)
        assertTrue(report.isCompatible(Feature.CLOCK_STYLES))
    }

    @Test
    fun onlyAMissingRequiredMemberMakesAFeatureIncompatible() {
        val optional = HostContract(Signature(LIST, Member.Method("gone")), setOf(Feature.DEPTH_EFFECT), isRequired = false)
        val required = HostContract(Signature(LIST, Member.Method("gone")), setOf(Feature.AOD_WALLPAPER))

        val report = analyzer.analyze(listOf(optional, required))

        assertTrue(report.isCompatible(Feature.DEPTH_EFFECT))
        assertFalse(report.isCompatible(Feature.AOD_WALLPAPER))
        assertEquals(1, report.missingRequired)
        assertEquals(1, report.optionalMissing)
        assertEquals(ContractResult.WARN, report.results.first().mark)
    }

    @Test
    fun theReportReadsAsTheCompatibilityPageDoes() {
        val text = analyzer.analyze(listOf(fallbackContract())).format(Host.SYSTEMUI, "37")

        assertTrue(text, "SystemUI version: 37" in text)
        assertTrue(text, "Required APIs 1 / 1, fallback signatures 1, optional unavailable 0" in text)
        assertTrue(text, "✓ Clock styles" in text)
        assertTrue(text, "⚠ ArrayList.registerListeners\n  Missing\n  Alternative found:\n  ArrayList.trimToSize()" in text)
    }

    private fun fallbackContract() = HostContract(
        Signature(LIST, Member.Method("registerListeners")),
        setOf(Feature.CLOCK_STYLES),
        alternatives = listOf(Signature(LIST, Member.Method("trimToSize", emptyList()))),
    )

    private fun check(member: Member): ContractResult =
        analyzer.analyze(listOf(HostContract(Signature(LIST, member), setOf(Feature.CLOCK_STYLES)))).results.single()

    private companion object {
        const val LIST = "java.util.ArrayList"
    }
}
