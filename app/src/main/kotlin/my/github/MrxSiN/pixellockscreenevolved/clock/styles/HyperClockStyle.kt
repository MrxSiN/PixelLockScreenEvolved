package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import kotlin.math.ceil
import kotlin.math.max

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
 * Hyper, after HyperOS's magazine lock screen: the hour over the minute in
 * giant, condensed, outlined figures that run down the whole screen, the hour
 * in the wallpaper's accent colour and the minute in the clock's own, with the
 * date beside them as a narrow column and the weekday turned on its side in an
 * accent pill. The figures are outline alone, so the photo shows through them,
 * and its subject can stand in front of them.
 */
class HyperClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_HYPER"
    override val name: String = "Hyper"
    override val description: String = "Outlined hour over minute down the whole screen, after HyperOS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        FaceSize.SMALL -> OneLineSmallFace(context, CUT.copy(width = 60f), tracking = TRACKING)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = box.height() * THUMBNAIL_STROKE
        ClockThumbnail.stacked(
            canvas, box, paint, CUT, listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE),
            heightShare = 0.92f, widthShare = 0.5f, lineGap = LINE_GAP,
        )
        // The weekday pill beside them, filled, as the face draws it.
        paint.style = Paint.Style.FILL
        val pillWidth = box.width() * 0.09f
        val left = box.right - box.width() * 0.16f
        val pill = RectF(left, box.top + box.height() * 0.3f, left + pillWidth, box.top + box.height() * 0.48f)
        canvas.drawRoundRect(pill, pillWidth / 2, pillWidth / 2, paint)
    }

    private class Face(context: Context) : NumeralClockFace(context, date = null), ResizableClockFace {

        private val hour = line(context)
        private val minute = line(context)
        private val time = column(Gravity.CENTER_HORIZONTAL, hour, minute)
        private val accent = HyperColors.accent(context)
        private val dateColumn = DateColumn(context, shortSide, accent)
        private var color = Color.WHITE
        private var doze = 0f

        override val numerals: List<NumeralView> = listOf(hour, minute)
        override val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = android.view.ViewGroup.LayoutParams(WRAP, WRAP)
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(dateColumn, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.TOP
                marginStart = (shortSide * DATE_GAP).toInt()
            })
        }
        override val timeView: View get() = time

        /** The face writes the date itself, in [dateColumn]. */
        override val drawsDate: Boolean = true

        init {
            setSize(0f)
            refresh()
        }

        override fun write(parts: TimeParts.Parts) {
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setText(parts.minute)
            dateColumn.refresh()
        }

        override fun setColor(color: Int) {
            this.color = color
            super.setColor(color)
            recolor()
        }

        override fun setDoze(fraction: Float) {
            doze = fraction
            super.setDoze(fraction)
            recolor()
        }

        /** The hour and the pill in the accent, easing to the clock's own colour on the always-on display. */
        private fun recolor() {
            val accented = HyperColors.accented(color, accent, doze)
            hour.setColor(accented)
            dateColumn.setColors(color, accented)
        }

        override fun setSize(size: Float) {
            val height = screenHeight * lerp(SMALLEST, LARGEST, size)
            numerals.forEach { it.fit(height, maxWidth = shortSide * MAX_WIDTH, maxStretch = MAX_STRETCH) }
            spaceAbove(minute, height * LINE_GAP)
        }
    }

    /**
     * The date as a narrow column: the month over a slash over the day, in the
     * locale's order, and the weekday turned on its side in a filled pill under them.
     */
    private class DateColumn(context: Context, shortSide: Float, accent: Int) : View(context) {

        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), DATE_WEIGHT, false)
            textSize = shortSide * DATE_SIZE
            textAlign = Paint.Align.CENTER
        }
        private val pillText = Paint(text).apply { color = Color.WHITE }
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        private val pillBox = RectF()
        private var lines = listOf<String>()
        private var weekday = ""

        fun refresh() {
            val now = Calendar.getInstance()
            val locale = Locale.getDefault()
            val month = String.format(locale, "%d", now[Calendar.MONTH] + 1)
            val day = String.format(locale, "%d", now[Calendar.DAY_OF_MONTH])
            val dayFirst = DateFormat.getBestDateTimePattern(locale, "Md").let { it.indexOf('d') in 0 until it.indexOf('M') }
            val next = if (dayFirst) listOf(day, "/", month) else listOf(month, "/", day)
            val nextWeekday = DateFormat.format("EEE", now).toString().uppercase(locale)
            if (next == lines && nextWeekday == weekday) return
            lines = next
            weekday = nextWeekday
            contentDescription = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), now)
            requestLayout()
            invalidate()
        }

        /** The date in [plain], the pill in [accent]. */
        fun setColors(plain: Int, accent: Int) {
            text.color = plain
            pill.color = accent
            invalidate()
        }

        private fun lineHeight(): Float = text.fontSpacing * LINE_SPACING
        private fun pillWidth(): Float = text.textSize * PILL_WIDTH
        private fun pillHeight(): Float = text.measureText(weekday) + pillWidth()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widest = max(lines.maxOfOrNull(text::measureText) ?: 0f, pillWidth())
            val height = lineHeight() * lines.size + lineHeight() * PILL_GAP + pillHeight()
            setMeasuredDimension(resolveSize(ceil(widest).toInt(), widthMeasureSpec), resolveSize(ceil(height).toInt(), heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val centre = width / 2f
            var baseline = -text.ascent()
            lines.forEach {
                canvas.drawText(it, centre, baseline, text)
                baseline += lineHeight()
            }
            val top = lineHeight() * lines.size + lineHeight() * PILL_GAP
            pillBox.set(centre - pillWidth() / 2, top, centre + pillWidth() / 2, top + pillHeight())
            canvas.drawRoundRect(pillBox, pillWidth() / 2, pillWidth() / 2, pill)
            // The weekday reads downwards, turned a quarter clockwise, as HyperOS sets it.
            canvas.save()
            canvas.rotate(90f, pillBox.centerX(), pillBox.centerY())
            canvas.drawText(weekday, pillBox.centerX(), pillBox.centerY() - (pillText.ascent() + pillText.descent()) / 2, pillText)
            canvas.restore()
        }
    }

    private companion object {
        /** Heavy and as narrow as Roboto Flex goes, so two figures fit across while standing most of the screen tall. */
        val CUT = FlexCut(weight = 900f, width = 25f)

        /**
         * Each line's height, against the screen's long side: together, with the gap, the two run
         * from under the status bar to the foot of the screen, as HyperOS's do, once SystemUI has drawn
         * the resting clock its tenth or so smaller.
         */
        const val SMALLEST = 0.43f
        const val LARGEST = 0.47f

        /** How wide the two figures may be, against the short side, and how far they may be stretched to stand that tall. */
        const val MAX_WIDTH = 0.74f
        const val MAX_STRETCH = 3f

        /** Space between the hour and the minute, against the line height: tight, yet clear of the occlusion reading's line gap. */
        const val LINE_GAP = 0.04f

        /** The outline, awake, in dp: bold enough to carry figures this large. */
        const val OUTLINE_DP = 3.5f

        const val DATE_GAP = 0.03f
        const val DATE_SIZE = 0.05f
        const val DATE_WEIGHT = 500
        const val LINE_SPACING = 0.95f
        const val PILL_GAP = 0.4f
        const val PILL_WIDTH = 1.7f
        const val THUMBNAIL_STROKE = 0.025f

        /** Space between the figures, in ems ([NumeralView.setTracking]). */
        const val TRACKING = 0.06f

        fun line(context: Context) = NumeralView(context, CUT).apply {
            setInk(fill = 0f, outline = 1f, outlineDp = OUTLINE_DP)
            setTracking(TRACKING)
        }
    }
}
