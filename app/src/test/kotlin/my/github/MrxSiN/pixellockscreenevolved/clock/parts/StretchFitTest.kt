package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import org.junit.Assert.assertEquals
import org.junit.Test

class StretchFitTest {

    private val numeralPerTextSize = 0.7f

    @Test
    fun aLineWithRoomAcrossIsSetAtItsHeightAndNotStretched() {
        val fit = StretchFit.of(
            numeralHeight = 700f,
            maxWidth = 5000f,
            numeralPerTextSize = numeralPerTextSize,
            widthPerTextSize = 2f,
            maxStretch = 3f,
        )
        assertEquals(1000f, fit.textSize, 1e-3f)
        assertEquals(1f, fit.stretch, 0f)
    }

    @Test
    fun aLineTheWidthCapsIsStretchedToItsHeight() {
        val fit = StretchFit.of(
            numeralHeight = 700f,
            maxWidth = 1000f,
            numeralPerTextSize = numeralPerTextSize,
            widthPerTextSize = 2f,
            maxStretch = 3f,
        )
        assertEquals(500f, fit.textSize, 1e-3f)
        assertEquals(700f, fit.textSize * numeralPerTextSize * fit.stretch, 1e-3f)
    }

    @Test
    fun aLineIsNeverStretchedPastItsLimit() {
        val fit = StretchFit.of(
            numeralHeight = 7000f,
            maxWidth = 1000f,
            numeralPerTextSize = numeralPerTextSize,
            widthPerTextSize = 2f,
            maxStretch = 3f,
        )
        assertEquals(3f, fit.stretch, 1e-5f)
    }

    @Test
    fun aLineThatMayNotStretchIsSimplyShorter() {
        val fit = StretchFit.of(
            numeralHeight = 700f,
            maxWidth = 1000f,
            numeralPerTextSize = numeralPerTextSize,
            widthPerTextSize = 2f,
            maxStretch = 1f,
        )
        assertEquals(500f, fit.textSize, 1e-3f)
        assertEquals(1f, fit.stretch, 0f)
    }

    @Test
    fun anUnmeasurableLineAsksForNothing() {
        val fit = StretchFit.of(700f, 1000f, numeralPerTextSize = 0f, widthPerTextSize = 2f, maxStretch = 3f)
        assertEquals(0f, fit.textSize, 0f)
        assertEquals(1f, fit.stretch, 0f)
    }
}
