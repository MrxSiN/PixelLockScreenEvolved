package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face drawn differently on the always-on display, beyond the colour
 * [ClockFace.setColor] already settles for doze.
 *
 * Kept apart from [ClockFace] so a style that looks the same awake and dozing
 * is never made to answer for the always-on display.
 */
interface DozingClockFace : ClockFace {

    /** Redraws for [fraction] of the way into the always-on display, from 0 awake to 1 dozing. */
    fun setDoze(fraction: Float)
}
