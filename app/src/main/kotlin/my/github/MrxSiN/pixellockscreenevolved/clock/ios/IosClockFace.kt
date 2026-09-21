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
) : ResizableClockFace, AdjustableWidthClockFace, DozingClockFace, WeatherClockFace {

    override val timeView = IosTimeView(context)

    private val info: DateLine? = if (showsInfo) DateLine(context, DATE) else null

    override val view: View = LinearLayout(context).apply {
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
        timeView.setText(
            IosTimeText.format(
                hourOfDay = now[Calendar.HOUR_OF_DAY],
                minute = now[Calendar.MINUTE],
                is24Hour = DateFormat.is24HourFormat(context),
                locale = Locale.getDefault(),
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

    override fun setWidth(width: Float) {
        timeView.setWidth(width)
    }

    /** Keeps [IosClockTypography.DATE_TO_TIME_GAP_DP] between the date row's baseline and the time, whatever its size. */
    private fun spaceInfoFromTime() {
        val row = info?.view ?: return
        val gap = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, IosClockTypography.DATE_TO_TIME_GAP_DP, context.resources.displayMetrics,
        )
        val params = timeView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        // The row ends its font's descent below the baseline.
        params.topMargin = (gap - row.paint.fontMetrics.descent).roundToInt().coerceAtLeast(0)
        timeView.layoutParams = params
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
