package my.github.MrxSiN.pixellockscreenevolved.clock.ios

/**
 * How the iOS numerals change shape from the smallest size to the largest.
 *
 * iOS grows its lock screen clock by making the numerals taller, and the taller
 * they get the lighter and narrower they are, until the largest size is a row
 * of thin, straight-sided figures. Roboto Flex gets there in two moves: its
 * parametric axes narrow the counters and thin the strokes, and the drawing is
 * then stretched vertically. Horizontal strokes are thinned further than
 * vertical ones beforehand, so after the stretch every stroke is the same
 * weight, as on iOS. Kept free of Android so the curve can be unit tested.
 */
internal object IosClockScale {

    /**
     * Roboto Flex axis values at the smallest size, the classic iOS clock.
     *
     * `opsz` is always set: left out, Android sets it from the text size, and
     * at these sizes that is the largest optical size, whose hairline strokes
     * are nothing like iOS.
     */
    private val SMALLEST = mapOf(
        "opsz" to 144f,
        "wght" to 600f,
        "wdth" to 100f,
        "XTRA" to 468f,
        "XOPQ" to 96f,
        "YOPQ" to 79f,
    )

    /**
     * Roboto Flex axis values at the largest size, before the stretch. Found by
     * measuring rendered numerals against the largest iOS clock: strokes 4.5%
     * of the numeral height, and horizontal strokes as heavy as vertical ones
     * once stretched.
     */
    private val LARGEST = mapOf(
        "opsz" to 14f,
        "wght" to 400f,
        "wdth" to 50f,
        "XTRA" to 400f,
        "XOPQ" to 85f,
        "YOPQ" to 40f,
    )

    /** Width over height of a "9" on the largest iOS clock. */
    private const val LARGEST_NINE_ASPECT = 0.23f

    /** Text size of the smallest clock, against the screen's short side. */
    const val SMALLEST_TEXT_TO_SHORT_SIDE = 0.26f

    /** Numeral height of the largest clock, against the screen's short side. */
    const val LARGEST_NUMERAL_TO_SHORT_SIDE = 0.88f

    /** Widest the time may be, against the screen's short side. */
    const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f

    /** Font variation settings at [size], from 0 for smallest to 1 for largest. */
    fun variationAt(size: Float): String {
        val t = size.coerceIn(0f, 1f)
        return SMALLEST.keys.joinToString(", ") { axis ->
            "'$axis' ${lerp(SMALLEST.getValue(axis), LARGEST.getValue(axis), t)}"
        }
    }

    /**
     * Vertical stretch at [size] for numerals whose "9" is [naturalNineAspect]
     * wide for its height as the font draws it. The aspect narrows from the
     * font's own at the smallest size to the iOS one at the largest, and never
     * widens: a font already narrower than iOS is left unstretched.
     */
    fun stretchAt(size: Float, naturalNineAspect: Float): Float {
        val target = lerp(naturalNineAspect, LARGEST_NINE_ASPECT, size.coerceIn(0f, 1f))
        return (naturalNineAspect / target).coerceAtLeast(1f)
    }

    /** Numeral height at [size], between the two ends, in the ends' unit. */
    fun numeralHeightAt(size: Float, smallest: Float, largest: Float): Float =
        lerp(smallest, largest, size.coerceIn(0f, 1f))

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
}
