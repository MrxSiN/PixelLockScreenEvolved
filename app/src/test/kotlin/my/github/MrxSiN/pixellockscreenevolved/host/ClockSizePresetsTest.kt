package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockSizePresetsTest {

    @Test
    fun stepsRunFromSmallestToLargest() {
        assertEquals(ClockSizePresets.STEPS, ClockSizePresets.values.size)
        assertEquals(0f, ClockSizePresets.sizeOf(ClockSizePresets.values.first()), 0f)
        assertEquals(1f, ClockSizePresets.sizeOf(ClockSizePresets.values.last()), 0f)
    }

    @Test
    fun stepsAreEvenlySpaced() {
        assertEquals(0.4f, ClockSizePresets.sizeOf(2f), 1e-6f)
    }

    @Test
    fun missingValueIsSmallest() {
        assertEquals(0f, ClockSizePresets.sizeOf(null), 0f)
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        assertEquals(0f, ClockSizePresets.sizeOf(-4f), 0f)
        assertEquals(1f, ClockSizePresets.sizeOf(99f), 0f)
    }

    @Test
    fun presetsDeclareAWideClock() {
        val axes = ClockSizePresets.axesOf(4f)
        assertEquals(4f, axes.getValue(ClockSizePresets.AXIS_KEY), 0f)
        assertEquals(true, axes.getValue(ClockSizePresets.WIDTH_AXIS_KEY) >= 110f)
    }
}
