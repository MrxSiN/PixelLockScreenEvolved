package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    private val shortSide = 1000f
    private val numeral = 0.7f

    @Test
    fun aNarrowTimeGrowsWithoutStretching() {
        val fit = IosClockScale.fit(size = 0f, shortSide, numeral, widthPerTextSize = 1f, maxStretch = 1.6f)
        assertEquals(300f / numeral, fit.textSize, 1e-3f)
        assertEquals(1f, fit.stretch, 0f)
    }

    @Test
    fun aWideTimeIsStretchedOnlyUpToTheFontsLimit() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widthPerTextSize = 2.3f, maxStretch = 1.6f)
        assertEquals(920f / 2.3f, fit.textSize, 1e-3f)
        assertEquals(1.6f, fit.stretch, 0f)
    }

    @Test
    fun aWideTimeStillGrowsAtEveryStep() {
        val heights = (0..5).map { step ->
            val fit = IosClockScale.fit(size = step / 5f, shortSide, numeral, widthPerTextSize = 3f, maxStretch = 1.6f)
            fit.textSize * numeral * fit.stretch
        }
        heights.zipWithNext().forEach { (smaller, larger) -> assert(larger > smaller) { "$heights" } }
        assertEquals(920f / 3f * numeral * 1.6f, heights.last(), 1e-2f)
    }

    @Test
    fun aTimeThatNeedsLittleStretchGetsExactlyThat() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widthPerTextSize = 1.1f, maxStretch = 1.6f)
        assertEquals(880f, fit.textSize * numeral * fit.stretch, 1e-2f)
    }
}
