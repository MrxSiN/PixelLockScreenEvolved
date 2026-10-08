package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face that may span the screen from edge to edge, and so must know how
 * much smaller than laid out the host draws it: the lock screen scene draws
 * its clock elements scaled down, and lays them out narrower than the screen.
 *
 * Kept apart from [ClockFace] so a face that keeps to its own box is never
 * made to answer for the scale it is drawn at.
 */
interface EdgeToEdgeClockFace : ClockFace {

    /** The host draws this face [scale] times its laid-out size while it rests. */
    fun setDrawnScale(scale: Float)

    companion object {
        /**
         * Room the face leaves between itself and either edge of the screen, as
         * drawn: the lock screen's own text margin, where the date row and the
         * smartspace card start (99 px from the edge on a Pixel 8 Pro, measured).
         */
        const val EDGE_DP: Float = 32f
    }
}
