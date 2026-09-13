package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * The axes this module stores in the clock setting, and what they mean.
 *
 * The picker keeps a clock's choices as axis values in the setting it writes
 * and hands them back to the clock unchanged, so a font and a size ride along
 * as two axes. Kept free of Android so the mapping can be unit tested.
 */
internal object ClockAxes {

    /** Axis keys. Renaming one silently resets that choice on every device. */
    const val FONT_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_FONT"
    const val SIZE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_SIZE"

    /** Stops on the size slider, the smallest size first. */
    const val SIZE_STEPS = 6

    /**
     * The size the small clock is always shown at, the smallest. It shares the
     * lock screen with notifications, so it keeps one modest size whatever was
     * chosen for the large clock.
     */
    const val SMALL_CLOCK_SIZE_STEP = 0f

    /**
     * Axis SystemUI reads to tell a wide clock. With a width of 110 or more it
     * puts its date and weather line below the small clock instead of beside
     * it, and this module's small clocks are too wide to have anything beside
     * them, so every stored setting carries it.
     */
    const val WIDTH_AXIS_KEY = "wdth"
    const val WIDE_CLOCK_WIDTH = 120f

    /** The 0 to 1 size for a stored size step; a clock that never stored one is smallest. */
    fun sizeOf(step: Float?): Float =
        (step ?: 0f).coerceIn(0f, (SIZE_STEPS - 1).toFloat()) / (SIZE_STEPS - 1)

    /** The font index for a stored font value, among [fontCount] fonts. */
    fun fontIndexOf(value: Float?, fontCount: Int): Int =
        (value ?: 0f).toInt().coerceIn(0, (fontCount - 1).coerceAtLeast(0))

    /** The axes one preset on the picker's font slider holds. */
    fun fontPreset(index: Int): Map<String, Float> =
        linkedMapOf(FONT_KEY to index.toFloat(), WIDTH_AXIS_KEY to WIDE_CLOCK_WIDTH)
}
