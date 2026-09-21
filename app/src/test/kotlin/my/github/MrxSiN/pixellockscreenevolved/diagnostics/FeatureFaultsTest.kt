package my.github.MrxSiN.pixellockscreenevolved.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

class FeatureFaultsTest {

    private val error = IllegalStateException("moved")

    @Test
    fun aFeatureIsStoppedOnlyOnceItReachesTheThreshold() {
        val faults = FeatureFaults(SilentLogger, threshold = 3)

        repeat(2) { faults.record(Feature.DEPTH_EFFECT, error) }
        assertFalse(faults.isTripped(Feature.DEPTH_EFFECT))

        faults.record(Feature.DEPTH_EFFECT, error)
        assertTrue(faults.isTripped(Feature.DEPTH_EFFECT))
        assertFalse(faults.isTripped(Feature.AOD_WALLPAPER))
    }

    @Test
    fun listenersHearEachFeatureTripOnce() {
        val faults = FeatureFaults(SilentLogger, threshold = 1)
        val heard = mutableListOf<Feature>()
        faults.addListener { heard += it }

        faults.record(Feature.CLOCK_STYLES, error)
        faults.record(Feature.CLOCK_STYLES, error)
        faults.trip(Feature.CLOCK_STYLES, error)

        assertEquals(listOf(Feature.CLOCK_STYLES), heard)
    }

    @Test
    fun aGateLeavesOutAFeatureThatFailedToInstallAndInstallsTheRest() {
        val faults = FeatureFaults(SilentLogger)
        val gate = FeatureGate(ContractReport(emptyList()), emptySet(), SilentLogger, faults)
        var installed = false
        val loader = requireNotNull(javaClass.classLoader)

        gate.gate(Feature.DEPTH_EFFECT) { throw error }.install(loader)
        gate.gate(Feature.AOD_WALLPAPER) { installed = true }.install(loader)

        assertTrue(faults.isTripped(Feature.DEPTH_EFFECT))
        assertFalse(gate.allows(Feature.DEPTH_EFFECT))
        assertTrue(installed)
    }

    private object SilentLogger : Logger {
        override fun info(message: String) = Unit
        override fun warn(message: String, error: Throwable?) = Unit
    }
}
