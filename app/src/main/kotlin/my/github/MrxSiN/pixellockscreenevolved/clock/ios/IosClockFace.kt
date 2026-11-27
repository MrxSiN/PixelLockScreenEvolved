package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine

/**
 * The iOS clock: the date and weather over a large, centred time.
 *
 * Both SystemUI faces are this same drawing, shown at the size and width the
 * host decides: with notifications the small face is the iOS clock at its
 * default size, date above, as iOS keeps it. The date keeps its classic size however large the time
 * grows, as on iOS, and the always-on display keeps the time filled, as iOS
 * does.
 */
internal class IosClockFace(
    private val context: Context,
) : ResizableClockFace, AdjustableWidthClockFace, EdgeToEdgeClockFace, WeatherClockFace {

    override val timeView = IosTimeView(context)

    private val info = DateLine(context, DATE)

    override val view: View = WideFace(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
        addView(info.view, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    override val drawsDate: Boolean = true

    init {
        spaceInfoFromTime()
        setColor(Color.WHITE)
        refresh()
    }

    override fun refresh() {
        val now = Calendar.getInstance()
        val is24Hour = DateFormat.is24HourFormat(context)
        val locale = Locale.getDefault()
        timeView.setText(
            IosTimeText.format(
                hourOfDay = now[Calendar.HOUR_OF_DAY],
                minute = now[Calendar.MINUTE],
                is24Hour = is24Hour,
                locale = locale,
            ),
        )
        info.refresh()
    }

    override fun setWeather(weather: ClockWeather?) {
        info.setWeather(weather)
    }

    override fun setColor(color: Int) {
        timeView.setColor(color)
        info.setColor(color)
    }

    override fun setSize(size: Float) {
        timeView.setSize(size)
    }

    override fun setSizeAtOnce(size: Float) {
        timeView.setSizeAtOnce(size)
    }

    override fun setDrawnScale(scale: Float) {
        timeView.setDrawnScale(scale)
    }

    override fun setWidth(width: Float) {
        timeView.setWidth(width)
    }

    /**
     * Spaces the date row above the time by the lock screen's one row gap,
     * whatever its size, with room above it from the status bar; below, the
     * smartspace card is spaced by the lock screen itself.
     */
    private fun spaceInfoFromTime() {
        view.setPadding(0, dp(IosClockTypography.TOP_EXTRA_DP).roundToInt(), 0, 0)
        val params = timeView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        params.topMargin = dp(IosClockTypography.DATE_TO_TIME_DP).roundToInt()
        timeView.layoutParams = params
    }

    private fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    /**
     * The face's column, as wide and as tall as the time it holds even where that is wider
     * than the room its host lays it out in ([EdgeToEdgeClockFace]): the lock
     * screen's clock element is narrower than the screen. Drawn whole rather
     * than clipped to that room (the depth effect draws the face as one layer,
     * which ends at the face's bounds), and kept centred on it: a host that
     * puts it at its start, as the scene's view holder does, has it moved back
     * by half the overflow.
     */
    private class WideFace(context: Context) : LinearLayout(context) {
        init {
            // A rolling digit may pass a little beyond the time's own box.
            clipChildren = false
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // As tall as the time asks too: the scene's clock box is shorter than the largest sizes, and the
            // depth effect's layer, which ends at the face's bounds, would cut the numerals off at its edge.
            val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            super.onMeasure(unbounded, unbounded)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val room = (parent as? View)?.width ?: return
            val shift = if (left == 0 && width > room) (room - width) / 2f else 0f
            // Added to the translation, which the depth effect's tilt shares ([MotionParallax]).
            if (shift != centring) translationX += shift - centring
            centring = shift
        }

        private var centring = 0f
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** The iOS 26 date: "Tue Apr 1", or "Sat 13" beside the weather as iOS writes it with an inline widget, "☀️ 72°". */
        val DATE = DateLine.Look(
            skeleton = IosClockTypography.DATE_SKELETON,
            weatherSkeleton = IosClockTypography.DATE_WITH_WEATHER_SKELETON,
            temperatureUnit = false,
            sizeToShortSide = 0.045f,
            weight = 600,
            alpha = IosClockTypography.DATE_ALPHA,
        )
    }
}
