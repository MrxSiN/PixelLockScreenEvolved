package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.Color

/** The accent HyperOS's layered styles set part of the time in ([HeadlineClockStyle], [PosterClockStyle]). */
internal object HyperColors {

    /** The wallpaper's tertiary accent, as Material You derives it, or white where the system has none. */
    fun accent(context: Context): Int =
        runCatching { context.getColor(android.R.color.system_accent3_200) }.getOrDefault(Color.WHITE)

    /** [accent] awake, easing to the clock's own [color] as the display dozes, which settles its colour for the always-on display. */
    fun accented(color: Int, accent: Int, doze: Float): Int {
        val t = doze.coerceIn(0f, 1f)
        if (t == 0f) return accent
        if (t == 1f) return color
        fun channel(shift: Int): Int {
            val from = (accent ushr shift) and 0xff
            return (from + (((color ushr shift) and 0xff) - from) * t + 0.5f).toInt() shl shift
        }
        return channel(24) or channel(16) or channel(8) or channel(0)
    }
}
