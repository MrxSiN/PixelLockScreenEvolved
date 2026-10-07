package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.View

import kotlin.math.ceil

import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DigitRoll
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk

/**
 * The iOS time, drawn at any size and width in [RobotoFlexNumerals].
 *
 * A TextView cannot stretch its glyphs vertically or keep a colon round while
 * the digits around it stretch, so the time draws itself: the digits through a
 * vertical scale, and the colon as two round dots centred between the digits
 * on either side and around the middle of the numerals, as iOS sets it, rather
 * than where the font puts its colon for running text. The view is exactly as
 * tall as the numerals, so the date sits right above them at every size.
 *
 * The time is laid out once as a path per character, whenever the text, size
 * or width change, so it can be filled awake and traced in outline on the
 * always-on display ([setOutline]) from the same shapes. The paths are
 * stretched rather than the canvas, so the outline ([DozeInk]) keeps one width
 * around every stroke.
 *
 * When the minute turns, the digits that change roll, as on iOS ([DigitRoll],
 * [isChanging]).
 */
internal class IosTimeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    private val ink = DozeInk(context)

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()

    /** The time as laid out, a glyph per character, and its roll to each new minute. */
    private val digits = DigitRoll(this, ink)

    private var text = ""
    private var size = 0f
    private var faceWidth = AdjustableWidthClockFace.DEFAULT_WIDTH
    private var stretch = 1f

    /** Numeral box relative to the baseline, before the stretch. */
    private var numeralTop = 0f
    private var numeralBottom = 0f

    /** Whether the digits are rolling to a new minute, so what is drawn now is not yet what the time will show. */
    val isChanging: Boolean get() = digits.isChanging

    fun setText(text: String) {
        if (this.text == text) return
        val before = this.text
        val beforeWidth = measuredTextWidth()
        this.text = text
        relayout()
        // The view stays centred, so glyphs laid out for the old width sit this far off in the new one.
        digits.show(before, text, layOutGlyphs(), (measuredTextWidth() - beforeWidth) / 2)
    }

    fun setSize(size: Float) {
        this.size = size
        relayout()
        digits.replace(layOutGlyphs())
    }

    fun setWidth(width: Float) {
        if (faceWidth == width) return
        faceWidth = width
        relayout()
        digits.replace(layOutGlyphs())
    }

    fun setColor(color: Int) {
        ink.color = color
        invalidate()
    }

    /** Fades the filled time into its outline, from 0 filled to 1 outline only. */
    fun setOutline(fraction: Float) {
        ink.outline = fraction
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // A stroked path extends half its stroke beyond the filled glyph. Keep
        // that ink inside the view instead of clipping the outermost line.
        val width = ceil(measuredTextWidth()).toInt() + 2 * outlineInset
        val height = ceil(numeralHeight()).toInt() + 2 * outlineInset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digits.finish()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate((width - measuredTextWidth()) / 2, outlineInset.toFloat())
        digits.draw(canvas, numeralHeight() * DigitRoll.RISE)
        canvas.restore()
    }

    private fun measuredTextWidth(): Float = paint.measureText(text)

    /** Room for half the AOD outline plus one anti-aliasing pixel. */
    private val outlineInset: Int get() = ink.inset

    private fun numeralHeight(): Float = (numeralBottom - numeralTop) * stretch

    /** Sets the cut, text size and stretch for the current size, width and text, and lays the time out again. */
    private fun relayout() {
        paint.typeface = RobotoFlexNumerals.typeface
        // Paint ignores settings equal to the last ones, even though setting the
        // typeface above dropped them, so they are cleared first.
        paint.textSize = PROBE_SIZE
        val probed = text.ifEmpty { NUMERALS }
        // Measured at the widest width first, which every width's sizes are spread by, so the
        // size chosen is one the widest width fits and a narrower one draws a narrower time.
        paint.fontVariationSettings = null
        paint.fontVariationSettings = RobotoFlexNumerals.variationFor(width = 1f, stretch = 1f)
        val widestWidthPerTextSize = paint.measureText(probed) / PROBE_SIZE
        paint.fontVariationSettings = null
        paint.fontVariationSettings = RobotoFlexNumerals.variationFor(faceWidth, stretch = 1f)
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)

        val fit = IosClockScale.fit(
            size = size,
            shortSide = shortSide,
            numeralPerTextSize = bounds.height() / PROBE_SIZE,
            widestWidthPerTextSize = widestWidthPerTextSize,
            maxStretch = RobotoFlexNumerals.MAX_STRETCH,
        )
        stretch = fit.stretch
        paint.textSize = fit.textSize
        paint.fontVariationSettings = RobotoFlexNumerals.variationFor(faceWidth, stretch)

        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)
        numeralTop = bounds.top.toFloat()
        numeralBottom = bounds.bottom.toFloat()

        requestLayout()
        invalidate()
    }

    /** Lays each character out as a glyph: the digits stretched, the colon as its two dots between them. */
    private fun layOutGlyphs(): List<DigitRoll.Glyph> {
        val stretching = Matrix().apply { setTranslate(0f, -numeralTop); postScale(1f, stretch) }
        val colon = text.indexOf(COLON)
        return text.indices.map { index ->
            val left = paint.measureText(text, 0, index)
            val path = Path()
            if (index == colon) {
                if (colon > 0 && colon < text.length - 1) addColon(path, colon)
            } else {
                paint.getTextPath(text, index, index + 1, left, 0f, path)
                path.transform(stretching)
            }
            DigitRoll.Glyph(path, left)
        }
    }

    /**
     * The colon's two dots, centred in the gap between the last digit before the
     * colon and the first after it, round and the size of the font's own, placed
     * evenly above and below the middle of the numerals.
     */
    private fun addColon(path: Path, colon: Int) {
        paint.getTextBounds(text, 0, colon, bounds)
        val before = bounds.right
        val afterStart = paint.measureText(text, 0, colon + 1)
        paint.getTextBounds(text, colon + 1, text.length, bounds)
        val after = afterStart + bounds.left
        val centreX = (before + after) / 2
        val middle = numeralHeight() / 2

        paint.getTextBounds(COLON_TEXT, 0, 1, bounds)
        val radius = bounds.width() / 2f
        val offset = numeralHeight() * RobotoFlexNumerals.COLON_DOT_OFFSET
        path.addCircle(centreX, middle - offset, radius, Path.Direction.CW)
        path.addCircle(centreX, middle + offset, radius, Path.Direction.CW)
    }

    private companion object {
        const val COLON = ':'
        const val COLON_TEXT = ":"
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
    }
}
