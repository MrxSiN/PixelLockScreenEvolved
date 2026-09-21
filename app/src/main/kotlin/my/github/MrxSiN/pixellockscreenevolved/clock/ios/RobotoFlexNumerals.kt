package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Typeface

import kotlin.math.max
import kotlin.math.min

/**
 * Roboto Flex, the system font the iOS time is set in, and how its cut follows
 * the width chosen in Wallpaper & style.
 *
 * The "Clock face width" slider does not move the width axis alone. Google's
 * own flex clock steps through seven cuts in which the numerals grow heavier as
 * they narrow and lighter as they widen, so every stop keeps about the same ink
 * on screen: read from `FlexClockController.BASE_PRESETS`, they run from
 * `wght` 200 at `wdth` 120 to `wght` 800 at `wdth` 30. The same seven are used
 * here, so this clock's slider moves as the stock one does rather than fading a
 * fixed weight across a wider range of widths than the font is drawn for.
 *
 * Its horizontal-stroke axis takes back what a vertical stretch adds
 * ([IosClockScale]). `opsz` is always set: left out, Android sets it from the
 * text size, which would change the strokes with size.
 */
internal object RobotoFlexNumerals {

    /** One stop of the width slider: how heavy the numerals are and how wide. */
    private data class Cut(val weight: Float, val width: Float)

    /**
     * The stops, narrowest first, as `width` runs from 0 to 1. The slider itself
     * runs the other way ([my.github.MrxSiN.pixellockscreenevolved.host.ClockAxes]),
     * as Google's does, so its first stop is the widest and lightest.
     */
    private val CUTS = listOf(
        Cut(weight = 800f, width = 30f),
        Cut(weight = 700f, width = 55f),
        Cut(weight = 600f, width = 80f),
        Cut(weight = 500f, width = 100f),
        Cut(weight = 400f, width = 108f),
        Cut(weight = 300f, width = 116f),
        Cut(weight = 200f, width = 120f),
    )

    /** How many stops the width slider has, so the picker offers the same as Google's. */
    const val WIDTH_STOPS: Int = 7

    /** The stop a clock that never stored a width is drawn at: `wght` 500 at the font's own `wdth` 100. */
    const val DEFAULT_WIDTH_STOP: Int = 3

    private const val HORIZONTAL_STROKE = 79f
    private const val THINNEST_HORIZONTAL_STROKE = 25f

    val typeface: Typeface = Typeface.create("roboto-flex", Typeface.NORMAL)

    /**
     * Past this a stretched diagonal, such as the stem of a 7, reads thinner than
     * a bar. Every width of one size is stretched the same, so the limit holds at
     * all of them ([IosClockScale]).
     */
    const val MAX_STRETCH: Float = 1.6f

    /** Each colon dot's distance from the numerals' middle, against their height, as iOS sets it. */
    const val COLON_DOT_OFFSET: Float = 0.2f

    /**
     * Font variation settings at [width], from 0 for the narrowest and heaviest
     * stop to 1 for the widest and lightest, for numerals drawn [stretch] times
     * taller than designed.
     */
    fun variationFor(width: Float, stretch: Float): String {
        val cut = cutAt(width)
        val horizontal = max(THINNEST_HORIZONTAL_STROKE, HORIZONTAL_STROKE / max(1f, stretch))
        return "'opsz' 144, 'wght' ${cut.weight}, 'wdth' ${cut.width}, 'YOPQ' $horizontal"
    }

    /** The cut [width] of the way along the stops, between the two it falls between. */
    private fun cutAt(width: Float): Cut {
        val along = width.coerceIn(0f, 1f) * (CUTS.size - 1)
        val first = along.toInt()
        val second = min(first + 1, CUTS.size - 1)
        val into = along - first
        return Cut(
            weight = lerp(CUTS[first].weight, CUTS[second].weight, into),
            width = lerp(CUTS[first].width, CUTS[second].width, into),
        )
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
}
