package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    private val shortSide = 1000f
    private val numeral = 0.7f

    @Test
    fun unstretchedTimeKeepsTheFontShape() {
        assertEquals(
            "'opsz' 144, 'wght' 600, 'wdth' 100, 'XTRA' 468, 'XOPQ' 96, 'YOPQ' 79.0",
            IosClockScale.variationFor(stretch = 1f),
        )
    }

    @Test
    fun stretchThinsOnlyTheHorizontalStrokes() {
        assertEquals(
            "'opsz' 144, 'wght' 600, 'wdth' 100, 'XTRA' 468, 'XOPQ' 96, 'YOPQ' 39.5",
            IosClockScale.variationFor(stretch = 2f),
        )
        assertEquals(
            "'opsz' 144, 'wght' 600, 'wdth' 100, 'XTRA' 468, 'XOPQ' 96, 'YOPQ' 25.0",
            IosClockScale.variationFor(stretch = 10f),
        )
    }

    @Test
    fun aNarrowTimeGrowsWithoutStretching() {
        val fit = IosClockScale.fit(size = 0f, shortSide, numeral, widthPerTextSize = 1f)
        assertEquals(300f / numeral, fit.textSize, 1e-3f)
        assertEquals(1f, fit.stretch, 0f)
    }

    @Test
    fun aTimeTooWideForItsHeightIsStretchedNotSqueezed() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widthPerTextSize = 2.3f)
        assertEquals(920f / 2.3f, fit.textSize, 1e-3f)
        assertEquals(880f, fit.textSize * numeral * fit.stretch, 1e-2f)
    }

    @Test
    fun sizesOutsideTheRangeAreClamped() {
        assertEquals(
            IosClockScale.fit(size = 1f, shortSide, numeral, widthPerTextSize = 2f),
            IosClockScale.fit(size = 4f, shortSide, numeral, widthPerTextSize = 2f),
        )
    }
}
