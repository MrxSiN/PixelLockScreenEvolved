package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class CircleRevealTest {

    @Test
    fun theCircleReachesTheFurthestCorner() {
        assertEquals(1600f, CircleReveal.endRadius(100f, 400f, 1000, 2000), 0f)
        assertEquals(2000f, CircleReveal.endRadius(0f, 0f, 1000, 2000), 0f)
    }

    @Test
    fun theOutsideStaysDarkUntilHalfRevealed() {
        assertEquals(1f, CircleReveal.endColorAlpha(0f), 0f)
        assertEquals(1f, CircleReveal.endColorAlpha(0.5f), 0f)
        assertEquals(0.5f, CircleReveal.endColorAlpha(0.75f), 0f)
        assertEquals(0f, CircleReveal.endColorAlpha(1f), 0f)
    }
}
