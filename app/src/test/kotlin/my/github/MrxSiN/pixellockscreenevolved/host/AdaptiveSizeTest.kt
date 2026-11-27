package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class AdaptiveSizeTest {

    /**
     * An 8 by 8 grid over an 800 by 800 cut-out: a head whose crown rises to row 3 in the middle
     * columns, and shoulders filling rows 6 and 7 across.
     */
    private val portrait = SubjectInk(
        8, 8,
        BooleanArray(64) { i ->
            val x = i % 8
            val y = i / 8
            (y >= 3 && x in 3..4) || y >= 6
        },
        800, 800,
    )

    @Test
    fun theEdgeIsWhereTheSubjectFirstRisesIntoTheClocksColumns() {
        // Over the middle, the crown.
        assertEquals(300f, portrait.topEdge(200f, 600f, 0f, 800f, 0.08f), 0.001f)
        // Off to the side the crown is missed: the shoulders.
        assertEquals(600f, portrait.topEdge(0f, 200f, 0f, 800f, 0.08f), 0.001f)
        // Searched from below the crown, the next row of the head.
        assertEquals(400f, portrait.topEdge(200f, 600f, 400f, 800f, 0.08f), 0.001f)
        // A sliver of subject in a wide band is not enough when more is asked for.
        assertEquals(600f, portrait.topEdge(0f, 800f, 0f, 800f, 0.5f), 0.001f)
        // None above where the search stops.
        assertTrue(portrait.topEdge(200f, 600f, 0f, 250f, 0.08f).isNaN())
    }

    @Test
    fun atTheTopTheSubjectStandsInFrontOfTheFootOfTheFace() {
        val height = AdaptiveSize.height(edge = 900f, bandTop = 100f, bandBottom = 2000f, share = 0f)
        // The edge lands the overlap's share of the face above its bottom.
        assertEquals(900f, 100f + height * (1f - AdaptiveSize.OVERLAP), 0.01f)
    }

    @Test
    fun placedLowerTheFaceStillMeetsTheEdgeAsItsTopRisesWithItsSize() {
        val share = 0.3f
        val height = AdaptiveSize.height(edge = 1500f, bandTop = 100f, bandBottom = 2000f, share = share)
        val top = ClockPosition.topAt(share, 100f, 2000f, height)
        assertEquals(1500f, top + height * (1f - AdaptiveSize.OVERLAP), 0.01f)
    }

    @Test
    fun noEdgeNoSizeAndAnEdgeAboveTheBandGivesTheSmallest() {
        assertTrue(AdaptiveSize.height(Float.NaN, 100f, 2000f, 0f).isNaN())
        assertEquals(0f, AdaptiveSize.height(edge = 50f, bandTop = 100f, bandBottom = 2000f, share = 0f), 0f)
        // Stood so low the edge cannot be met, the band bounds it.
        assertEquals(1900f, AdaptiveSize.height(edge = 1990f, bandTop = 100f, bandBottom = 2000f, share = 0.9f), 0f)
    }
}
