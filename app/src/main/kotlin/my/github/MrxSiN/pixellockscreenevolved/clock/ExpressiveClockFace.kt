package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face that answers the lock screen's moments with motion of its own, as
 * Google's clocks do on Android 17.
 *
 * Kept apart from [ClockFace] so a face with nothing to play is never made to
 * answer for these.
 */
interface ExpressiveClockFace : ClockFace {

    /** The lock screen is appearing, as the screen wakes or after a notification leaves. */
    fun enter()

    /** The charger has been plugged in. */
    fun charge()
}
