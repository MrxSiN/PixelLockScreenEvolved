package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    private val shortSide = 1000f
    private val numeral = 0.7f

    @Test
    fun aNarrowTimeGrowsWithoutStretching() {
        val fit = IosClockScale.fit(size = 0f, shortSide, numeral, widestWidthPerTextSize = 1f, maxStretch = 1.6f)
        assertEquals(300f / numeral, fit.textSize, 1e-3f)
        assertEquals(1f, fit.stretch, 0f)
    }

    @Test
    fun aWideTimeIsStretchedOnlyUpToTheFontsLimit() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widestWidthPerTextSize = 2.3f, maxStretch = 1.6f)
        assertEquals(920f / 2.3f, fit.textSize, 1e-3f)
        assertEquals(1.6f, fit.stretch, 0f)
    }

    @Test
    fun aWideTimeStillGrowsAtEveryStep() {
        val heights = (0..5).map { step ->
            val fit = IosClockScale.fit(size = step / 5f, shortSide, numeral, widestWidthPerTextSize = 3f, maxStretch = 1.6f)
            fit.textSize * numeral * fit.stretch
        }
        heights.zipWithNext().forEach { (smaller, larger) -> assert(larger > smaller) { "$heights" } }
        assertEquals(920f / 3f * numeral * 1.6f, heights.last(), 1e-2f)
    }

    @Test
    fun aWideTimeThatMayNotStretchStillGrowsAtEveryStep() {
        val heights = (0..5).map { step ->
            val fit = IosClockScale.fit(size = step / 5f, shortSide, numeral, widestWidthPerTextSize = 3f, maxStretch = 1f)
            assertEquals(1f, fit.stretch, 1e-5f)
            fit.textSize * numeral
        }
        heights.zipWithNext().forEach { (smaller, larger) -> assert(larger > smaller) { "$heights" } }
        assertEquals(920f / 3f * numeral, heights.last(), 1e-2f)
    }

    @Test
    fun aTimeThatNeedsLittleStretchGetsExactlyThat() {
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widestWidthPerTextSize = 1.1f, maxStretch = 1.6f)
        assertEquals(880f, fit.textSize * numeral * fit.stretch, 1e-2f)
    }

    /**
     * The fit is the one the widest width needs, so every width of a size draws
     * the same numerals; only the cut they are set in, and so the time's own
     * width, changes with the slider.
     */
    @Test
    fun aSizeIsTheSameHeightAtEveryWidth() {
        val widest = 2.4f
        listOf(0f, 0.4f, 1f).forEach { size ->
            val fit = IosClockScale.fit(size, shortSide, numeral, widest, maxStretch = 1.6f)
            val height = fit.textSize * numeral * fit.stretch
            listOf(1f, 1.6f, widest).forEach { width ->
                assertEquals("width $width at size $size", height, fit.textSize * numeral * fit.stretch, 1e-2f)
                assert(fit.textSize * width <= shortSide * 0.92f + 1e-2f) { "width $width at size $size overflows" }
            }
        }
    }

    @Test
    fun aNarrowerWidthDrawsANarrowerTime() {
        val widest = 2.4f
        val fit = IosClockScale.fit(size = 1f, shortSide, numeral, widest, maxStretch = 1.6f)
        val widths = listOf(1f, 1.6f, widest).map { fit.textSize * it }
        widths.zipWithNext().forEach { (narrower, wider) -> assert(wider > narrower) { "$widths" } }
        assertEquals(shortSide * 0.92f, widths.last(), 1e-2f)
    }
}
