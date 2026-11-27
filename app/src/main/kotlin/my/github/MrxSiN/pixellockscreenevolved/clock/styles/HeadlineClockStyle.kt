package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralView
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Headline, after HyperOS's layered lock screen: the time, the weekday and the
 * date set as one condensed, heavy headline down the start of the screen, each
 * line as wide as the block, the minute in the wallpaper's accent colour.
 */
class HeadlineClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_HEADLINE"
    override val name: String = "Headline"
    override val description: String = "Time, weekday and date as one condensed headline, after HyperOS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        FaceSize.SMALL -> OneLineSmallFace(context, CUT, tracking = TRACKING)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        ClockThumbnail.stacked(
            canvas, box, paint, CUT, listOf("0941", "THU", "4/6"),
            heightShare = 0.8f, widthShare = 0.6f, align = { -1 }, lineGap = LINE_GAP,
        )
    }

    private class Face(context: Context) : NumeralClockFace(context, date = null), ResizableClockFace {

        private val hour = line(context)
        private val minute = line(context)
        private val weekday = line(context)
        private val day = line(context)
        private val time = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hour, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(minute, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        private val accent = HyperColors.accent(context)
        private var color = 0
        private var doze = 0f

        override val numerals: List<NumeralView> = listOf(hour, minute, weekday, day)
        override val view: View = column(Gravity.START, time, weekday, day).apply {
            minimumWidth = (shortSide * BLOCK_WIDTH).toInt()
        }
        override val timeView: View get() = time
        override val drawsDate: Boolean = true

        init {
            setSize(0f)
            refresh()
        }

        override fun write(parts: TimeParts.Parts) {
            val now = Calendar.getInstance()
            val locale = Locale.getDefault()
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setText(parts.minute)
            weekday.setText(DateFormat.format("EEE", now).toString().uppercase(locale))
            day.setText(DateFormat.format(DateFormat.getBestDateTimePattern(locale, "Md"), now).toString())
        }

        override fun setColor(color: Int) {
            this.color = color
            super.setColor(color)
            minute.setColor(HyperColors.accented(color, accent, doze))
        }

        override fun setDoze(fraction: Float) {
            doze = fraction
            super.setDoze(fraction)
            minute.setColor(HyperColors.accented(color, accent, doze))
        }

        override fun setSize(size: Float) {
            val width = shortSide * lerp(SMALLEST, LARGEST, size)
            // Each line as tall as the block's width lets it be, so every line runs the block's width;
            // the hour and the minute share it, the space between them included.
            val pair = width * (1f - PAIR_GAP) / 2
            hour.fit(shortSide, maxWidth = pair)
            minute.fit(shortSide, maxWidth = pair)
            (minute.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.marginStart = (width * PAIR_GAP).toInt()
            weekday.fit(shortSide, maxWidth = width)
            day.fit(shortSide, maxWidth = width)
            val gap = width * LINE_GAP
            spaceAbove(weekday, gap)
            spaceAbove(day, gap)
        }
    }

    private companion object {
        val CUT = FlexCut(weight = 900f, width = 30f)

        /** Space between the figures, in ems, and between the hour and the minute, against the block's width. */
        const val TRACKING = 0.06f
        const val PAIR_GAP = 0.03f

        fun line(context: Context) = NumeralView(context, CUT).apply {
            gravity = Gravity.START
            setTracking(TRACKING)
        }

        /** The headline's width against the short side, smallest and largest. */
        const val SMALLEST = 0.42f
        const val LARGEST = 0.6f

        /** Kept this wide so the headline's start lines up with the lock screen's. */
        const val BLOCK_WIDTH = 0.86f
        const val LINE_GAP = 0.03f
    }
}
