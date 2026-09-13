package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

import kotlin.math.ceil

/**
 * The iOS time, drawn at any size and in any [IosNumeralFont].
 *
 * A TextView cannot stretch its glyphs vertically or keep a colon round while
 * the digits around it stretch, so the time draws itself: the digits through a
 * vertical scale, and the colon as two round dots centred between the digits
 * on either side and around the middle of the numerals, as iOS sets it, rather
 * than where the font puts its colon for running text. The view is exactly as
 * tall as the numerals, so the date sits right above them at every size.
 */
internal class IosTimeView(context: Context, font: IosNumeralFont) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()

    private var font = font
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

    fun setFont(font: IosNumeralFont) {
        if (this.font === font) return
        this.font = font
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

        if (colon > 0 && colon < text.length - 1) drawColon(canvas, x, colon)
    }

    /**
     * Two round dots, the size of the font's own, centred in the gap between the
     * last digit before the colon and the first after it, and placed evenly
     * above and below the middle of the numerals.
     */
    private fun drawColon(canvas: Canvas, left: Float, colon: Int) {
        paint.getTextBounds(COLON_TEXT, 0, 1, bounds)
        val radius = bounds.width() / 2f

        paint.getTextBounds(text, 0, colon, bounds)
        val before = left + bounds.right
        val afterStart = left + paint.measureText(text, 0, colon + 1)
        paint.getTextBounds(text, colon + 1, text.length, bounds)
        val after = afterStart + bounds.left
        val centreX = (before + after) / 2

        val height = (numeralBottom - numeralTop) * stretch
        val offset = height * COLON_DOT_OFFSET
        canvas.drawCircle(centreX, height / 2 - offset, radius, paint)
        canvas.drawCircle(centreX, height / 2 + offset, radius, paint)
    }

    /** Sets the cut, text size and stretch for the current font, size and text. */
    private fun relayout() {
        paint.typeface = font.typeface
        // Paint ignores settings equal to the last ones, even though setting the
        // typeface above dropped them, so they are cleared first.
        paint.fontVariationSettings = null
        paint.fontVariationSettings = font.variationFor(size, stretch = 1f)
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)

        val fit = IosClockScale.fit(
            size = size,
            shortSide = shortSide,
            numeralPerTextSize = bounds.height() / PROBE_SIZE,
            widthPerTextSize = paint.measureText(text.ifEmpty { NUMERALS }) / PROBE_SIZE,
            maxStretch = font.maxStretch,
        )
        stretch = fit.stretch
        paint.textSize = fit.textSize
        paint.fontVariationSettings = font.variationFor(size, stretch)

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

        /** Each dot's distance from the numerals' middle, against their height, as iOS sets it. */
        const val COLON_DOT_OFFSET = 0.2f
    }
}
