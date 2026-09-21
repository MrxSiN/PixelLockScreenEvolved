package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * The order of the large clock's motion between the lock screen and the
 * always-on display, as the doze fraction runs from 0 awake to 1 dozing.
 *
 * Going to sleep, the time first comes out in front of the depth effect's
 * subject, and only then gives its awake placement back to SystemUI. SystemUI
 * owns the always-on position, including the room it keeps around the
 * fingerprint sensor. Waking runs the same fractions backwards: the time
 * returns from that position first, and goes behind the subject once it is in
 * place. Kept free of Android so the order can be unit tested.
 */
internal object AodClockMotion {

    /** The share of the doze spent bringing the time out in front of the subject, before it moves. */
    const val IN_FRONT_BY = 0.3f

    /** How far the subject still stands in front of the time, from 1 awake to 0 once the time has come out. */
    fun subjectInFront(doze: Float): Float = 1f - smoothstep(doze / IN_FRONT_BY)

    /** How far placement belongs to SystemUI, from 0 at the awake pin to 1 at its always-on position. */
    fun towardAodPosition(doze: Float): Float = smoothstep((doze - IN_FRONT_BY) / (1f - IN_FRONT_BY))

    /** Inner-view correction left after SystemUI takes ownership of always-on placement. */
    fun placementCorrection(awakeCorrection: Float, doze: Float): Float =
        awakeCorrection * (1f - towardAodPosition(doze))

    private fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
