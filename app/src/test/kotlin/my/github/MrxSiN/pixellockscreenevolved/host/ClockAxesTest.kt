package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockAxesTest {

    @Test
    fun sizeStepsRunFromSmallestToLargest() {
        assertEquals(0f, ClockAxes.sizeOf(0f, 6), 0f)
        assertEquals(0.4f, ClockAxes.sizeOf(2f, 6), 1e-6f)
        assertEquals(1f, ClockAxes.sizeOf(5f, 6), 0f)
        assertEquals(1f, ClockAxes.sizeOf(3f, 4), 0f)
    }

    @Test
    fun missingOrOutOfRangeSizesAreClamped() {
        assertEquals(0f, ClockAxes.sizeOf(null, 6), 0f)
        assertEquals(0f, ClockAxes.sizeOf(-4f, 6), 0f)
        assertEquals(1f, ClockAxes.sizeOf(99f, 6), 0f)
        // A step stored for a longer slider is the largest of a shorter one.
        assertEquals(1f, ClockAxes.sizeOf(5f, 4), 0f)
    }

    /** The slider's first stop is the widest and lightest cut and its last the narrowest, as Google's is. */
    @Test
    fun widthStepsRunFromWidestToNarrowest() {
        assertEquals(1f, ClockAxes.widthOf(0f), 0f)
        assertEquals(0f, ClockAxes.widthOf((ClockAxes.WIDTH_STEPS - 1).toFloat()), 0f)
    }

    @Test
    fun theSliderHasTheSameStopsAsGooglesOwn() {
        assertEquals(7, ClockAxes.WIDTH_STEPS)
        assertEquals(3, ClockAxes.DEFAULT_WIDTH_STEP)
    }

    @Test
    fun missingWidthIsTheMiddleAndOutOfRangeWidthsAreClamped() {
        assertEquals(ClockAxes.DEFAULT_WIDTH_STEP, ClockAxes.widthStepOf(null))
        assertEquals(0.5f, ClockAxes.widthOf(null), 0f)
        assertEquals(0, ClockAxes.widthStepOf(-3f))
        assertEquals(ClockAxes.WIDTH_STEPS - 1, ClockAxes.widthStepOf(40f))
    }

    @Test
    fun widthPresetsDeclareAWideClockWhateverTheWidthChosen() {
        repeat(ClockAxes.WIDTH_STEPS) { step ->
            val preset = ClockAxes.widthPreset(step)
            assertEquals(step.toFloat(), preset.getValue(ClockAxes.WIDTH_KEY), 0f)
            assertTrue(preset.getValue(ClockAxes.WIDTH_AXIS_KEY) >= 110f)
        }
    }

    @Test
    fun widthPresetsKeepTheWidthAndAddWhatElseWasChosen() {
        val preset = ClockAxes.widthPresetWith(1, mapOf(ClockAxes.SIZE_KEY to 3f, ClockAxes.POSITION_KEY to 2f))
        assertEquals(ClockAxes.widthPreset(1), preset - ClockAxes.SIZE_KEY - ClockAxes.POSITION_KEY)
        assertEquals(3f, preset.getValue(ClockAxes.SIZE_KEY), 0f)
        assertEquals(2f, preset.getValue(ClockAxes.POSITION_KEY), 0f)
    }
}
