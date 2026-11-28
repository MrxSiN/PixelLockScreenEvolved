package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AodClockMotionTest {

    @Test
    fun awakeTheSubjectStandsInFrontAndTheTimeIsInPlace() {
        assertEquals(1f, AodClockMotion.subjectInFront(0f, waking = true), 0f)
        assertEquals(0f, AodClockMotion.towardAodPosition(0f), 0f)
        assertEquals(24f, AodClockMotion.placementCorrection(24f, 0f), 0f)
    }

    @Test
    fun dozingTheTimeIsOutInFrontAndAtSystemUisPosition() {
        assertEquals(0f, AodClockMotion.subjectInFront(1f, waking = false), 0f)
        assertEquals(1f, AodClockMotion.towardAodPosition(1f), 0f)
        assertEquals(0f, AodClockMotion.placementCorrection(24f, 1f), 0f)
    }

    @Test
    fun goingToSleepTheTimeComesOutEarlyAndWakingItGoesBehindEarly() {
        val by = AodClockMotion.TRADE_BY
        assertEquals("going to sleep, out by the first share", 0f, AodClockMotion.subjectInFront(by, waking = false), 0f)
        assertEquals("waking, back in front by the first share of the wake", 1f, AodClockMotion.subjectInFront(1f - by, waking = true), 0f)
        assertEquals(0.5f, AodClockMotion.subjectInFront(by / 2, waking = false), 1e-6f)
        assertEquals(0f, AodClockMotion.subjectInFront(1f, waking = true), 0f)
        assertEquals(1f, AodClockMotion.subjectInFront(0f, waking = false), 0f)
    }
}
