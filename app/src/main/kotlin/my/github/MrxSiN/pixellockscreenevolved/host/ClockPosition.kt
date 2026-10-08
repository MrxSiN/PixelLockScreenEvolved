package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * Where a large clock of this module's stands on the lock screen while awake,
 * as the clock setting stores it: [share], how far down the band the clock may
 * stand in it is placed, from 0 at the top to 1 at the bottom
 * ([ClockAxes.POSITION_SHARE_KEY]), and whether it is [adaptive] instead
 * ([ClockAxes.POSITION_KEY]), in which case [AdaptivePlacement] picks one of
 * the [ANCHORS] for the photo. [share] is kept while adaptive, so turning
 * Adaptive off puts the clock back where it was dragged.
 *
 * The band runs from under the status bar to above the fingerprint sensor and
 * the smartspace card that follows the clock; a share of it, not a pixel, is
 * stored, so a position holds on every screen size and resolution. The
 * always-on display keeps SystemUI's own position whatever is chosen
 * ([AodClockMotion]), and the small clock beside notifications is never
 * moved. Kept free of Android so it can be unit tested.
 */
internal data class ClockPosition(val share: Float, val adaptive: Boolean = false) {

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

        /** The position stored [mode] and [share] axis values choose; anything this build does not know reads as placed. */
        fun of(mode: Float?, share: Float?): ClockPosition =
            ClockPosition((share ?: 0f).coerceIn(0f, 1f), adaptive = mode == ADAPTIVE_MODE)

        /** The top of a face [height] tall at [share] of the band from [bandTop] to [bandBottom]. */
        fun topAt(share: Float, bandTop: Float, bandBottom: Float, height: Float): Float =
            bandTop + share.coerceIn(0f, 1f) * (bandBottom - bandTop - height).coerceAtLeast(0f)

        /** The share of the band a face [height] tall whose top is at [top] stands at, unclamped. */
        fun shareAt(top: Float, bandTop: Float, bandBottom: Float, height: Float): Float {
            val free = bandBottom - bandTop - height
            return if (free > 0f) (top - bandTop) / free else 0f
        }
    }
}

/**
 * Chooses the anchor for an adaptive clock: the one where the face hides least
 * of the photo's subject, given how much of the face's box the subject covers
 * at each anchor.
 *
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

    private fun cost(covered: FloatArray, index: Int): Float = covered[index] + index * STEP_DOWN_COST
}
