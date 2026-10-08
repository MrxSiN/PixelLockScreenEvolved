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
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine

/**
 * The iOS clock: the date and weather over a large, centred time.
 *
 * Both SystemUI faces are this drawing, shown at the size and width the host
 * decides; [showsInfo] leaves the row out where SystemUI
 * writes its own. The date keeps its classic size however large the time
 * grows, as on iOS. On the always-on display the time is drawn in outline, so
 * its wide strokes do not keep lighting the same pixels.
 */
internal class IosClockFace(
    private val context: Context,
    showsInfo: Boolean,
) : ResizableClockFace, AdjustableWidthClockFace, EdgeToEdgeClockFace, DozingClockFace, WeatherClockFace {

    override val timeView = IosTimeView(context)

    private val info: DateLine? = if (showsInfo) DateLine(context, DATE) else null

    override val view: View = WideFace(context).apply {
        orientation = LinearLayout.VERTICAL
        // The small face is given the band the lock screen lays its own small clock out in, and sits in the middle of it.
        gravity = if (showsInfo) Gravity.CENTER_HORIZONTAL else Gravity.CENTER
        layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
        info?.let { addView(it.view, LinearLayout.LayoutParams(WRAP, WRAP)) }
        addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    override val drawsDate: Boolean = showsInfo

    override val isChanging: Boolean get() = timeView.isChanging

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
        info?.refresh()
    }

    override fun setWeather(weather: ClockWeather?) {
        info?.setWeather(weather)
    }

    override fun setColor(color: Int) {
        timeView.setColor(color)
        info?.setColor(color)
    }

    override fun setDoze(fraction: Float) {
        timeView.setOutline(fraction)
    }

    override fun setSize(size: Float) {
        timeView.setSize(size)
    }

    override fun setDrawnScale(scale: Float) {
        timeView.setDrawnScale(scale)
    }

    override fun setWidth(width: Float) {
        timeView.setWidth(width)
    }

    /**
     * Spaces the rows around the time by the lock screen's one row gap,
     * whatever its size: the large face's date row above it, with room above
     * that from the status bar; below, the smartspace card is spaced by the
     * lock screen itself, and SystemUI's date row under the small face by room
     * added under it.
     */
    private fun spaceInfoFromTime() {
        val row = info?.view
        if (row == null) {
            val bottom = dp(IosClockTypography.SMALL_BOTTOM_DP).roundToInt()
            view.setPadding(0, 0, 0, bottom)
            // The scene lowers the small band by all it grows, so the time is lifted back by the room added under it.
            view.translationY = -bottom.toFloat()
            return
        }
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
            if (translationX != shift) translationX = shift
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** The classic iOS date: "Saturday, September 13", a little translucent. */
        val DATE = DateLine.Look(
            skeleton = IosClockTypography.DATE_SKELETON,
            sizeToShortSide = 0.045f,
            weight = 600,
            alpha = IosClockTypography.DATE_ALPHA,
        )
    }
}
