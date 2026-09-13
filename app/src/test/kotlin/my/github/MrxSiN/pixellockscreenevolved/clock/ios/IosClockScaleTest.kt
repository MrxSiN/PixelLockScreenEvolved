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
    fun aTimeThatNeedsLittleStretchGetsExactlyThat() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widthPerTextSize = 1.1f, maxStretch = 1.6f)
        assertEquals(880f, fit.textSize * numeral * fit.stretch, 1e-2f)
    }
}
