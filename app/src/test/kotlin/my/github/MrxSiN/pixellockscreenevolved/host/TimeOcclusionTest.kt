package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeOcclusionTest {

    private val width = 100
    private val height = 20

    /**
     * "9:41" as four solid boxes: three digits 20 wide and a colon 4 wide and short,
     * at x 5, 30 (colon), 40 and 70.
     */
    private val digits = listOf(5 until 25, 40 until 60, 70 until 90)
    private val colon = 30 until 34

    private fun time(strokes: Boolean = false): ByteArray = ByteArray(width * height) { i ->
        val x = i % width
        val y = i / width
        val inDigit = digits.any { x in it } && y in 2 until 18 && (!strokes || x % 5 == 0 || y % 5 == 0)
        val inColon = x in colon && (y in 5 until 8 || y in 12 until 15)
        if (inDigit || inColon) 255.toByte() else 0
    }

    private fun erased(drawn: ByteArray, xs: IntRange): ByteArray =
        drawn.copyOf().also { left -> for (i in left.indices) if (i % width in xs) left[i] = 0 }

    private val balanced = TimeOcclusion.Conditions(DepthOcclusion.BALANCED, contrast = null, accessible = false)

    @Test
    fun theTimeIsReadAsDigitsGroupedAroundTheColon() {
        val reading = TimeOcclusion.read(time(), time(), width, height)!!
        assertEquals(3, reading.digits.size)
        assertEquals(2, reading.groups.size)
        assertEquals(0f, reading.hidden, 0f)
    }

    @Test
    fun aSubjectHidingOneWholeDigitSendsItBehindThoughLittleInkIsHidden() {
        val drawn = time()
        val reading = TimeOcclusion.read(drawn, erased(drawn, 0 until 26), width, height)!!
        assertTrue("only a third of the ink is hidden: ${reading.hidden}", reading.hidden < 0.4f)
        assertEquals(0f, reading.digits[0], 0f)
        assertFalse(TimeOcclusion.inFront(reading, balanced, wasInFront = false))
    }

    @Test
    fun aSubjectNibblingEveryDigitALittleStaysInFront() {
        val drawn = time()
        val left = drawn.copyOf().also { for (i in it.indices) if (i / width >= 15) it[i] = 0 }
        val reading = TimeOcclusion.read(drawn, left, width, height)!!
        assertTrue(TimeOcclusion.inFront(reading, balanced, wasInFront = false))
    }

    @Test
    fun theAnswerHoldsNearTheLine() {
        val drawn = time()
        // 40% of the first digit hidden: inside the hysteresis band either side of the bar.
        val reading = TimeOcclusion.read(drawn, erased(drawn, 5 until 13), width, height)!!
        assertTrue(TimeOcclusion.inFront(reading, balanced, wasInFront = true))
        assertFalse(TimeOcclusion.inFront(reading, balanced, wasInFront = false))
    }

    @Test
    fun thinStrokesLowContrastAndAccessibilityRaiseTheBar() {
        val heavy = TimeOcclusion.barFor(balanced, strokeDensity = 0.5f)
        val thin = TimeOcclusion.barFor(balanced, strokeDensity = 0.1f)
        val faint = TimeOcclusion.barFor(balanced.copy(contrast = 1.5f), strokeDensity = 0.5f)
        val accessible = TimeOcclusion.barFor(balanced.copy(accessible = true), strokeDensity = 0.5f)
        assertTrue(thin.leastDigit > heavy.leastDigit)
        assertTrue(faint.mostHidden < heavy.mostHidden)
        assertTrue(accessible.leastGroup > heavy.leastGroup)
        assertTrue(TimeOcclusion.read(time(strokes = true), time(strokes = true), width, height)!!.strokeDensity < 0.5f)
    }

    @Test
    fun depthFirstAlwaysStandsInFront() {
        val drawn = time()
        val reading = TimeOcclusion.read(drawn, ByteArray(drawn.size), width, height)!!
        assertEquals(1f, reading.hidden, 0f)
        assertTrue(TimeOcclusion.inFront(reading, balanced.copy(mode = DepthOcclusion.DEPTH), wasInFront = false))
    }

    @Test
    fun stackedLinesAreTheHourOverTheMinute() {
        val stacked = ByteArray(width * height * 2) { i ->
            val x = i % width
            val y = i / width
            if ((x in 10 until 40 || x in 50 until 80) && (y in 2 until 18 || y in 22 until 38)) 255.toByte() else 0
        }
        val left = stacked.copyOf().also { for (i in it.indices) if (i / width < 20) it[i] = 0 }
        val reading = TimeOcclusion.read(stacked, left, width, height * 2)!!
        assertEquals(2, reading.groups.size)
        assertEquals(0f, reading.groups[0], 0f)
        assertEquals(1f, reading.groups[1], 0f)
    }

    @Test
    fun nothingDrawnHasNothingToKeepReadable() {
        assertEquals(null, TimeOcclusion.read(ByteArray(10), ByteArray(10), 5, 2))
        assertTrue(TimeOcclusion.inFront(null, balanced, wasInFront = false))
    }

    @Test
    fun contrastIsTheWcagRatio() {
        assertEquals(21f, TimeOcclusion.contrast(1f, 0f), 0.01f)
        assertEquals(1f, TimeOcclusion.contrast(0.3f, 0.3f), 0f)
        assertEquals(1f, LumaGrid.luminance(0xffffffff.toInt()), 0.001f)
    }
}
