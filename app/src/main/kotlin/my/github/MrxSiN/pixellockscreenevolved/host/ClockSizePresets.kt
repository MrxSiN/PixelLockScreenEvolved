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
    const val STEPS = 6

    /**
     * The step the small clock is always shown at, the smallest on the slider.
     * The small clock shares the lock screen with notifications, so it keeps
     * one modest size whatever was chosen for the large clock.
     */
    const val SMALL_CLOCK_STEP = 0f

    /** The stored value of each step, in slider order. */
    val values: List<Float> = List(STEPS) { it.toFloat() }

    /**
     * Axis SystemUI reads to tell a wide clock. With a width of 110 or more it
     * puts its date and weather line below the small clock instead of beside
     * it, and this clock's small face is too wide to have anything beside it.
     */
    const val WIDTH_AXIS_KEY = "wdth"
    const val WIDE_CLOCK_WIDTH = 120f

    /** Every axis one step's preset stores, in the setting's own key order. */
    fun axesOf(value: Float): Map<String, Float> =
        linkedMapOf(AXIS_KEY to value, WIDTH_AXIS_KEY to WIDE_CLOCK_WIDTH)

    /** The size for a stored [value]; a clock that never stored one is smallest. */
    fun sizeOf(value: Float?): Float =
        (value ?: 0f).coerceIn(0f, values.last()) / values.last()
}
