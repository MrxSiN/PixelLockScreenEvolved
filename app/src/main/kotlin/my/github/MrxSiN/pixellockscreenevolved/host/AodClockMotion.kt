package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * The order of the large clock's motion between the lock screen and the
 * always-on display, as the doze fraction runs from 0 awake to 1 dozing.
 *
 * Where SystemUI's reveal scrim cannot be read ([ScrimReveal], which the depth
 * effect follows otherwise), which way the display is going decides when the
 * subject and the time trade places, each over the first [TRADE_BY] of the move: going to sleep, the time
 * comes out in front of the subject before the photo has dimmed, so the time is
 * never seen cut by a subject that has faded away; waking, the subject is back
 * in front by the time the photo has come up, so the time slides into its place
 * behind it. One order both ways left one of them wrong: the time slid in over
 * the subject and sank behind it after (waking), or stayed cut by the subject
 * as the photo dimmed to black (going to sleep).
 *
 * SystemUI owns the always-on position, including the room it keeps around the
 * fingerprint sensor; the awake placement is handed to it from [PLACED_BY] on. Kept free of Android so the order can be unit tested.
 */
internal object AodClockMotion {

    /** The share of a move, from its start, over which the subject and the time trade places. */
    const val TRADE_BY = 0.3f

    /** How far into the doze the awake placement starts going to SystemUI. */
    const val PLACED_BY = 0.3f

    /**
     * How far the subject still stands in front of the time, from 1 awake to 0 dozing, as the display is
     * [waking] or going to sleep.
     */
    fun subjectInFront(doze: Float, waking: Boolean): Float =
        1f - smoothstep(if (waking) (doze - (1f - TRADE_BY)) / TRADE_BY else doze / TRADE_BY)

    /** How far placement belongs to SystemUI, from 0 at the awake pin to 1 at its always-on position. */
    fun towardAodPosition(doze: Float): Float = smoothstep((doze - PLACED_BY) / (1f - PLACED_BY))

    /** Inner-view correction left after SystemUI takes ownership of always-on placement. */
    fun placementCorrection(awakeCorrection: Float, doze: Float): Float =
        awakeCorrection * (1f - towardAodPosition(doze))

    private fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
