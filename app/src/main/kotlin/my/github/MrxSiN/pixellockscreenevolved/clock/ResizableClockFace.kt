package my.github.MrxSiN.pixellockscreenevolved.clock

/**
 * A face whose size a person can choose.
 *
 * Kept apart from [ClockFace] so a style that has one size is never made to
 * answer for sizes it does not have.
 */
interface ResizableClockFace : ClockFace {

    /**
     * Redraws at [size], from 0 for the style's smallest to 1 for its largest.
     * A face may ignore it where SystemUI fixes its size, as it does the small
     * clock's.
     */
    fun setSize(size: Float)
}
