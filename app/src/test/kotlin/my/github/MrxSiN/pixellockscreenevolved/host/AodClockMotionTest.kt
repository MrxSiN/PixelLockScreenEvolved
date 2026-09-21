package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AodClockMotionTest {

    @Test
    fun awakeTheSubjectStandsInFrontAndTheTimeIsInPlace() {
        assertEquals(1f, AodClockMotion.subjectInFront(0f), 0f)
        assertEquals(0f, AodClockMotion.towardAodPosition(0f), 0f)
        assertEquals(24f, AodClockMotion.placementCorrection(24f, 0f), 0f)
    }

    @Test
    fun theTimeComesOutInFrontBeforeItMoves() {
        val out = AodClockMotion.IN_FRONT_BY
        assertEquals(0f, AodClockMotion.subjectInFront(out), 0f)
        assertEquals(0f, AodClockMotion.towardAodPosition(out), 0f)
        assertEquals(1f, AodClockMotion.towardAodPosition(1f), 0f)
        assertEquals(0f, AodClockMotion.placementCorrection(24f, 1f), 0f)
    }

    @Test
    fun wakingMovesFromTheCentreBeforeTheSubjectComesBackInFront() {
        val moving = AodClockMotion.IN_FRONT_BY + (1f - AodClockMotion.IN_FRONT_BY) / 2f
        assertEquals(0f, AodClockMotion.subjectInFront(moving), 0f)
        assertTrue(AodClockMotion.towardAodPosition(moving) > 0f)

        val layering = AodClockMotion.IN_FRONT_BY / 2f
        assertEquals(0f, AodClockMotion.towardAodPosition(layering), 0f)
        assertTrue(AodClockMotion.subjectInFront(layering) > 0f)
    }
}
