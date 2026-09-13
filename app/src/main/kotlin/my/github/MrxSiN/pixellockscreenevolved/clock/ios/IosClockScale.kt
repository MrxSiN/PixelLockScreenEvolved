package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import kotlin.math.max
import kotlin.math.min

/**
 * How the iOS numerals grow from the smallest size to the largest.
 *
 * A larger size is a taller clock set in a bolder, more condensed cut of
 * Roboto Flex: the weight rises and the width narrows along the font's own
 * designed axes, so every cut keeps even strokes. The time is set as large as
 * the height and the screen width allow, and a little of the height may come
 * from stretching the numerals vertically. The stretch is capped, because past
 * it a diagonal such as the stem of a 7 reads visibly thinner than a bar; the
 * horizontal strokes are thinned by as much as the stretch thickens them.
 * Kept free of Android so the curve can be unit tested.
 */
internal object IosClockScale {

    /** Numeral height of the smallest clock, against the screen's short side. */
    private const val SMALLEST_NUMERAL_TO_SHORT_SIDE = 0.30f

    /** Numeral height of the largest clock, against the screen's short side. */
    private const val LARGEST_NUMERAL_TO_SHORT_SIDE = 0.88f

    /** Widest the time may be, against the screen's short side. */
    private const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f

    /** Most the numerals are ever stretched; past it strokes stop looking even. */
    private const val MAX_STRETCH = 1.6f

    private const val SMALLEST_WEIGHT = 600f
    private const val LARGEST_WEIGHT = 700f
    private const val SMALLEST_WIDTH = 100f
    private const val LARGEST_WIDTH = 25f

    /** Roboto Flex horizontal stroke as the font draws it, and the thinnest it goes. */
    private const val HORIZONTAL_STROKE = 79f
    private const val THINNEST_HORIZONTAL_STROKE = 25f

    /**
     * Font variation settings for the time at [size], from 0 for the smallest
     * to 1 for the largest, drawn [stretch] times taller than designed.
     *
     * `opsz` is always set: left out, Android sets it from the text size, which
     * would change the strokes with size.
     */
    fun variationFor(size: Float, stretch: Float): String {
        val t = size.coerceIn(0f, 1f)
        val weight = lerp(SMALLEST_WEIGHT, LARGEST_WEIGHT, t)
        val width = lerp(SMALLEST_WIDTH, LARGEST_WIDTH, t)
        val horizontal = max(THINNEST_HORIZONTAL_STROKE, HORIZONTAL_STROKE / max(1f, stretch))
        return "'opsz' 144, 'wght' $weight, 'wdth' $width, 'YOPQ' $horizontal"
    }

    /**
     * Text size and vertical stretch for the time at [size].
     *
     * [numeralPerTextSize] is the numeral height per pixel of text size and
     * [widthPerTextSize] the width of this particular time per pixel of text
     * size, both measured unstretched in the cut [variationFor] gives [size].
     */
    fun fit(size: Float, shortSide: Float, numeralPerTextSize: Float, widthPerTextSize: Float): Fit {
        val t = size.coerceIn(0f, 1f)
        val numeral = shortSide * lerp(SMALLEST_NUMERAL_TO_SHORT_SIDE, LARGEST_NUMERAL_TO_SHORT_SIDE, t)

        val byHeight = numeral / numeralPerTextSize
        val byWidth = shortSide * MAX_WIDTH_TO_SHORT_SIDE / widthPerTextSize
        val textSize = min(byHeight, byWidth)
        val stretch = (numeral / (textSize * numeralPerTextSize)).coerceIn(1f, MAX_STRETCH)
        return Fit(textSize = textSize, stretch = stretch)
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

    /** The text size to set, and how much taller than that to draw the numerals. */
    data class Fit(val textSize: Float, val stretch: Float)
}
