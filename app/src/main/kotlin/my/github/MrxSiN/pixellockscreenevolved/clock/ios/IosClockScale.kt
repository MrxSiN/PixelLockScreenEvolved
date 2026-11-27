package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.StretchFit

/**
 * How large the iOS time is set, and how much it is stretched, against the
 * screen's short side, measured on Apple's own iPhone Lock Screen guide
 * screenshots (support.apple.com, `iph4d0e6c351`, 490 x 1008 images with a
 * 444 px wide screen):
 *
 * - iOS 18, default clock: "9:41" ink 200 px wide, figures 80 px tall.
 * - iOS 26, clock dragged to its tallest: "9:41" about 0.70 of the width,
 *   figures about 0.68.
 *
 * The size chosen runs between the two. The text size is set so a fixed
 * [REFERENCE] time is as wide as the size asks, so it never changes with the
 * minute and the width follows the figures, as on iOS; the stretch makes the
 * figures as tall as the size asks. Kept free of Android so the arithmetic
 * can be unit tested.
 */
internal object IosClockScale {

    /** The time the widths were measured on. */
    const val REFERENCE = "9:41"

    const val SMALLEST_WIDTH = 200f / 444f
    const val SMALLEST_HEIGHT = 80f / 444f
    const val LARGEST_WIDTH = 0.70f
    const val LARGEST_HEIGHT = 0.68f

    /**
     * The text size and stretch at [size], from 0 the iOS default to 1 the tallest, for numerals
     * [numeralPerTextSize] tall and a [REFERENCE] ink [referencePerTextSize] wide per pixel of text size.
     */
    fun fit(size: Float, shortSide: Float, numeralPerTextSize: Float, referencePerTextSize: Float): StretchFit.Fit {
        if (numeralPerTextSize <= 0f || referencePerTextSize <= 0f) return StretchFit.Fit(textSize = 0f, stretch = 1f)
        val t = size.coerceIn(0f, 1f)
        val textSize = shortSide * lerp(SMALLEST_WIDTH, LARGEST_WIDTH, t) / referencePerTextSize
        val height = shortSide * lerp(SMALLEST_HEIGHT, LARGEST_HEIGHT, t)
        return StretchFit.Fit(textSize = textSize, stretch = height / (numeralPerTextSize * textSize))
    }

    private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
}
