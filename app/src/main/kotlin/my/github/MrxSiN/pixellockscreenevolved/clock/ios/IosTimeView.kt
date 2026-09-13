package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

import kotlin.math.ceil

/**
 * The iOS time, drawn at any size from the smallest clock to the tallest.
 *
 * A TextView cannot stretch its glyphs vertically or keep a colon round while
 * the digits around it stretch, so the time draws itself: the digits through a
 * vertical scale, the colon as two circles placed where the stretched colon
 * would put them. The view is exactly as tall as the numerals, so the date sits
 * right above them at every size.
 */
internal class IosTimeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = IosClockTypography.timeTypeface
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()

    private var text = ""
    private var size = 0f
    private var stretch = 1f

    /** Numeral box relative to the baseline, before the stretch. */
    private var numeralTop = 0f
    private var numeralBottom = 0f

    fun setText(text: String) {
        if (this.text == text) return
        this.text = text
        relayout()
    }

    fun setSize(size: Float) {
        this.size = size
        relayout()
    }

    fun setColor(color: Int) {
        paint.color = color
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ceil(paint.measureText(text)).toInt()
        val height = ceil((numeralBottom - numeralTop) * stretch).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val x = (width - paint.measureText(text)) / 2
        val colon = text.indexOf(COLON)

        canvas.save()
        canvas.translate(x, 0f)
        canvas.scale(1f, stretch)
        canvas.translate(0f, -numeralTop)
        if (colon < 0) {
            canvas.drawText(text, 0f, 0f, paint)
        } else {
            canvas.drawText(text, 0, colon, 0f, 0f, paint)
            canvas.drawText(text, colon + 1, text.length, paint.measureText(text, 0, colon + 1), 0f, paint)
        }
        canvas.restore()

        if (colon >= 0) drawColon(canvas, x + paint.measureText(text, 0, colon))
    }

    /** Two round dots, the font's own size, where its colon would sit once stretched. */
    private fun drawColon(canvas: Canvas, left: Float) {
        paint.getTextBounds(COLON_TEXT, 0, 1, bounds)
        val radius = bounds.width() / 2f
        val centreX = left + bounds.exactCenterX()
        val upper = (bounds.top + radius - numeralTop) * stretch
        val lower = (bounds.bottom - radius - numeralTop) * stretch
        canvas.drawCircle(centreX, upper, radius, paint)
        canvas.drawCircle(centreX, lower, radius, paint)
    }

    /** Sets the text size and stretch for the current size and text. */
    private fun relayout() {
        paint.fontVariationSettings = IosClockScale.variationFor(size, stretch = 1f)
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)

        val fit = IosClockScale.fit(
            size = size,
            shortSide = shortSide,
            numeralPerTextSize = bounds.height() / PROBE_SIZE,
            widthPerTextSize = paint.measureText(text.ifEmpty { NUMERALS }) / PROBE_SIZE,
        )
        stretch = fit.stretch
        paint.textSize = fit.textSize
        paint.fontVariationSettings = IosClockScale.variationFor(size, stretch)

        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)
        numeralTop = bounds.top.toFloat()
        numeralBottom = bounds.bottom.toFloat()

        requestLayout()
        invalidate()
    }

    private companion object {
        const val COLON = ':'
        const val COLON_TEXT = ":"
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
    }
}
