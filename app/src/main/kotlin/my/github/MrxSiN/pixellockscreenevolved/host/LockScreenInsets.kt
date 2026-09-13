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

    /** The top of a large clock pinned under the status bar, in pixels. */
    fun largeClockTop(context: Context): Int {
        val system = Resources.getSystem()
        val statusBarId = system.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId != 0) system.getDimensionPixelSize(statusBarId) else 0
        return statusBar + dp(context, ROW_GAP_DP - STATUS_BAR_INK_ABOVE_BOTTOM_DP - DATE_INK_BELOW_TOP_DP)
    }

    /** Space between the bottom of a large clock and the top of the smartspace card below it, in pixels. */
    fun cardGap(context: Context): Int = dp(context, ROW_GAP_DP - CARD_INK_BELOW_TOP_DP).coerceAtLeast(0)

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
}
