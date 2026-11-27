package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Where a large clock of this module's stands on the lock screen while awake,
 * as the clock setting stores it: [share], how far down the band the clock may
 * stand in it is placed, from 0 at the top to 1 at the bottom
 * ([ClockAxes.POSITION_SHARE_KEY]), and whether it is [adaptive] instead
 * ([ClockAxes.POSITION_KEY]), in which case [AdaptivePlacement] picks one of
 * the [ANCHORS] for the photo; and how far it is turned about its middle,
 * [rotation] degrees clockwise ([ClockAxes.ROTATION_KEY], [ClockRotation]),
 * whether placed or adaptive. [share] is kept while adaptive, so turning
 * Adaptive off puts the clock back where it was dragged.
 *
 * A turned clock is placed by the box it covers turned
 * ([ClockRotation.extentHeight]), so its corners stay in the band: the share
 * puts that box's middle, which turning never moves, between the band's ends.
 *
 * The band runs from under the status bar to above the fingerprint sensor and
 * the smartspace card that follows the clock; a share of it, not a pixel, is
 * stored, so a position holds on every screen size and resolution. The
 * always-on display keeps SystemUI's own position whatever is chosen
 * ([AodClockMotion]), and the small clock beside notifications is never
 * moved. Kept free of Android so it can be unit tested.
 */
internal data class ClockPosition(val share: Float, val adaptive: Boolean = false, val rotation: Float = 0f) {

    /** The top of a face [height] tall at [share] of the band from [bandTop] to [bandBottom]. */
    fun topIn(bandTop: Float, bandBottom: Float, height: Float): Float = topAt(share, bandTop, bandBottom, height)

    /** What the clock setting stores for [adaptive]. */
    val modeValue: Float get() = if (adaptive) ADAPTIVE_MODE else PLACED_MODE

    companion object {
        /** Where these clocks always stood, and what a setting without a position means. */
        val TOP = ClockPosition(0f)

        /**
         * The shares a dragged clock snaps to and an adaptive clock chooses
         * between, highest first: the top, the quarters, the bottom.
         */
        val ANCHORS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        /** What each of [ANCHORS] is called when TalkBack announces where a dragged clock landed. */
        private val ANCHOR_NAMES = arrayOf("Top", "Upper", "Middle", "Lower", "Bottom")

        /** The name of the anchor [share] is on, or how far down it is as a percentage between them. */
        fun nameOf(share: Float): String {
            for (i in ANCHORS.indices) if (kotlin.math.abs(ANCHORS[i] - share) < NAME_TOLERANCE) return ANCHOR_NAMES[i]
            return "${kotlin.math.round(share * PERCENT).toInt()}% down"
        }

        private const val NAME_TOLERANCE = 0.005f
        private const val PERCENT = 100f
        private const val PLACED_MODE = 0f
        private const val ADAPTIVE_MODE = 1f

        /**
         * The position stored [mode], [share] and [rotation] axis values choose; anything this build does
         * not know reads as placed, and a rotation out of range as the nearest one in it.
         */
        fun of(mode: Float?, share: Float?, rotation: Float? = null): ClockPosition = ClockPosition(
            (share ?: 0f).coerceIn(0f, 1f),
            adaptive = mode == ADAPTIVE_MODE,
            rotation = ClockRotation.clamp(rotation ?: 0f),
        )

        /**
         * The top of a face [height] tall at [share] of the band from [bandTop] to [bandBottom], where,
         * turned, it covers [extent] of height ([ClockRotation.extentHeight]). The face turns about its
         * middle, so this is the top of the face upright, as laid out.
         */
        fun topAt(share: Float, bandTop: Float, bandBottom: Float, height: Float, extent: Float = height): Float {
            val middle = bandTop + extent / 2 + share.coerceIn(0f, 1f) * (bandBottom - bandTop - extent).coerceAtLeast(0f)
            return middle - height / 2
        }

        /** The share of the band a face [height] tall, covering [extent] turned, whose top is at [top] stands at, unclamped. */
        fun shareAt(top: Float, bandTop: Float, bandBottom: Float, height: Float, extent: Float = height): Float {
            val free = bandBottom - bandTop - extent
            return if (free > 0f) (top + height / 2 - extent / 2 - bandTop) / free else 0f
        }
    }
}

/**
 * Adaptive size: a large clock grown down to the wallpaper's subject, as
 * the depth effect is made for. The subject's top edge in the clock's columns
 * ([SubjectInk.topEdge]) lands [OVERLAP] of the face's height above its
 * bottom, so the subject stands in front of the foot of the time and the rest
 * of it reads clear; the smallest and largest sizes of the face's style still
 * bound it. Kept free of Android so it can be unit tested.
 */
internal object AdaptiveSize {

    /** How much of the face's height, from its bottom, the subject stands in front of. */
    const val OVERLAP = 0.18f

    /** The share of the clock's columns the subject must fill in a row for that row to be its top edge. */
    const val EDGE_SHARE = 0.08f

    /** Below this, the clock stands so low that whatever its size, its foot reaches the band's bottom first. */
    private const val LEAST_SPAN = 0.05f

    /**
     * The upright height, in the band's pixels, of a face standing at [share] of the band from [bandTop]
     * to [bandBottom] ([ClockPosition.topAt]) whose height puts [edge] [OVERLAP] of it above its bottom;
     * the band's height where it stands too low for that, and NaN with no edge. The face's top moves up as
     * it grows at any share but 0, which this takes in, so the answer does not depend on the face's
     * present height and fitting to it never feeds back into itself.
     */
    fun height(edge: Float, bandTop: Float, bandBottom: Float, share: Float): Float {
        if (edge.isNaN()) return Float.NaN
        val band = bandBottom - bandTop
        if (band <= 0f) return Float.NaN
        val s = share.coerceIn(0f, 1f)
        // top(h) = bandTop + s (band - h); the edge stands at top(h) + (1 - OVERLAP) h.
        val span = 1f - OVERLAP - s
        if (span < LEAST_SPAN) return band
        return ((edge - bandTop - s * band) / span).coerceIn(0f, band)
    }
}

/**
 * How a large clock is turned ([ClockPosition.rotation]): the box it covers
 * turned, the height a face that fills the screen down may take so it still
 * fits once turned, the angles a turning finger is drawn to, and how an angle
 * is said. Degrees, clockwise positive, as `View.setRotation` takes them.
 */
internal object ClockRotation {

    /** As far as the clock turns either way: on its side, reading up or down the screen. */
    const val MAX_DEGREES = 90f

    /** The angles a turning finger or the slider is drawn to, with a tick: upright, the common tilts, on its side. */
    val DETENTS = floatArrayOf(-90f, -45f, -30f, -15f, 0f, 15f, 30f, 45f, 90f)

    /** Within this many degrees of a detent, a turn settles on it. */
    const val DETENT_PULL = 4f

    /** Below this cosine the face is near enough on its side that its upright height no longer limits its turned height. */
    private const val SIDEWAYS_COS = 0.2f

    /** The least share of its room a filling face keeps, however far it is turned. */
    private const val LEAST_ROOM = 0.35f

    /** [degrees] within the range a clock turns; 0 for anything that is not a number. */
    fun clamp(degrees: Float): Float = if (degrees.isNaN()) 0f else degrees.coerceIn(-MAX_DEGREES, MAX_DEGREES)

    /** The height a face [width] by [height] covers turned [degrees]. */
    fun extentHeight(width: Float, height: Float, degrees: Float): Float {
        // Upright, as almost every face is: no trigonometry before each frame.
        if (degrees == 0f) return height
        val radians = Math.toRadians(degrees.toDouble())
        return (width * abs(sin(radians)) + height * abs(cos(radians))).toFloat()
    }

    /** The width a face [width] by [height] covers turned [degrees]. */
    fun extentWidth(width: Float, height: Float, degrees: Float): Float = extentHeight(height, width, degrees)

    /**
     * The upright height a face [width] wide may take for its turned box to stay within [room]: the room
     * itself upright, less what the width takes turned. A face nearly on its side is limited by its
     * width, not its height, and keeps its room.
     */
    fun uprightRoom(room: Float, width: Float, degrees: Float): Float {
        if (degrees == 0f) return room
        val radians = Math.toRadians(degrees.toDouble())
        val cos = abs(cos(radians)).toFloat()
        if (cos < SIDEWAYS_COS) return room
        val sin = abs(sin(radians)).toFloat()
        return ((room - width * sin) / cos).coerceIn(room * LEAST_ROOM, room)
    }

    /** The detent within [pull] degrees of [degrees], or [degrees] itself. */
    fun snap(degrees: Float, pull: Float = DETENT_PULL): Float = detentOf(degrees, pull).let { if (it < 0) degrees else DETENTS[it] }

    /** The index into [DETENTS] of the one nearest [degrees] within [pull] degrees, or -1. */
    fun detentOf(degrees: Float, pull: Float = DETENT_PULL): Int {
        var nearest = -1
        for (i in DETENTS.indices) {
            val off = abs(DETENTS[i] - degrees)
            if (off <= pull && (nearest < 0 || off < abs(DETENTS[nearest] - degrees))) nearest = i
        }
        return nearest
    }

    /** How [degrees] is said: "Upright", or how far and which way the clock leans. */
    fun nameOf(degrees: Float): String {
        val whole = round(degrees).toInt()
        return when {
            whole == 0 -> "Upright"
            whole > 0 -> "Turned $whole degrees right"
            else -> "Turned ${-whole} degrees left"
        }
    }

    /** How [degrees] is written beside the slider and a turning finger: "0\u00b0", "15\u00b0", "\u221230\u00b0". */
    fun label(degrees: Float): String {
        val whole = round(degrees).toInt()
        return if (whole < 0) "\u2212${-whole}\u00b0" else "$whole\u00b0"
    }
}

/**
 * Chooses the anchor for an adaptive clock: the one where the face hides least
 * of the photo's subject, given how much of the face's box the subject covers
 * at each anchor.
 *
 * The subject over the top half of the face counts for more than over its
 * bottom half ([weigh]), and a little of it costs nothing ([FREE_OVERLAP]).
 * Each step down from the top costs a little, so with no subject, or one that
 * fills the screen, the clock stays where these clocks always stood, and the
 * time stays readable (the depth effect brings it out in front of a subject
 * that would hide most of it). The anchor already shown is kept unless another
 * is better by [HYSTERESIS], so a photo scrolled or recropped a little never
 * makes the clock jump between two that are nearly as good.
 */
internal object AdaptivePlacement {

    /** How much less of the subject another anchor must cover before the clock moves to it. */
    const val HYSTERESIS = 0.1f

    /** What each step down from the top costs, against a share of the face covered. */
    const val STEP_DOWN_COST = 0.04f

    /**
     * The index into [ClockPosition.ANCHORS] to show, where [covered] holds
     * the share of the face's box the subject covers at each anchor, and
     * [shown] is the index shown now, or -1 before any is.
     */
    fun choose(covered: FloatArray, shown: Int): Int {
        var best = 0
        for (i in 1 until covered.size) if (cost(covered, i) < cost(covered, best)) best = i
        if (shown in covered.indices && cost(covered, shown) - cost(covered, best) < HYSTERESIS) return shown
        return best
    }

    private fun cost(covered: FloatArray, index: Int): Float = (covered[index] - FREE_OVERLAP).coerceAtLeast(0f) + index * STEP_DOWN_COST

    /**
     * A little of the subject over the face is what the depth effect is for: a head rising into the
     * foot of the digits. Up to this much of the face covered costs nothing.
     */
    const val FREE_OVERLAP = 0.08f

    /** How much the top half of the face weighs against the bottom: the subject over the digits' tops hides more of how they read. */
    private const val TOP_WEIGHT = 0.65f

    /**
     * The share of the face's box covered, weighing the subject over its [top] half
     * more than over its [bottom] half; an even cover of both is that cover.
     */
    fun weigh(top: Float, bottom: Float): Float = TOP_WEIGHT * top + (1f - TOP_WEIGHT) * bottom
}
