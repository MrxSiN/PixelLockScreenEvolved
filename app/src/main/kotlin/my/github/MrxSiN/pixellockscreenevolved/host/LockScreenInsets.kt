package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue

/**
 * Distances on the lock screen that SystemUI does not hand to a clock.
 *
 * With the large clock, the rows down the screen are the status bar, the
 * clock's date and time, and the smartspace card, and the same space is kept
 * between the ink of each and the next. Where a row's view starts above its
 * ink, the space is shortened by that inset, measured on a Pixel 8 Pro.
 */
internal object LockScreenInsets {

    /** Space between one row's ink and the next. The iOS clock keeps it between its date and time too. */
    private const val ROW_GAP_DP = 40f

    /** How far above the status bar's bottom its icons and text end. */
    private const val STATUS_BAR_INK_ABOVE_BOTTOM_DP = 19f

    /** How far below the top of a date line its capitals start. */
    private const val DATE_INK_BELOW_TOP_DP = 16f

    /** How far below the top of SystemUI's smartspace card its first line of text starts. */
    private const val CARD_INK_BELOW_TOP_DP = 38f

    /** SystemUI's own dimension for the band its small clock is laid out in. */
    private const val SMALL_CLOCK_HEIGHT = "small_clock_height"

    /**
     * The band SystemUI lays its own small clock out in, in pixels.
     *
     * The lock screen spaces what it puts under the small clock against the
     * bottom of that band, not against the clock's ink. This module's small
     * faces are exactly as tall as their numerals ([NumeralView]), so left to
     * measure themselves they sat in a shorter band and the date row came up to
     * meet them while the notifications below it stayed where SystemUI put
     * them, leaving the rows unevenly spaced on the always-on display. Given
     * the band, the faces keep SystemUI's own spacing on both sides. A build
     * that does not name the dimension gives 0, and the face measures itself as
     * it did before.
     */
    fun smallClockHeight(context: Context): Int {
        val id = context.resources.getIdentifier(SMALL_CLOCK_HEIGHT, "dimen", context.packageName)
        return if (id != 0) context.resources.getDimensionPixelSize(id) else 0
    }

    /** The top of a large clock pinned under the status bar, in pixels. */
    fun largeClockTop(context: Context): Int {
        val system = Resources.getSystem()
        val statusBarId = system.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId != 0) system.getDimensionPixelSize(statusBarId) else 0
        return statusBar + dp(context, ROW_GAP_DP - STATUS_BAR_INK_ABOVE_BOTTOM_DP - DATE_INK_BELOW_TOP_DP)
    }

    /** Space between the bottom of a large clock and the top of SystemUI's date row moved below it, in pixels. */
    fun rowGap(context: Context): Int = dp(context, ROW_GAP_DP - DATE_INK_BELOW_TOP_DP).coerceAtLeast(0)

    /** Space between the bottom of a large clock and the top of the smartspace card below it, in pixels. */
    fun cardGap(context: Context): Int = dp(context, ROW_GAP_DP - CARD_INK_BELOW_TOP_DP).coerceAtLeast(0)

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
}
