package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.graphics.Typeface

/**
 * One cut of Roboto Flex, the system's variable font, by its axes: how heavy,
 * how wide, how slanted and how tall its figures are. Every face picks its
 * cuts from here rather than from separate fonts, so each design comes from
 * the one font the system already carries.
 *
 * `opsz` is always set: left out, Android sets it from the text size, which
 * would change the strokes with size.
 */
data class FlexCut(
    /** `wght`, 100 to 1000. */
    val weight: Float,
    /** `wdth`, 25 for the narrowest to 151 for the widest. */
    val width: Float = 100f,
    /** `slnt`, 0 upright to -10. */
    val slant: Float = 0f,
    /** `YTFI`, the height of the figures, 560 to 788; null keeps the font's own. */
    val figureHeight: Float? = null,
) {

    val settings: String
        get() = buildString {
            append("'opsz' 144, 'wght' $weight, 'wdth' $width, 'slnt' $slant")
            figureHeight?.let { append(", 'YTFI' $it") }
        }

    /** The cut [t] of the way from this one to [to], every axis in step; 0 is this one and 1 is [to]. */
    fun toward(to: FlexCut, t: Float): FlexCut {
        if (t <= 0f) return this
        if (t >= 1f) return to
        fun lerp(from: Float, until: Float) = from + (until - from) * t
        return FlexCut(
            weight = lerp(weight, to.weight),
            width = lerp(width, to.width),
            slant = lerp(slant, to.slant),
            figureHeight = if (figureHeight != null && to.figureHeight != null) lerp(figureHeight, to.figureHeight) else figureHeight,
        )
    }

    companion object {
        val typeface: Typeface = Typeface.create("roboto-flex", Typeface.NORMAL)
    }
}
