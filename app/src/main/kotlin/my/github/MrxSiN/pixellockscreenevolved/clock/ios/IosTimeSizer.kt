package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView

/**
 * Sizes the large time the way iOS resizes its lock screen clock.
 *
 * The smallest size is the classic iOS clock. Growing it makes the numerals
 * taller; once they would run past the screen's edges they narrow on the
 * font's width axis instead of shrinking, so the largest size is a tall wall of
 * digits across the top of the screen.
 */
internal class IosTimeSizer(metrics: DisplayMetrics) {

    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val longSide = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()

    /** Text size of the classic iOS clock, which is also this module's smallest. */
    val smallestTextSize: Float = shortSide * SMALLEST_TEXT_TO_SHORT_SIDE

    private val largestTextSize = longSide * LARGEST_TEXT_TO_LONG_SIDE
    private val maxWidth = shortSide * MAX_WIDTH_TO_SHORT_SIDE
    private val bounds = Rect()

    /**
     * Applies [size], from 0 to 1, to [time] as it reads now. Called again
     * whenever the text changes, because "1:11" and "12:58" narrow differently.
     */
    fun apply(time: TextView, size: Float) {
        val textSize = smallestTextSize + (largestTextSize - smallestTextSize) * size.coerceIn(0f, 1f)
        time.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        fitWidth(time)
        hugNumerals(time, textSize)
    }

    private fun fitWidth(time: TextView) {
        time.textScaleX = 1f
        time.fontVariationSettings = IosClockTypography.timeVariation()
        val natural = measure(time)
        if (natural <= maxWidth) return

        val width = (IosClockTypography.NORMAL_WIDTH * maxWidth / natural)
            .coerceAtLeast(IosClockTypography.NARROWEST_WIDTH)
        time.fontVariationSettings = IosClockTypography.timeVariation(width)

        // The width axis ends before every face fits; the last of it is squeezed.
        val narrowed = measure(time)
        if (narrowed > maxWidth) time.textScaleX = maxWidth / narrowed
    }

    /**
     * Trims the line box to the numerals. A font's line is sized for accents
     * and descenders the time never has, and at the largest sizes that empty
     * space would push the time far below the date. TextView only ever pads a
     * line, so the surplus is taken back with negative margins.
     */
    private fun hugNumerals(time: TextView, textSize: Float) {
        val params = time.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val metrics = time.paint.fontMetrics
        time.paint.getTextBounds(DIGITS, 0, DIGITS.length, bounds)

        val gap = (textSize * GAP_TO_TEXT).toInt()
        params.topMargin = (metrics.ascent - bounds.top).toInt() + gap
        params.bottomMargin = (bounds.bottom - metrics.descent).toInt() + gap
        time.layoutParams = params
    }

    private fun measure(time: TextView): Float = time.paint.measureText(time.text.toString())

    private companion object {
        const val DIGITS = "0123456789"
        const val SMALLEST_TEXT_TO_SHORT_SIDE = 0.26f
        const val LARGEST_TEXT_TO_LONG_SIDE = 0.5f
        const val MAX_WIDTH_TO_SHORT_SIDE = 0.92f
        const val GAP_TO_TEXT = 0.04f
    }
}
