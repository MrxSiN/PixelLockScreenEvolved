package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import kotlin.math.min

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.StretchFit

/**
 * How tall the iOS time is at each size, and how large it is set to leave the
 * width its own work.
 *
 * A larger size is a taller clock. The heights are spread between the smallest
 * and the tallest the time can reach at the font's widest width, so every step
 * is taller than the last and a size is the same height at every width. The
 * text size is the one the widest width would need, so the same size fits the
 * screen however wide the time is set, and a narrower width draws a narrower
 * time rather than the same block in a different cut: Wallpaper & style's
 * "Clock face width" slider then moves this clock as it moves Google's. What
 * height the width cap does not allow comes from stretching the numerals
 * vertically ([StretchFit]). Kept free of Android so the arithmetic can be unit
 * tested.
 */
internal object IosClockScale {

    /** Numeral height of the smallest clock, against the screen's short side. */
    private const val SMALLEST_NUMERAL_TO_SHORT_SIDE = 0.30f

    /** Numeral height of the largest clock, against the screen's short side. */
    private const val LARGEST_NUMERAL_TO_SHORT_SIDE = 0.88f

    /**
     * The smallest clock is never more than this share of the tallest. A time so
     * wide that the screen caps it below the usual smallest height would
     * otherwise be the same height at every size.
     */
    private const val SMALLEST_TO_TALLEST = 0.6f

    /** Widest the time may be, against the screen's short side, at its widest width. */
    private const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f

    /**
     * Text size and vertical stretch for the time at [size], from 0 for the
     * smallest to 1 for the largest.
     *
     * [numeralPerTextSize] is the numeral height per pixel of text size and
     * [widestWidthPerTextSize] the width of this particular time per pixel of
     * text size at the font's widest width, both measured unstretched;
     * [maxStretch] is how much taller than designed the numerals may be drawn.
     */
    fun fit(
        size: Float,
        shortSide: Float,
        numeralPerTextSize: Float,
        widestWidthPerTextSize: Float,
        maxStretch: Float,
    ): StretchFit.Fit {
        val maxWidth = shortSide * MAX_WIDTH_TO_SHORT_SIDE
        val widestByWidth = if (widestWidthPerTextSize > 0f) maxWidth / widestWidthPerTextSize else Float.MAX_VALUE
        val tallest = min(shortSide * LARGEST_NUMERAL_TO_SHORT_SIDE, widestByWidth * numeralPerTextSize * maxStretch)
        val smallest = min(shortSide * SMALLEST_NUMERAL_TO_SHORT_SIDE, tallest * SMALLEST_TO_TALLEST)
        val numeral = smallest + (tallest - smallest) * size.coerceIn(0f, 1f)

        return StretchFit.of(
            numeralHeight = numeral,
            maxWidth = maxWidth,
            numeralPerTextSize = numeralPerTextSize,
            // Sized for the widest width, so every width of this size shares one text size.
            widthPerTextSize = widestWidthPerTextSize,
            maxStretch = maxStretch,
        )
    }
}
