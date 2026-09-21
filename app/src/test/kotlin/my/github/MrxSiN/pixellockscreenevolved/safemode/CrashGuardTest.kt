package my.github.MrxSiN.pixellockscreenevolved.safemode

import java.nio.file.Files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Host
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SafeModeAnswer

class CrashGuardTest {

    private val store = FileSafeModeStore(Files.createTempDirectory("safe_mode").resolve("store").toFile())
    private val now = 1_000_000L
    private val guard = CrashGuard(store, SilentLogger) { now }
    private val features = Feature.of(Host.SYSTEMUI)

    @Test
    fun threeCrashesWithinAMinuteHoldEveryFeatureOff() {
        listOf(20_000L, 10_000L, 0L).forEach { store.recordCrash(now - it) }

        assertEquals(features.toSet(), guard.recover(features))
        assertEquals(features.toSet(), store.disabled())
        assertFalse(store.isAnswered())
        assertTrue(store.crashes().isEmpty())
    }

    @Test
    fun crashesSpreadOverMoreThanAMinuteAreNotALoop() {
        listOf(80_000L, 40_000L, 0L).forEach { store.recordCrash(now - it) }

        assertTrue(guard.recover(features).isEmpty())
        assertTrue(store.disabled().isEmpty())
    }

    @Test
    fun onlyCrashesInsideTheWindowAreKept() {
        store.recordCrash(now - 120_000L)
        store.recordCrash(now)

        assertEquals(listOf(now), store.crashes())
    }

    @Test
    fun keepingFeaturesOffStopsTheAskingAndRestoringThemRestarts() {
        repeat(3) { store.recordCrash(now) }
        guard.recover(features)
        var restarted = false
        var changes = 0
        val controller = SafeModeController(store, SilentLogger, onChanged = { changes++ }, restart = { restarted = true })

        controller.answer(SafeModeAnswer.KEEP_DISABLED)
        assertTrue(store.isAnswered())
        assertEquals(features.toSet(), store.disabled())
        assertFalse(restarted)

        controller.answer(SafeModeAnswer.RESTORE)
        assertTrue(store.disabled().isEmpty())
        assertTrue(restarted)
        assertEquals(2, changes)
    }

    @Test
    fun theMessageNamesWhatWasHeldOffAndWhy() {
        assertEquals(
            "Depth effect was automatically disabled because SystemUI crashed repeatedly after it was enabled.",
            SafeModeText.message(Host.SYSTEMUI, listOf(Feature.DEPTH_EFFECT)),
        )
        assertEquals(
            "Clock styles and Depth effect were automatically disabled because SystemUI crashed repeatedly after they were enabled.",
            SafeModeText.message(Host.SYSTEMUI, listOf(Feature.CLOCK_STYLES, Feature.DEPTH_EFFECT)),
        )
    }

    private object SilentLogger : Logger {
        override fun info(message: String) = Unit
        override fun warn(message: String, error: Throwable?) = Unit
    }
}
