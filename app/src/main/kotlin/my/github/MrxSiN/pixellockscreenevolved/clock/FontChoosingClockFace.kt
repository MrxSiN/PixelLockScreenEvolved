package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face whose font a person can choose.
 *
 * Kept apart from [ClockFace] so a style with one font is never made to answer
 * for fonts it does not have.
 */
interface FontChoosingClockFace : ClockFace {

    /** Redraws in the font at [index] of its style's [ClockStyle.fontNames]. */
    fun setFont(index: Int)
}
