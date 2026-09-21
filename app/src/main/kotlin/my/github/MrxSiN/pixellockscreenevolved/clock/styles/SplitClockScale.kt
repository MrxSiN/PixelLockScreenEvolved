package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectFrame

/**
 * How far down the screen the Split time reaches.
 *
 * The time is one line stretched down the lock screen until it meets the
 * photo's subject, and a little past it, so the subject stands in front of the
 * ends of the numerals rather than beside them. The room it may claim is what
 * lies between the top of the numerals and the subject's own ink; the size
 * chosen in Wallpaper & style says how much of that room it takes. With no
 * subject the numerals reach the middle of the screen, where a photo's subject
 * usually begins, and so too where the subject leaves almost nothing to reach:
 * a photo whose subject fills the screen from its top is a background to stand
 * the time over, not something to stand the time above. Kept free of Android so
 * the arithmetic can be unit tested.
 */
internal object SplitClockScale {

    /** How far the numerals reach past the subject's top, against the subject's own height. */
    private const val OVERLAP = 0.16f

    /** Room the numerals reach with no subject to measure against, against the screen's height. */
    private const val CENTRED_ROOM_TO_HEIGHT = 0.42f

    /** Room a subject must leave, against the screen's height, to be worth standing the time above. */
    private const val LEAST_ROOM_TO_HEIGHT = 0.2f

    /** Share of the room the numerals claim, at the smallest size and at the largest. */
    private const val SHORTEST_SHARE = 0.6f
    private const val TALLEST_SHARE = 1f

    /** Shortest and tallest the numerals may be however much room there is, against the screen's short side. */
    private const val SHORTEST = 0.22f
    private const val TALLEST = 1.25f

    /**
     * The room below the numerals' top, in the face's own pixels, for numerals
     * that start [aboveNumerals] below the face's top: what [subject] leaves
     * above it, or the centred room where there is no subject or it leaves too
     * little of the screen to stand above.
     */
    fun roomFor(subject: SubjectFrame?, aboveNumerals: Float, screenHeight: Float): Float {
        val centred = screenHeight * CENTRED_ROOM_TO_HEIGHT
        val above = subject ?: return centred
        val room = above.top - aboveNumerals + above.height * OVERLAP
        return if (room >= screenHeight * LEAST_ROOM_TO_HEIGHT) room else centred
    }

    /**
     * How tall the numerals are drawn at [size], from 0 for the smallest to 1
     * for the largest, given the [room] below them and the screen's [shortSide].
     */
    fun numeralHeight(size: Float, room: Float, shortSide: Float): Float {
        val share = SHORTEST_SHARE + (TALLEST_SHARE - SHORTEST_SHARE) * size.coerceIn(0f, 1f)
        return (room * share).coerceIn(shortSide * SHORTEST, shortSide * TALLEST)
    }
}
