package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import kotlin.math.min

/**
 * How tall the iOS time is at each size, whatever font it is set in.
 *
 * A larger size is a taller clock. The time is set as large as that height and
 * the screen width allow, and whatever height the width does not allow comes
 * from stretching the numerals vertically, up to the font's own limit. The
 * sizes are spread between the smallest and the tallest the font can reach for
 * this time, so every step is taller than the last. Kept free of Android so the
 * arithmetic can be unit tested.
 */
internal object IosClockScale {

    /** Numeral height of the smallest clock, against the screen's short side. */
    private const val SMALLEST_NUMERAL_TO_SHORT_SIDE = 0.30f

    /** Numeral height of the largest clock, against the screen's short side. */
    private const val LARGEST_NUMERAL_TO_SHORT_SIDE = 0.88f

    /** Widest the time may be, against the screen's short side. */
    private const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f

    /**
     * Text size and vertical stretch for the time at [size], from 0 for the
     * smallest to 1 for the largest.
     *
     * [numeralPerTextSize] is the numeral height per pixel of text size and
     * [widthPerTextSize] the width of this particular time per pixel of text
     * size, both measured unstretched; [maxStretch] is the font's limit.
     */
    fun fit(
        size: Float,
        shortSide: Float,
        numeralPerTextSize: Float,
        widthPerTextSize: Float,
        maxStretch: Float,
    ): Fit {
        val byWidth = shortSide * MAX_WIDTH_TO_SHORT_SIDE / widthPerTextSize
        val tallest = min(shortSide * LARGEST_NUMERAL_TO_SHORT_SIDE, byWidth * numeralPerTextSize * maxStretch)
        val smallest = min(shortSide * SMALLEST_NUMERAL_TO_SHORT_SIDE, tallest)
        val numeral = smallest + (tallest - smallest) * size.coerceIn(0f, 1f)

        val textSize = min(numeral / numeralPerTextSize, byWidth)
        val stretch = (numeral / (textSize * numeralPerTextSize)).coerceIn(1f, maxStretch)
        return Fit(textSize = textSize, stretch = stretch)
    }

    /** The text size to set, and how much taller than that to draw the numerals. */
    data class Fit(val textSize: Float, val stretch: Float)
}
