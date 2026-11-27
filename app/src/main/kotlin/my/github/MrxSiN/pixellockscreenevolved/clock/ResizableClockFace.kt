package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face whose size a person can choose.
 *
 * Kept apart from [ClockFace] so a style that has one size is never made to
 * answer for sizes it does not have.
 */
interface ResizableClockFace : ClockFace {

    /** Redraws at [size], from 0 for the style's smallest to 1 for its largest. */
    fun setSize(size: Float)

    /**
     * Lays the face out at [size] now, without the motion [setSize] may play, so it can be measured
     * there (adaptive size tries several sizes in one go). A face that never animates its size needs
     * nothing more than [setSize].
     */
    fun setSizeAtOnce(size: Float) = setSize(size)
}
