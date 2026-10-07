package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** A description read back answers a contract check the way the classes it was taken from do. */
class HostDescriptorTest {

    private val reflected = ReflectedHostTypes { name -> runCatching { Class.forName(name) }.getOrNull() }
    private val text = HostDescriptor.write("TEST.1", Host.SYSTEMUI, reflected, listOf(LIST, GONE))
    private val described = HostDescriptor.read(text)

    @Test
    fun theHeaderIsReadBack() {
        assertEquals("TEST.1", described.build)
        assertEquals(Host.SYSTEMUI, described.host)
    }

    @Test
    fun aDescriptionChecksContractsAsTheClassesDo() {
        val contracts = listOf(
            Member.Method("add", listOf("java.lang.Object")),
            Member.Method("add", listOf("int", "java.lang.Object")),
            Member.Method("add", listOf("long")),
            Member.Method("trimToSize"),
            Member.Field("modCount"),
            Member.Field("gone"),
        ).map { HostContract(Signature(LIST, it), setOf(Feature.CLOCK_STYLES)) } +
            HostContract(Signature(GONE), setOf(Feature.CLOCK_STYLES))

        val outcomes = { types: HostTypes -> ContractAnalyzer(types).analyze(contracts).results.map { it.outcome } }

        assertEquals(outcomes(reflected), outcomes(described))
    }

    @Test
    fun writingIsStable() {
        assertEquals(text, HostDescriptor.write("TEST.1", Host.SYSTEMUI, described, listOf(GONE, LIST)))
    }

    @Test
    fun aClassRecordedAbsentIsMissing() {
        assertNull(described.find(GONE))
    }

    @Test
    fun aClassNeverRecordedIsAnErrorNotAnAbsence() {
        assertThrows(IllegalStateException::class.java) { described.find("java.util.LinkedList") }
    }

    private companion object {
        const val LIST = "java.util.ArrayList"
        const val GONE = "com.example.Gone"
    }
}
