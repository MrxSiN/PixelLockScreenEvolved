package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Typeface

import kotlin.math.max

/**
 * A font the iOS time can be set in, and how it grows with the clock's size.
 *
 * Each font decides its own cut at a size and how far its numerals may be
 * stretched before their strokes stop looking even; [IosClockScale] does the
 * arithmetic common to all of them.
 */
internal interface IosNumeralFont {

    val typeface: Typeface

    /** The date line above the time, set to match. */
    val dateTypeface: Typeface
    val dateVariation: String

    /** Most the numerals may be stretched vertically. */
    val maxStretch: Float

    /**
     * Font variation settings at [size], from 0 for the smallest clock to 1 for
     * the largest, for numerals drawn [stretch] times taller than designed.
     */
    fun variationFor(size: Float, stretch: Float): String
}

/**
 * Roboto Flex, the system font. Larger sizes move to a bolder, condensed cut
 * along its designed weight and width axes, so strokes stay even, and its
 * horizontal-stroke axis takes back what a stretch adds.
 *
 * `opsz` is always set: left out, Android sets it from the text size, which
 * would change the strokes with size.
 */
internal object RobotoFlexNumerals : IosNumeralFont {

    private const val SMALLEST_WEIGHT = 600f
    private const val LARGEST_WEIGHT = 700f
    private const val SMALLEST_WIDTH = 100f
    private const val LARGEST_WIDTH = 25f
    private const val HORIZONTAL_STROKE = 79f
    private const val THINNEST_HORIZONTAL_STROKE = 25f

    override val typeface: Typeface = Typeface.create("roboto-flex", Typeface.NORMAL)
    override val dateTypeface: Typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 600, false)
    override val dateVariation: String = ""

    /** Past this a stretched diagonal, such as the stem of a 7, reads thinner than a bar. */
    override val maxStretch: Float = 1.6f

    override fun variationFor(size: Float, stretch: Float): String {
        val t = size.coerceIn(0f, 1f)
        val weight = lerp(SMALLEST_WEIGHT, LARGEST_WEIGHT, t)
        val width = lerp(SMALLEST_WIDTH, LARGEST_WIDTH, t)
        val horizontal = max(THINNEST_HORIZONTAL_STROKE, HORIZONTAL_STROKE / max(1f, stretch))
        return "'opsz' 144, 'wght' $weight, 'wdth' $width, 'YOPQ' $horizontal"
    }
}

/**
 * Inter, an open-source font drawn in the manner of Apple's San Francisco and
 * the closest match to the iOS lock screen clock that can be shipped.
 *
 * It has no width axis to condense, so a larger clock comes mostly from
 * stretching the numerals vertically; its weight grows with the size so the
 * upright strokes keep pace with the bars the stretch thickens.
 */
internal class InterNumerals(override val typeface: Typeface) : IosNumeralFont {

    override val dateTypeface: Typeface = typeface
    override val dateVariation: String = "'opsz' 14, 'wght' 600"

    override val maxStretch: Float = 2f

    override fun variationFor(size: Float, stretch: Float): String =
        "'opsz' 32, 'wght' ${lerp(SMALLEST_WEIGHT, LARGEST_WEIGHT, size.coerceIn(0f, 1f))}"

    companion object {
        /** Where the font sits in the module's assets. */
        const val ASSET_PATH = "fonts/InterVariable.ttf"

        private const val SMALLEST_WEIGHT = 600f
        private const val LARGEST_WEIGHT = 800f
    }
}

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
