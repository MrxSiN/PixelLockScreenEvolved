package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * Where the photo's subject stands, in screen pixels: [left] and [right] from
 * the screen's own edge, [top] and [bottom] from the top of the face as the
 * lock screen pins it awake, so a subject that begins above the face reads as a
 * negative [top].
 *
 * The face is never told where it is on screen or how large it came out: the
 * host works the frame out from the wallpaper's placement and the clock's pin
 * alone, so that a face laying itself out around the subject never moves the
 * frame it is laying itself out against.
 */
data class SubjectFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * A face laid out around the photo's subject, so the time frames the subject
 * rather than standing wherever it would have stood.
 */
interface SubjectAwareClockFace : ClockFace {

    /**
     * Lays the face out around [subject], or around a subject taken to stand in
     * the middle of the screen where there is none: the depth effect is off, no
     * subject was found, or none has been found yet.
     */
    fun setSubject(subject: SubjectFrame?)
}
