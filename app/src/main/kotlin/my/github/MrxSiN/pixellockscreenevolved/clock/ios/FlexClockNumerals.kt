package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Typeface

import kotlin.math.min

/**
 * The numerals of the Pixel's own lock screen clock, which the iOS time is set
 * in, and how their cut follows the width chosen in Wallpaper & style.
 *
 * The font is the one SystemUI's default clock, the first in the picker's list,
 * loads (`DefaultClockProvider`: `Typeface.create("google-sans-flex-clock",
 * NORMAL)`), so the iOS face draws the same figures, only on one line.
 *
 * The "Clock face width" slider moves the same seven cuts as Google's flex
 * clock (`FlexClockController.BASE_PRESETS`): the numerals grow heavier as
 * they narrow and lighter as they widen, from `wght` 200 at `wdth` 120 to
 * `wght` 800 at `wdth` 30, with `ROND` and `slnt` at 0. `opsz` is left to the
 * platform, as SystemUI leaves it.
 */
internal object FlexClockNumerals {

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

    val typeface: Typeface = Typeface.create("google-sans-flex-clock", Typeface.NORMAL)

    /** The figures SystemUI's clock sets (`FontTextStyleImpl.fontFeatureSettings`): the proportional ones, drawn for the clock. */
    const val FEATURES: String = "pnum"

    /** Font variation settings at [width], from 0 for the narrowest and heaviest stop to 1 for the widest and lightest. */
    fun variationFor(width: Float): String {
        val cut = cutAt(width)
        return "'wght' ${cut.weight}, 'wdth' ${cut.width}, 'ROND' 0, 'slnt' 0"
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
