package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * The size steps a resizable style offers in Wallpaper & style.
 *
 * The picker draws a stepped slider for a clock that lists axis presets, and
 * stores the chosen preset in the clock setting's axes. Each step here is one
 * preset holding a single axis, and a stored value is turned back into the 0 to
 * 1 size a [my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace]
 * takes. Kept free of Android so the mapping can be unit tested.
 */
internal object ClockSizePresets {

    /**
     * Axis key written to the clock setting. Renaming it silently resets every
     * chosen size to the smallest.
     */
    const val AXIS_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_SIZE"

    /** Stops on the slider, the smallest size first. */
    const val STEPS = 7

    /** The stored value of each step, in slider order. */
    val values: List<Float> = List(STEPS) { it.toFloat() }

    /** The size for a stored [value]; a clock that never stored one is smallest. */
    fun sizeOf(value: Float?): Float =
        (value ?: 0f).coerceIn(0f, values.last()) / values.last()
}
