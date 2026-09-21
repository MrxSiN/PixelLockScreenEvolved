package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face whose width a person can choose, as Wallpaper & style's "Clock face
 * width" slider chooses it for Google's clocks.
 *
 * Kept apart from [ClockFace] so a style with one width is never made to answer
 * for widths it does not have.
 */
interface AdjustableWidthClockFace : ClockFace {

    /** Redraws at [width], from 0 for the style's narrowest to 1 for its widest. */
    fun setWidth(width: Float)

    companion object {
        /** The width a face is drawn at until one is chosen: halfway. */
        const val DEFAULT_WIDTH: Float = 0.5f
    }
}
