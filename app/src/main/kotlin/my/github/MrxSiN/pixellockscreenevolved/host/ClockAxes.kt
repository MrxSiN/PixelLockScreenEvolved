package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.RobotoFlexNumerals

/**
 * The axes this module stores in the clock setting, and what they mean.
 *
 * The picker keeps a clock's choices as axis values in the setting it writes
 * and hands them back to the clock unchanged, so a width and a size ride along
 * as two axes. Kept free of Android so the mapping can be unit tested.
 */
internal object ClockAxes {

    /** Axis keys. Renaming one silently resets that choice on every device. */
    const val WIDTH_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_WIDTH"
    const val SIZE_KEY = "PIXEL_LOCK_SCREEN_EVOLVED_SIZE"

    /** Stops on the size slider, the smallest size first. */
    const val SIZE_STEPS = 6

    /**
     * Stops on the picker's "Clock face width" slider, the widest and lightest
     * first, as Google's flex clock orders its own seven
     * ([my.github.MrxSiN.pixellockscreenevolved.clock.ios.RobotoFlexNumerals]).
     */
    const val WIDTH_STEPS = RobotoFlexNumerals.WIDTH_STOPS

    /** The width a clock that never stored one is drawn at. */
    const val DEFAULT_WIDTH_STEP = RobotoFlexNumerals.DEFAULT_WIDTH_STOP

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
     * them, so every stored setting carries it. The width a person chooses is
     * kept apart, under [WIDTH_KEY], so no choice of it moves that line.
     */
    const val WIDTH_AXIS_KEY = "wdth"
    const val WIDE_CLOCK_WIDTH = 120f

    /** The 0 to 1 size for a stored size step; a clock that never stored one is smallest. */
    fun sizeOf(step: Float?): Float =
        (step ?: 0f).coerceIn(0f, (SIZE_STEPS - 1).toFloat()) / (SIZE_STEPS - 1)

    /** The width step a stored width value chooses; a clock that never stored one is at [DEFAULT_WIDTH_STEP]. */
    fun widthStepOf(value: Float?): Int =
        (value?.toInt() ?: DEFAULT_WIDTH_STEP).coerceIn(0, WIDTH_STEPS - 1)

    /**
     * The 0 to 1 width for a stored width value, where 0 is the narrowest cut
     * and 1 the widest. The slider runs the other way, its first stop the
     * widest, so dragging it moves this clock the way it moves Google's.
     */
    fun widthOf(value: Float?): Float = 1f - widthStepOf(value).toFloat() / (WIDTH_STEPS - 1)

    /** The axes one preset on the picker's width slider holds. */
    fun widthPreset(step: Int): Map<String, Float> =
        linkedMapOf(WIDTH_KEY to step.toFloat(), WIDTH_AXIS_KEY to WIDE_CLOCK_WIDTH)

    /** The axes of the width preset at [step] with the size step [sizeStep] chosen as well. */
    fun sizedWidthPreset(step: Int, sizeStep: Float): Map<String, Float> =
        widthPreset(step) + (SIZE_KEY to sizeStep)
}
