package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.view.animation.PathInterpolator

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
 * The time is laid out once as a path per character, whenever the text, size
 * or font change, so it can be filled awake and traced in outline on the
 * always-on display ([setOutline]) from the same shapes. The paths are
 * stretched rather than the canvas, so the outline keeps one width around every
 * stroke. Variable fonts build glyphs from overlapping contours, which a plain
 * stroke would trace inside them, so the outline is stroked twice as wide and
 * the glyphs' insides are cut out of it, leaving only the line around their
 * outer edge.
 *
 * When the minute turns, the digits that change roll, as on iOS: each old digit
 * rises and fades while its new one comes up from below into its place, and
 * the digits that stay glide to where the new text puts them ([isChanging]).
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

    /** One character of the time as laid out: its shape, from the left of the text's advance and the top of the numerals. */
    private class Glyph(val path: Path, val left: Float)

    /** The time as laid out, a glyph per character of [text]. */
    private var glyphs = emptyList<Glyph>()

    private var font = font
    private var text = ""
    private var size = 0f
    private var stretch = 1f
    private var color = Color.WHITE
    private var outline = 0f

    /** Numeral box relative to the baseline, before the stretch. */
    private var numeralTop = 0f
    private var numeralBottom = 0f

    /** The roll from the last minute, while it runs. */
    private class Roll(
        val before: List<Glyph>,
        val places: List<IosDigitTransition.Place>,
        /** How much wider the new text is than the old on each side, as the centred view grows. */
        val shift: Float,
    ) {
        var progress = 0f
    }

    private var roll: Roll? = null
    private var rolling: ValueAnimator? = null
    private val placed = Path()
    private val move = Matrix()

    /** Whether the digits are rolling to a new minute, so what is drawn now is not yet what the time will show. */
    val isChanging: Boolean get() = roll != null

    fun setText(text: String) {
        if (this.text == text) return
        val before = this.text
        val beforeGlyphs = glyphs
        val beforeWidth = measuredTextWidth()
        this.text = text
        relayout()
        if (before.isNotEmpty() && isAttachedToWindow && isShown) {
            // The view stays centred, so glyphs laid out for the old width sit this far off in the new one.
            startRoll(Roll(beforeGlyphs, IosDigitTransition.between(before, text), (measuredTextWidth() - beforeWidth) / 2))
        }
    }

    fun setSize(size: Float) {
        this.size = size
        finishRoll()
        relayout()
    }

    fun setFont(font: IosNumeralFont) {
        if (this.font === font) return
        this.font = font
        finishRoll()
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
        val width = ceil(measuredTextWidth()).toInt()
        val height = ceil(numeralHeight()).toInt()
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        finishRoll()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate((width - measuredTextWidth()) / 2, 0f)
        val current = roll
        if (current == null) {
            glyphs.forEach { draw(canvas, it.path, 0f, 0f, 1f) }
        } else {
            drawRoll(canvas, current)
        }
        canvas.restore()
    }

    /**
     * Draws the time part of the way through [roll]: a digit that stays glides from its old place to its
     * new one; an old digit that changes rises out of its place and fades, while the new one comes up from
     * below and fades in; both move sideways with the digits that stay.
     */
    private fun drawRoll(canvas: Canvas, roll: Roll) {
        val eased = ROLL_EASING.getInterpolation(roll.progress)
        val rise = numeralHeight() * ROLL_RISE
        roll.places.forEach { place ->
            val old = place.before?.let(roll.before::get)
            val new = place.after?.let(glyphs::get)
            // An old glyph laid out for the old width sits [Roll.shift] further right in the new one.
            val oldX = old?.let { it.left + roll.shift }
            // How far the place is from its new left across the roll: from its old left to none.
            val glide = if (oldX != null && new != null) (oldX - new.left) * (1f - eased) else 0f
            if (!place.changes) {
                new?.let { draw(canvas, it.path, glide, 0f, 1f) }
                return@forEach
            }
            if (old != null) {
                val sideways = if (new != null) new.left + glide - old.left else roll.shift
                draw(canvas, old.path, sideways, -rise * eased, 1f - smoothstep(roll.progress / OUT_BY))
            }
            if (new != null) draw(canvas, new.path, glide, rise * (1f - eased), smoothstep((roll.progress - IN_FROM) / (1f - IN_FROM)))
        }
    }

    /** Draws [shape], moved by [dx] and [dy], filled or in outline as the doze asks, as opaque as [alpha] makes it. */
    private fun draw(canvas: Canvas, shape: Path, dx: Float, dy: Float, alpha: Float) {
        if (alpha <= 0f) return
        val path = if (dx == 0f && dy == 0f) {
            shape
        } else {
            move.setTranslate(dx, dy)
            placed.set(shape)
            placed.transform(move)
            placed
        }
        if (outline < 1f) {
            paint.color = withAlpha(color, (1f - outline) * alpha)
            canvas.drawPath(path, paint)
        }
        if (outline > 0f) {
            canvas.saveLayerAlpha(null, (outline * alpha * OPAQUE).roundToInt())
            outlinePaint.color = color
            canvas.drawPath(path, outlinePaint)
            canvas.drawPath(path, insidePaint)
            canvas.restore()
        }
    }

    private fun startRoll(next: Roll) {
        rolling?.cancel()
        roll = next
        rolling = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ROLL_MS
            addUpdateListener {
                next.progress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (roll === next) finishRoll()
                }
            })
            start()
        }
    }

    private fun finishRoll() {
        roll = null
        rolling?.let {
            rolling = null
            it.cancel()
        }
        invalidate()
    }

    private fun measuredTextWidth(): Float = paint.measureText(text)

    private fun numeralHeight(): Float = (numeralBottom - numeralTop) * stretch

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

        layOutGlyphs()
        requestLayout()
        invalidate()
    }

    /** Lays each character out as a glyph: the digits stretched, the colon as its two dots between them. */
    private fun layOutGlyphs() {
        val stretching = Matrix().apply { setTranslate(0f, -numeralTop); postScale(1f, stretch) }
        val colon = text.indexOf(COLON)
        glyphs = text.indices.map { index ->
            val left = paint.measureText(text, 0, index)
            val path = Path()
            if (index == colon) {
                if (colon > 0 && colon < text.length - 1) addColon(path, colon)
            } else {
                paint.getTextPath(text, index, index + 1, left, 0f, path)
                path.transform(stretching)
            }
            Glyph(path, left)
        }
    }

    /**
     * Two round dots, the size of the font's own, centred in the gap between the
     * last digit before the colon and the first after it, and placed evenly
     * above and below the middle of the numerals.
     */
    private fun addColon(path: Path, colon: Int) {
        paint.getTextBounds(COLON_TEXT, 0, 1, bounds)
        val radius = bounds.width() / 2f

        paint.getTextBounds(text, 0, colon, bounds)
        val before = bounds.right
        val afterStart = paint.measureText(text, 0, colon + 1)
        paint.getTextBounds(text, colon + 1, text.length, bounds)
        val after = afterStart + bounds.left
        val centreX = (before + after) / 2

        val height = numeralHeight()
        val offset = height * COLON_DOT_OFFSET
        path.addCircle(centreX, height / 2 - offset, radius, Path.Direction.CW)
        path.addCircle(centreX, height / 2 + offset, radius, Path.Direction.CW)
    }

    private companion object {
        const val COLON = ':'
        const val COLON_TEXT = ":"
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
        const val OPAQUE = 255

        /** Each dot's distance from the numerals' middle, against their height, as iOS sets it. */
        const val COLON_DOT_OFFSET = 0.2f

        /** How long the digits take to roll to a new minute. */
        const val ROLL_MS = 600L

        /** How far a rolling digit rises, against the numerals' height. */
        const val ROLL_RISE = 0.45f

        /** Share of the roll by which an old digit has faded out. */
        const val OUT_BY = 0.55f

        /** Share of the roll after which a new digit starts to fade in. */
        const val IN_FROM = 0.15f

        /** Material's emphasized easing: a gentle start, most of the way early, and a long settle. */
        val ROLL_EASING = PathInterpolator(0.2f, 0f, 0f, 1f)

        fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
