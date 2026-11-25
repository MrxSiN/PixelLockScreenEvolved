package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import kotlin.math.ceil

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Dot, after Nothing OS: the time in a five-by-seven dot matrix, the hour over
 * the minute, with a spaced capital date under them. On the always-on display
 * the dots shrink, so fewer pixels stay lit.
 */
class DotClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_DOT"
    override val name: String = "Dot"
    override val description: String = "Dot-matrix hour over the minute, after Nothing OS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> LargeFace(context)
        FaceSize.SMALL -> SmallFace(context)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        val lines = listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE)
        val pitch = minOf(box.height() * 0.8f / (2 * DotMatrix.ROWS + LINE_GAP_DOTS), box.width() * 0.8f / DotMatrix.columnsOf(lines[0]))
        var top = box.exactCenterY() - pitch * (2 * DotMatrix.ROWS + LINE_GAP_DOTS) / 2
        lines.forEach { line ->
            DotMatrix.draw(canvas, line, box.exactCenterX() - pitch * DotMatrix.columnsOf(line) / 2, top, pitch, pitch * DotMatrix.DOT, paint)
            top += pitch * (DotMatrix.ROWS + LINE_GAP_DOTS)
        }
    }

    /** Shared by both faces: the time read in ASCII digits, which the matrix has glyphs for. */
    private abstract class Face(protected val context: Context) : ClockFace, DozingClockFace {
        protected val shortSide: Float = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        protected abstract val lines: List<DotMatrixView>

        protected fun parts(): TimeParts.Parts {
            val now = Calendar.getInstance()
            return TimeParts.of(now[Calendar.HOUR_OF_DAY], now[Calendar.MINUTE], DateFormat.is24HourFormat(context), Locale.ROOT)
        }

        override fun setColor(color: Int) = lines.forEach { it.setColor(color) }

        override fun setDoze(fraction: Float) = lines.forEach { it.setDoze(fraction) }

        protected fun column(vararg children: View): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            children.forEach { addView(it, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }) }
        }
    }

    private class LargeFace(context: Context) : Face(context), ResizableClockFace, WeatherClockFace {
        private val hour = DotMatrixView(context)
        private val minute = DotMatrixView(context)
        private val dateLine = DateLine(context, DATE)
        private val time = column(hour, minute)

        override val lines = listOf(hour, minute)
        override val view: View = column(time, dateLine.view)
        override val timeView: View get() = time
        override val drawsDate: Boolean = true

        init {
            setSize(0f)
            refresh()
        }

        override fun refresh() {
            val parts = parts()
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setText(parts.minute)
            dateLine.refresh()
        }

        override fun setColor(color: Int) {
            super.setColor(color)
            dateLine.setColor(color)
        }

        override fun setWeather(weather: ClockWeather?) = dateLine.setWeather(weather)

        override fun setSize(size: Float) {
            val height = shortSide * (SMALLEST + (LARGEST - SMALLEST) * size.coerceIn(0f, 1f))
            lines.forEach { it.setHeight(height) }
            (minute.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (height / DotMatrix.ROWS * LINE_GAP_DOTS).toInt()
            (dateLine.view.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (height * DATE_GAP).toInt()
            view.requestLayout()
        }
    }

    private class SmallFace(context: Context) : Face(context) {
        private val time = DotMatrixView(context).apply { setHeight(shortSide * SMALL_HEIGHT) }

        override val lines = listOf(time)
        override val view: View = column(time)
        override val timeView: View get() = time
        override val drawsDate: Boolean = false

        init {
            refresh()
        }

        override fun refresh() = time.setText(parts().joined)
    }

    private companion object {
        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.034f, weight = 500, allCaps = true, letterSpacing = 0.25f)
        const val SMALLEST = 0.2f
        const val LARGEST = 0.32f
        const val SMALL_HEIGHT = 0.1f

        /** Dot pitches between the hour and the minute: clear of the occlusion reading's line gap. */
        const val LINE_GAP_DOTS = 3f
        const val DATE_GAP = 0.16f
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** One line of dot-matrix text, as tall as [setHeight] says. */
internal class DotMatrixView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var text = ""
    private var pitch = 1f
    private var doze = 0f

    fun setText(text: String) {
        if (this.text == text) return
        val wider = DotMatrix.columnsOf(text) != DotMatrix.columnsOf(this.text)
        this.text = text
        if (wider) requestLayout()
        invalidate()
    }

    fun setHeight(height: Float) {
        pitch = height / DotMatrix.ROWS
        requestLayout()
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    fun setDoze(fraction: Float) {
        doze = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(ceil(DotMatrix.columnsOf(text) * pitch).toInt(), widthMeasureSpec),
            resolveSize(ceil(DotMatrix.ROWS * pitch).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val left = (width - DotMatrix.columnsOf(text) * pitch) / 2
        DotMatrix.draw(canvas, text, left, 0f, pitch, pitch * DotMatrix.DOT * (1f - DOZE_SHRINK * doze), paint)
    }

    private companion object {
        /** How much smaller the dots are on the always-on display. */
        const val DOZE_SHRINK = 0.4f
    }
}

/** A five-by-seven dot font for the digits and the colon, and how to draw it. */
internal object DotMatrix {

    const val ROWS = 7

    /** Dot radius against the pitch. */
    const val DOT = 0.42f

    private const val GAP = 1

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        '1' to listOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to listOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to listOf(".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."),
        '4' to listOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to listOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to listOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to listOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to listOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to listOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to listOf(".", ".", "#", ".", "#", ".", "."),
    )

    /** How many dot columns [text] takes, the gaps between glyphs included. */
    fun columnsOf(text: String): Int {
        val glyphs = text.mapNotNull { GLYPHS[it] }
        return if (glyphs.isEmpty()) 0 else glyphs.sumOf { it[0].length } + GAP * (glyphs.size - 1)
    }

    /** Draws [text] with its top left dot cell at ([left], [top]). */
    fun draw(canvas: Canvas, text: String, left: Float, top: Float, pitch: Float, radius: Float, paint: Paint) {
        var x = left
        for (char in text) {
            val glyph = GLYPHS[char] ?: continue
            glyph.forEachIndexed { row, line ->
                line.forEachIndexed { column, dot ->
                    if (dot == '#') canvas.drawCircle(x + (column + 0.5f) * pitch, top + (row + 0.5f) * pitch, radius, paint)
                }
            }
            x += (glyph[0].length + GAP) * pitch
        }
    }
}
