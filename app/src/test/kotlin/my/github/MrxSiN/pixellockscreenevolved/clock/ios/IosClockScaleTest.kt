package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    private val shortSide = 1000f
    private val numeral = 0.7f
    private val reference = 1.9f

    private fun width(size: Float) = IosClockScale.fit(size, shortSide, numeral, reference).textSize * reference
    private fun height(size: Float) = IosClockScale.fit(size, shortSide, numeral, reference).let { it.textSize * numeral * it.stretch }

    @Test
    fun theSmallestIsTheIosDefaultClock() {
        assertEquals(shortSide * IosClockScale.SMALLEST_WIDTH, width(0f), 1e-2f)
        assertEquals(shortSide * IosClockScale.SMALLEST_HEIGHT, height(0f), 1e-2f)
    }

    @Test
    fun theLargestIsTheTallestIos26Clock() {
        assertEquals(shortSide * IosClockScale.LARGEST_WIDTH, width(1f), 1e-2f)
        assertEquals(shortSide * IosClockScale.LARGEST_HEIGHT, height(1f), 1e-2f)
    }
}
