package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.TypedValue
import android.view.View

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The iOS time, drawn at any size and in any [IosNumeralFont].
 *
 * A TextView cannot stretch its glyphs vertically or keep a colon round while
 * the digits around it stretch, so the time draws itself: the digits through a
 * vertical scale, and the colon as two round dots centred between the digits
 * on either side and around the middle of the numerals, as iOS sets it, rather
 * than where the font puts its colon for running text. The view is exactly as
 * tall as the numerals, so the date sits right above them at every size.
 *
 * The time is laid out once as a path, whenever the text, size or font change,
 * so it can be filled awake and traced in outline on the always-on display
 * ([setOutline]) from the same shapes. The path is stretched rather than the
 * canvas, so the outline keeps one width around every stroke. Variable fonts
 * build glyphs from overlapping contours, which a plain stroke would trace
 * inside them, so the outline is stroked twice as wide and the glyphs' insides
 * are cut out of it, leaving only the line around their outer edge.
 */
internal class IosTimeView(context: Context, font: IosNumeralFont) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2 * TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, IosClockTypography.TIME_OUTLINE_DP, context.resources.displayMetrics,
        )
    }

    private val insidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()

    /** The time as laid out, from the left of its advance and the top of the numerals. */
    private val shapes = Path()
    private val glyphs = Path()

    private var font = font
    private var text = ""
    private var size = 0f
    private var stretch = 1f
    private var color = Color.WHITE
    private var outline = 0f

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
        this.color = color
        invalidate()
    }

    /** Fades the filled time into its outline, from 0 filled to 1 outline only. */
    fun setOutline(fraction: Float) {
        outline = fraction.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ceil(paint.measureText(text)).toInt()
        val height = ceil((numeralBottom - numeralTop) * stretch).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate((width - paint.measureText(text)) / 2, 0f)
        if (outline < 1f) {
            paint.color = withAlpha(color, 1f - outline)
            canvas.drawPath(shapes, paint)
        }
        if (outline > 0f) {
            canvas.saveLayerAlpha(null, (outline * OPAQUE).roundToInt())
            outlinePaint.color = color
            canvas.drawPath(shapes, outlinePaint)
            canvas.drawPath(shapes, insidePaint)
            canvas.restore()
        }
        canvas.restore()
    }

    /** Sets the cut, text size and stretch for the current font, size and text, and lays the time out again. */
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

        layOutShapes()
        requestLayout()
        invalidate()
    }

    /** Lays the digits out as [shapes], stretched, with the colon's dots added between them. */
    private fun layOutShapes() {
        shapes.reset()
        val colon = text.indexOf(COLON)
        if (colon < 0) {
            addGlyphs(0, text.length, 0f)
        } else {
            addGlyphs(0, colon, 0f)
            addGlyphs(colon + 1, text.length, paint.measureText(text, 0, colon + 1))
        }
        shapes.transform(Matrix().apply { setTranslate(0f, -numeralTop); postScale(1f, stretch) })

        if (colon > 0 && colon < text.length - 1) addColon(colon)
    }

    private fun addGlyphs(start: Int, end: Int, x: Float) {
        paint.getTextPath(text, start, end, x, 0f, glyphs)
        shapes.addPath(glyphs)
    }

    /**
     * Two round dots, the size of the font's own, centred in the gap between the
     * last digit before the colon and the first after it, and placed evenly
     * above and below the middle of the numerals.
     */
    private fun addColon(colon: Int) {
        paint.getTextBounds(COLON_TEXT, 0, 1, bounds)
        val radius = bounds.width() / 2f

        paint.getTextBounds(text, 0, colon, bounds)
        val before = bounds.right
        val afterStart = paint.measureText(text, 0, colon + 1)
        paint.getTextBounds(text, colon + 1, text.length, bounds)
        val after = afterStart + bounds.left
        val centreX = (before + after) / 2

        val height = (numeralBottom - numeralTop) * stretch
        val offset = height * COLON_DOT_OFFSET
        shapes.addCircle(centreX, height / 2 - offset, radius, Path.Direction.CW)
        shapes.addCircle(centreX, height / 2 + offset, radius, Path.Direction.CW)
    }

    private companion object {
        const val COLON = ':'
        const val COLON_TEXT = ":"
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
        const val OPAQUE = 255

        /** Each dot's distance from the numerals' middle, against their height, as iOS sets it. */
        const val COLON_DOT_OFFSET = 0.2f

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
