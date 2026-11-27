package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Poster, after HyperOS's layered lock screen: the weekday and the date as a
 * bold title across the top, for the photo's subject to stand in front of, and
 * the time small, in a serif, set to the end below it with the date and the
 * weekday, in the wallpaper's accent colour.
 */
class PosterClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_POSTER"
    override val name: String = "Poster"
    override val description: String = "A bold title with a small serif time below, after HyperOS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Large(context)
        FaceSize.SMALL -> Small(context)
    }

    /** The face in small: the two-line title across the top, the serif date, time and weekday set to the end below it. */
    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        val left = box.left + box.width() * THUMB_MARGIN
        val right = box.right - box.width() * THUMB_MARGIN
        paint.typeface = TITLE_TYPEFACE
        paint.textSize = box.height().toFloat()
        // As tall as the title share of the box allows, or as the longest line fitting the margins does: measured at
        // a text size of the box's height, the width that fits scales that size by the room over the line's width.
        val longest = paint.measureText(THUMB_TITLE_LINES.maxBy(paint::measureText))
        paint.textSize = minOf(box.height() * THUMB_TITLE, box.height() * (right - left) / longest)
        var baseline = box.top + box.height() * 0.12f - paint.ascent()
        THUMB_TITLE_LINES.forEach {
            canvas.drawText(it, left, baseline, paint)
            baseline += paint.textSize * TITLE_LINE_SPACING
        }
        paint.typeface = SERIF
        paint.textAlign = Paint.Align.RIGHT
        baseline = box.top + box.height() * 0.6f
        listOf("4/6" to THUMB_SIDE, ClockThumbnail.SAMPLE_TIME.padStart(5, '0') to THUMB_TIME, "Thu" to THUMB_SIDE).forEach { (line, size) ->
            paint.textSize = box.height() * size
            baseline += -paint.ascent()
            canvas.drawText(line, right, baseline, paint)
            baseline += paint.descent()
        }
    }

    private abstract class Face(protected val context: Context) : ClockFace, DozingClockFace {
        protected val shortSide: Float = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        protected val accent = HyperColors.accent(context)
        private var color = Color.WHITE
        private var doze = 0f

        /** The lines drawn in the clock's colour, and those drawn in the accent. */
        protected abstract val plain: List<TextView>
        protected abstract val accented: List<TextView>

        /** The lines traced rather than filled on the always-on display: the large ones. */
        protected open val traced: List<TextView> get() = plain

        protected fun time(): String {
            val now = Calendar.getInstance()
            val parts = TimeParts.of(now[Calendar.HOUR_OF_DAY], now[Calendar.MINUTE], DateFormat.is24HourFormat(context), Locale.getDefault())
            return "${parts.hour.padStart(2, '0')}:${parts.minute}"
        }

        protected fun text(typeface: Typeface, gravity: Int): TextView = OutlinedText(context).apply {
            this.typeface = typeface
            this.gravity = gravity
            includeFontPadding = false
            maxLines = 1
        }

        override fun setColor(color: Int) {
            this.color = color
            paint()
        }

        override fun setDoze(fraction: Float) {
            doze = fraction
            paint()
        }

        /**
         * The always-on display traces the bold title rather than filling it, as the other faces thin or
         * outline their large figures there: filled, it kept a block of pixels lit all night. Switched
         * half way through the doze, which SystemUI runs in a moment as the display sleeps or wakes.
         */
        private fun paint() {
            val outline = doze >= HALF
            plain.forEach { it.setTextColor(color) }
            traced.forEach { (it as? OutlinedText)?.outlined = outline }
            accented.forEach { it.setTextColor(HyperColors.accented(color, accent, doze)) }
        }
    }

    private class Large(context: Context) : Face(context), ResizableClockFace {
        private val title = text(TITLE_TYPEFACE, Gravity.START).apply { maxLines = 2 }
        private val date = text(SERIF, Gravity.END)
        private val clock = text(SERIF, Gravity.END)
        private val weekday = text(SERIF, Gravity.END)
        private var scale = 1f

        override val plain = listOf(title)
        override val accented = listOf(date, clock, weekday)
        override val timeView: View get() = clock

        /** As on HyperOS, the subject stands in front of the title, and the time stays clear of it. */
        override val depthView: View get() = title
        override val drawsDate: Boolean = true

        override val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(WIDTH_MATCH, WRAP)
            minimumWidth = (shortSide * BLOCK_WIDTH).toInt()
            addView(title, LinearLayout.LayoutParams(WIDTH_MATCH, WRAP))
            listOf(date, clock, weekday).forEach { addView(it, LinearLayout.LayoutParams(WIDTH_MATCH, WRAP)) }
        }

        init {
            setSize(0f)
            refresh()
        }

        override fun refresh() {
            val now = Calendar.getInstance()
            val locale = Locale.getDefault()
            title.text = buildString {
                append(DateFormat.format("EEEE", now).toString().uppercase(locale))
                append('\n')
                append(DateFormat.format(DateFormat.getBestDateTimePattern(locale, "MMMMd"), now).toString().uppercase(locale))
            }
            fitTitle()
            date.text = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "Md"), now)
            clock.text = time()
            weekday.text = DateFormat.format("EEE", now)
        }

        override fun setSize(size: Float) {
            scale = 1f + (LARGEST_SCALE - 1f) * size.coerceIn(0f, 1f)
            fitTitle()
            title.setLineSpacing(0f, TITLE_LINE_SPACING)
            clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * TIME_SIZE * scale)
            listOf(date, weekday).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * SIDE_SIZE * scale) }
            (date.layoutParams as ViewGroup.MarginLayoutParams).topMargin = (shortSide * TIME_GAP).toInt()
            view.requestLayout()
        }

        /** The title as large as the size asks, or smaller where its longest line would not fit across the block. */
        private fun fitTitle() {
            val wanted = shortSide * TITLE_SIZE * scale
            title.paint.textSize = wanted
            val widest = title.text.lines().maxOfOrNull { title.paint.measureText(it) } ?: 0f
            val room = shortSide * BLOCK_WIDTH
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (widest > room) wanted * room / widest else wanted)
        }
    }

    private class Small(context: Context) : Face(context) {
        private val clock = text(SERIF, Gravity.CENTER).apply { setTextSize(TypedValue.COMPLEX_UNIT_PX, columnFilling(this)) }
        override val plain = emptyList<TextView>()
        override val accented = listOf(clock)

        /** Run across the column, the small time is large enough to trace on the always-on display too. */
        override val traced = listOf(clock)
        override val timeView: View get() = clock
        override val drawsDate: Boolean = false
        override val view: View = clock

        init {
            refresh()
        }

        override fun refresh() {
            clock.text = time()
        }

        /**
         * The text size at which the widest time, "00:00", runs across the lock screen's text column, no taller
         * than [SMALL_MAX] of the short side: beside notifications the time fills the space, as iOS's does.
         */
        private fun columnFilling(text: TextView): Float {
            val paint = text.paint
            paint.textSize = PROBE
            val perSize = paint.measureText(WIDEST_TIME) / PROBE
            return minOf(shortSide * SMALL_COLUMN / perSize, shortSide * SMALL_MAX)
        }
    }

    /**
     * A line of text that can be traced in outline ([outlined]). A plain stroke traced the overlapping
     * contours inside the bold title's letters (crossbars through the H and the A), so, as [my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk]
     * does, it is stroked twice as wide and the letters' insides cut back out of it, leaving only the line
     * around their outer edge.
     */
    private class OutlinedText(context: Context) : TextView(context) {
        var outlined = false
            set(value) {
                if (field == value) return
                field = value
                invalidate()
            }

        private val cut = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)

        override fun onDraw(canvas: Canvas) {
            if (!outlined) return super.onDraw(canvas)
            val saved = canvas.saveLayer(null, null)
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 2 * OUTLINE_DP * resources.displayMetrics.density
            super.onDraw(canvas)
            paint.style = Paint.Style.FILL
            paint.xfermode = cut
            super.onDraw(canvas)
            paint.xfermode = null
            canvas.restoreToCount(saved)
        }
    }

    private companion object {
        /** Past this far into the doze, the title is traced ([Face]). */
        const val HALF = 0.5f

        /** The title's always-on outline, as [my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk] draws its own. */
        const val OUTLINE_DP = 1.5f

        val TITLE_TYPEFACE: Typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), 800, false)
        val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        val THUMB_TITLE_LINES = listOf("THURSDAY", "APRIL 6")
        const val THUMB_MARGIN = 0.12f
        const val THUMB_TITLE = 0.16f
        const val THUMB_TIME = 0.15f
        const val THUMB_SIDE = 0.08f

        const val TITLE_SIZE = 0.15f
        const val TITLE_LINE_SPACING = 0.9f
        const val TIME_SIZE = 0.12f
        const val SIDE_SIZE = 0.06f
        /** The small time's widest run and tallest size, against the short side. */
        const val SMALL_COLUMN = 0.86f
        const val SMALL_MAX = 0.3f
        const val WIDEST_TIME = "00:00"
        const val PROBE = 100f
        const val LARGEST_SCALE = 1.35f

        /** Space between the title and the time block, against the short side: the subject's room. */
        const val TIME_GAP = 0.32f
        const val BLOCK_WIDTH = 0.86f
        const val WIDTH_MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
