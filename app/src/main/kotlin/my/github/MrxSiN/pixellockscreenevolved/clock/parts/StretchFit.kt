package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import kotlin.math.max
import kotlin.math.min

/**
 * The text size and vertical stretch that draw a line of numerals a wanted
 * height tall without passing a wanted width.
 *
 * The line is set as large as the height and the room across both allow, and
 * whatever height the room across does not leave comes from drawing the
 * numerals taller than the font designs them, up to the limit its strokes
 * stand. Kept free of Android so the arithmetic can be unit tested.
 */
internal object StretchFit {

    /** The text size to set, and how much taller than that to draw the numerals. */
    data class Fit(val textSize: Float, val stretch: Float)

    /**
     * How to draw numerals [numeralHeight] tall in no more than [maxWidth].
     *
     * [numeralPerTextSize] is the numeral height per pixel of text size and
     * [widthPerTextSize] the line's width per pixel of text size, both measured
     * unstretched; [maxStretch] is how much taller than designed the numerals
     * may be drawn.
     */
    fun of(
        numeralHeight: Float,
        maxWidth: Float,
        numeralPerTextSize: Float,
        widthPerTextSize: Float,
        maxStretch: Float,
    ): Fit {
        if (numeralPerTextSize <= 0f) return Fit(textSize = 0f, stretch = 1f)
        val byHeight = numeralHeight / numeralPerTextSize
        val byWidth = if (widthPerTextSize > 0f) maxWidth / widthPerTextSize else byHeight
        val textSize = min(byHeight, byWidth)
        if (textSize <= 0f) return Fit(textSize = 0f, stretch = 1f)
        val stretch = min(max(1f, maxStretch), max(1f, numeralHeight / (textSize * numeralPerTextSize)))
        return Fit(textSize = textSize, stretch = stretch)
    }
}
