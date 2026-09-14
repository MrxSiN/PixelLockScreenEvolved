package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAxesTest {

    @Test
    fun sizeStepsRunFromSmallestToLargest() {
        assertEquals(0f, ClockAxes.sizeOf(0f), 0f)
        assertEquals(0.4f, ClockAxes.sizeOf(2f), 1e-6f)
        assertEquals(1f, ClockAxes.sizeOf((ClockAxes.SIZE_STEPS - 1).toFloat()), 0f)
    }

    @Test
    fun missingOrOutOfRangeSizesAreClamped() {
        assertEquals(0f, ClockAxes.sizeOf(null), 0f)
        assertEquals(0f, ClockAxes.sizeOf(-4f), 0f)
        assertEquals(1f, ClockAxes.sizeOf(99f), 0f)
    }

    @Test
    fun fontIndexIsClampedToTheFontsOffered() {
        assertEquals(0, ClockAxes.fontIndexOf(null, fontCount = 2))
        assertEquals(1, ClockAxes.fontIndexOf(1f, fontCount = 2))
        assertEquals(1, ClockAxes.fontIndexOf(7f, fontCount = 2))
        assertEquals(0, ClockAxes.fontIndexOf(1f, fontCount = 1))
    }

    @Test
    fun fontPresetsDeclareAWideClock() {
        val preset = ClockAxes.fontPreset(1)
        assertEquals(1f, preset.getValue(ClockAxes.FONT_KEY), 0f)
        assertTrue(preset.getValue(ClockAxes.WIDTH_AXIS_KEY) >= 110f)
    }

    @Test
    fun sizedFontPresetsKeepTheFontAndAddTheSize() {
        val preset = ClockAxes.sizedFontPreset(1, 3f)
        assertEquals(ClockAxes.fontPreset(1), preset - ClockAxes.SIZE_KEY)
        assertEquals(3f, preset.getValue(ClockAxes.SIZE_KEY), 0f)
    }
}
