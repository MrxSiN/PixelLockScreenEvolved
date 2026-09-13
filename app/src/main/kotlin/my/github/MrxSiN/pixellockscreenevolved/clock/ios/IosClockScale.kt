package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import kotlin.math.max
import kotlin.math.min

/**
 * How the iOS numerals grow from the smallest size to the largest.
 *
 * The font keeps its shape at every size: the same weight, the same width and
 * the same stroke. A larger size is a taller clock, not a thinner one. The time
 * is set as large as the height and the screen width allow, and whatever height
 * the width does not allow is reached by stretching the numerals vertically.
 * Stretching would thicken the horizontal strokes by the same factor, so those
 * are thinned beforehand on Roboto Flex's horizontal-stroke axis and every
 * stroke keeps its weight. Kept free of Android so the curve can be unit tested.
 */
internal object IosClockScale {

    /** Numeral height of the smallest clock, against the screen's short side. */
    private const val SMALLEST_NUMERAL_TO_SHORT_SIDE = 0.30f

    /** Numeral height of the largest clock, against the screen's short side. */
    private const val LARGEST_NUMERAL_TO_SHORT_SIDE = 0.88f

    /** Widest the time may be, against the screen's short side. */
    private const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f

    /** Roboto Flex horizontal stroke as the font draws it, and the thinnest it goes. */
    private const val HORIZONTAL_STROKE = 79f
    private const val THINNEST_HORIZONTAL_STROKE = 25f

    /**
     * The font's shape, fixed at every size. `opsz` is always set: left out,
     * Android sets it from the text size, which changes the stroke with size.
     */
    private const val SHAPE = "'opsz' 144, 'wght' 600, 'wdth' 100, 'XTRA' 468, 'XOPQ' 96"

    /**
     * Font variation settings for numerals drawn [stretch] times taller than
     * designed: the fixed shape, with horizontal strokes thinned by the stretch.
     */
    fun variationFor(stretch: Float): String {
        val horizontal = max(THINNEST_HORIZONTAL_STROKE, HORIZONTAL_STROKE / max(1f, stretch))
        return "$SHAPE, 'YOPQ' $horizontal"
    }

    /**
     * Text size and vertical stretch for the time at [size], from 0 for the
     * smallest to 1 for the largest.
     *
     * [numeralPerTextSize] is the numeral height per pixel of text size and
     * [widthPerTextSize] the width of this particular time per pixel of text
     * size, both as the font draws them unstretched.
     */
    fun fit(size: Float, shortSide: Float, numeralPerTextSize: Float, widthPerTextSize: Float): Fit {
        val t = size.coerceIn(0f, 1f)
        val numeral = shortSide * (SMALLEST_NUMERAL_TO_SHORT_SIDE +
            (LARGEST_NUMERAL_TO_SHORT_SIDE - SMALLEST_NUMERAL_TO_SHORT_SIDE) * t)

        val byHeight = numeral / numeralPerTextSize
        val byWidth = shortSide * MAX_WIDTH_TO_SHORT_SIDE / widthPerTextSize
        val textSize = min(byHeight, byWidth)
        return Fit(textSize = textSize, stretch = max(1f, numeral / (textSize * numeralPerTextSize)))
    }

    /** The text size to set, and how much taller than that to draw the numerals. */
    data class Fit(val textSize: Float, val stretch: Float)
}
