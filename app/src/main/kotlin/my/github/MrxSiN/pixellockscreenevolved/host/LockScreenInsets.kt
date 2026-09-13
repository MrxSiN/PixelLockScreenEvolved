package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue

/** Distances on the lock screen that SystemUI does not hand to a clock. */
internal object LockScreenInsets {

    /** Space between the status bar and the top of a large clock. */
    private const val LARGE_CLOCK_GAP_DP = 24f

    /** The top of a large clock pinned under the status bar, in pixels. */
    fun largeClockTop(context: Context): Int {
        val system = Resources.getSystem()
        val statusBarId = system.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId != 0) system.getDimensionPixelSize(statusBarId) else 0
        val gap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LARGE_CLOCK_GAP_DP, context.resources.displayMetrics)
        return statusBar + gap.toInt()
    }
}
