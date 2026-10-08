package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockPositionTest {

    @Test
    fun storedValuesReadBackAndMissingOnesStandAtTheTop() {
        val dragged = ClockPosition(0.4f, adaptive = true)
        assertEquals(dragged, ClockPosition.of(dragged.modeValue, dragged.share))
        assertEquals(ClockPosition.TOP, ClockPosition.of(null, null))
        assertEquals(ClockPosition(1f), ClockPosition.of(7f, 3f))
    }

    @Test
    fun aShareIsTakenOfTheBandsFreeHeight() {
        assertEquals(100f, ClockPosition(0f).topIn(100f, 1100f, 200f), 0f)
        assertEquals(500f, ClockPosition(0.5f).topIn(100f, 1100f, 200f), 0f)
        assertEquals(900f, ClockPosition(1f).topIn(100f, 1100f, 200f), 0f)
        assertEquals(0.5f, ClockPosition.shareAt(500f, 100f, 1100f, 200f), 0f)
        // A face taller than its band never goes above the band's top.
        assertEquals(100f, ClockPosition(1f).topIn(100f, 200f, 500f), 0f)
    }

    @Test
    fun adaptiveTakesTheAnchorHidingLeastOfTheSubject() {
        assertEquals(2, AdaptivePlacement.choose(floatArrayOf(0.6f, 0.5f, 0.05f, 0.3f, 0.4f), shown = -1))
    }

    @Test
    fun adaptiveStaysAtTheTopWithoutASubjectOrWithOneEverywhere() {
        assertEquals(0, AdaptivePlacement.choose(FloatArray(5), shown = -1))
        assertEquals(0, AdaptivePlacement.choose(FloatArray(5) { 1f }, shown = -1))
    }

    @Test
    fun adaptiveKeepsTheAnchorShownUnlessAnotherIsClearlyBetter() {
        assertEquals(1, AdaptivePlacement.choose(floatArrayOf(0.5f, 0.2f, 0.15f, 0.5f, 0.5f), shown = 1))
        assertEquals(2, AdaptivePlacement.choose(floatArrayOf(0.5f, 0.6f, 0f, 0.5f, 0.5f), shown = 1))
    }

    @Test
    fun anchorsAreNamedAndOtherSharesReadAsHowFarDown() {
        assertEquals("Middle", ClockPosition.nameOf(0.5f))
        assertEquals("40% down", ClockPosition.nameOf(0.4f))
    }
}
