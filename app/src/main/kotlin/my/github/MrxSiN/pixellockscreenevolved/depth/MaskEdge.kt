package my.github.MrxSiN.pixellockscreenevolved.depth

/**
 * How opaque the subject is drawn for a segmentation confidence. Kept free of
 * Android so it can be unit tested.
 */
internal object MaskEdge {

    /** Below this confidence a pixel is background. */
    private const val FROM = 0.4f

    /** From this confidence a pixel is wholly subject. */
    private const val TO = 0.6f

    /** Alpha from 0 to 1 for [confidence], eased across the band between [FROM] and [TO]. */
    fun alphaOf(confidence: Float): Float {
        val t = ((confidence - FROM) / (TO - FROM)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
