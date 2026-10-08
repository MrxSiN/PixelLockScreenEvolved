package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

import kotlin.math.abs
import kotlin.math.ceil

import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DigitRoll
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk
import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * The iOS time on one line, set in the Pixel clock's own numerals
 * ([FlexClockNumerals]).
 *
 * The time always spans the lock screen's text column, from the
 * edge the date row and the smartspace card start at to the one they end at:
 * every minute is set to that width. Its height is the size chosen
 * ([IosClockScale]), from the Pixel's own large clock upward, and the
 * numerals are drawn that tall by stretching them vertically; the colon's dots
 * stay round, stretched apart with the figures. Every character's ink stands
 * the same gap from the next ([layOutInk]). The small face, beside notifications, is the smallest size: the
 * same column, the stock large clock's height. The view is exactly as tall as
 * the numerals, so the date sits right above them.
 *
 * The time is laid out once as a path per character, whenever the text, size
 * or width change, so it can be filled awake and traced in outline on the
 * always-on display ([setOutline]) from the same shapes. The paths are
 * stretched rather than the canvas, so the outline ([DozeInk]) keeps one width
 * around every stroke. When the minute turns, the digits that change roll, as
 * on iOS ([DigitRoll], [isChanging]).
 */
internal class IosTimeView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = FlexClockNumerals.typeface
        fontFeatureSettings = FlexClockNumerals.FEATURES
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    private val ink = DozeInk(context)

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()
    private val stretching = Matrix()
    private val scratch = Path()
    private val glyphInk = RectF()

    /** The time as laid out, a glyph per character, and its roll to each new minute. */
    private val digits = DigitRoll(this, ink)

    private var text = ""
    private var size = 0f
    private var faceWidth = AdjustableWidthClockFace.DEFAULT_WIDTH

    /** Where a resize ([glideTo]) is headed, and the animator taking the time there. */
    private var targetSize = 0f
    private var targetWidth = AdjustableWidthClockFace.DEFAULT_WIDTH
    private var glide: ValueAnimator? = null
    private var stretch = 1f

    /** How much smaller than laid out the host draws the time ([setDrawnScale]). */
    private var drawnScale = 1f

    /** Numeral box relative to the baseline, before the stretch. */
    private var numeralTop = 0f
    private var numeralBottom = 0f

    /** The time's ink: how wide, and where it is centred from the start of its first character, which it is drawn centred on. */
    private var inkWidth = 0f
    private var inkCentre = 0f

    /** Where each character's origin goes, so the ink of every pair stands one gap apart ([layOutInk]). */
    private var origins = FloatArray(0)

    private var laidOutForScreenWidth = 0

    /**
     * The width the time spans, in its own pixels: the screen's width less
     * [EdgeToEdgeClockFace.EDGE_DP] at either edge, as drawn. The lock screen lays the clock out
     * narrower than that; the face lets the time run past its box.
     */
    private val columnWidth: Float
        get() = (screenWidth - 2 * EdgeToEdgeClockFace.EDGE_DP * metrics.density) / drawnScale

    /** The width of the screen the face is shown on: its window's, which in a preview is not the device's. */
    private val screenWidth: Int
        get() = rootView.width.takeIf { it > 0 && isAttachedToWindow } ?: metrics.widthPixels

    /** How tall the numerals are drawn, stretch included, in the view's own pixels. */
    private val numeralHeight: Float get() = (numeralBottom - numeralTop) * stretch

    init {
        relayout()
    }

    /** Whether the digits are rolling to a new minute, so what is drawn now is not yet what the time will show. */
    val isChanging: Boolean get() = digits.isChanging

    fun setText(text: String) {
        if (this.text == text) return
        val before = this.text
        val beforeCentre = inkCentre
        this.text = text
        // Every minute is set to the column's width, so its text size may change a little with it.
        relayout()
        // The view stays centred, so glyphs laid out for the old time sit this far off in the new one.
        digits.show(before, text, layOutGlyphs(), inkCentre - beforeCentre)
    }

    /** Sets how much smaller than laid out the host draws the time, so it spans the column as drawn. */
    fun setDrawnScale(scale: Float) {
        if (scale <= 0f || abs(drawnScale - scale) < SCALE_STEP) return
        drawnScale = scale
        relayoutAndReplace()
    }

    fun setSize(size: Float) {
        glideTo(size, targetWidth)
    }

    fun setWidth(width: Float) {
        if (targetWidth == width) return
        glideTo(targetSize, width)
    }

    /**
     * Moves to [size] and [width] on the expressive spatial spring while the
     * time is on screen, as Wallpaper & style's size and width sliders step,
     * so a step grows or narrows the time rather than swapping it; off screen,
     * or with animations off, at once.
     */
    private fun glideTo(size: Float, width: Float) {
        targetSize = size
        targetWidth = width
        glide?.cancel()
        glide = null
        if (!isShown || text.isEmpty() || SystemMotion.isReduced) {
            this.size = size
            faceWidth = width
            relayoutAndReplace()
            return
        }
        val fromSize = this.size
        val fromWidth = faceWidth
        glide = RESIZE.animator(
            update = { seconds ->
                val t = RESIZE.valueAt(seconds)
                this.size = fromSize + (size - fromSize) * t
                faceWidth = fromWidth + (width - fromWidth) * t
                relayoutAndReplace()
            },
            end = {
                if (glide != null) {
                    this.size = size
                    faceWidth = width
                    relayoutAndReplace()
                    glide = null
                }
            },
        ).apply { start() }
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
        // The ink is drawn centred, so the view holds it whole however the bearings fall.
        val width = ceil(inkWidth).toInt() + 2 * outlineInset
        val height = ceil(numeralHeight).toInt() + 2 * outlineInset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // The window's width is known only once attached; set the time again for it, after this layout pass.
        if (screenWidth != laidOutForScreenWidth) post {
            if (screenWidth != laidOutForScreenWidth) relayoutAndReplace()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digits.finish()
        // A resize under way lands where it was going.
        glide?.end()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(width / 2f - inkCentre, outlineInset.toFloat())
        digits.draw(canvas, numeralHeight * ROLL_RISE)
        canvas.restore()
    }

    /** Room for half the AOD outline plus one anti-aliasing pixel. */
    private val outlineInset: Int get() = ink.inset

    private fun relayoutAndReplace() {
        relayout()
        digits.replace(layOutGlyphs())
    }

    /**
     * Sets the cut, the text size and the stretch for the current time, size
     * and width: the text size at which this time's ink spans the column, and
     * the stretch that draws its numerals as tall as the size asks
     * ([IosClockScale]).
     */
    private fun relayout() {
        paint.textSize = PROBE_SIZE
        paint.fontVariationSettings = FlexClockNumerals.variationFor(faceWidth)
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)
        val numeralPerTextSize = bounds.height() / PROBE_SIZE
        // Heights are chosen as drawn, like the width, so the preview, drawn at its laid-out size, matches the lock screen.
        val drawnShortSide = shortSide / drawnScale
        // Outlines scale with the text size, so the ink is laid out once, at the probe size, and scaled.
        val probeInk = layOutInk()
        val fit = if (text.isNotEmpty()) {
            IosClockScale.spanning(size, drawnShortSide, numeralPerTextSize, probeInk / PROBE_SIZE, columnWidth)
        } else {
            IosClockScale.unstretched(IosClockScale.STOCK_NUMERAL_TO_SHORT_SIDE, drawnShortSide, numeralPerTextSize)
        }
        paint.textSize = fit.textSize
        stretch = fit.stretch
        laidOutForScreenWidth = screenWidth

        val toSize = fit.textSize / PROBE_SIZE
        numeralTop = bounds.top * toSize
        numeralBottom = bounds.bottom * toSize
        for (i in origins.indices) origins[i] *= toSize
        inkWidth = probeInk * toSize
        inkCentre = inkWidth / 2

        requestLayout()
        invalidate()
    }

    /** Lays each character out as a glyph: the digits stretched from the numerals' top, the colon as two round dots. */
    private fun layOutGlyphs(): List<DigitRoll.Glyph> {
        stretching.setTranslate(0f, -numeralTop)
        stretching.postScale(1f, stretch)
        return text.indices.map { index ->
            val left = origins[index]
            val path = Path()
            if (text[index] == COLON) {
                addColon(path, index, left)
            } else {
                paint.getTextPath(text, index, index + 1, left, 0f, path)
                path.transform(stretching)
            }
            DigitRoll.Glyph(path, left)
        }
    }

    /**
     * Places each character so the ink of every neighbouring pair stands the
     * same gap apart, the space the font leaves between two zeros, at the
     * paint's current size; the first character's ink starts at 0. The font's
     * proportional figures carry side bearings of their own (a 1 has far more
     * room around it than a 4), which left the minutes visibly tighter than the
     * hour. Returns how wide the ink is.
     */
    private fun layOutInk(): Float {
        if (origins.size != text.length) origins = FloatArray(text.length)
        // Read from the outlines themselves: the variable font's text bounds do not match what is drawn.
        inkOf(ZERO, 0)
        val gap = paint.measureText(ZERO) - glyphInk.width()
        var x = 0f
        for (i in text.indices) {
            inkOf(text, i)
            origins[i] = x - glyphInk.left
            x += glyphInk.width() + gap
        }
        return if (text.isEmpty()) 0f else x - gap
    }

    /** Writes the ink of [string]'s character at [index], drawn from 0, into [glyphInk]. */
    private fun inkOf(string: String, index: Int) {
        paint.getTextPath(string, index, index + 1, 0f, 0f, scratch)
        scratch.computeBounds(glyphInk, true)
    }

    /**
     * The colon as the font draws it, its dots kept round: each dot keeps the
     * font's radius, its centre moved where the stretch moves it.
     */
    private fun addColon(path: Path, index: Int, left: Float) {
        paint.getTextBounds(text, index, index + 1, bounds)
        val radius = bounds.width() / 2f
        val centreX = left + bounds.exactCenterX()
        path.addCircle(centreX, (bounds.top + radius - numeralTop) * stretch, radius, Path.Direction.CW)
        path.addCircle(centreX, (bounds.bottom - radius - numeralTop) * stretch, radius, Path.Direction.CW)
    }

    private companion object {
        const val COLON = ':'
        const val ZERO = "0"

        /** As SystemUI's own clocks resize: the expressive scheme's default spatial spring. */
        val RESIZE = ExpressiveSpring.DEFAULT_SPATIAL

        /**
         * How far a rolling digit travels, against the numerals' height: less
         * than other faces' ([DigitRoll.RISE]), as these numerals can be several
         * times the stock clock's height, and a digit rising half of that ran
         * far past the time and was cut off flat above it.
         */
        const val ROLL_RISE = 0.15f
        const val NUMERALS = "0123456789"
        // Large, so the whole-pixel numeral bounds read at it scale to any size within a fraction of a pixel.
        const val PROBE_SIZE = 1000f


        /** A drawn scale closer than this to the one in use changes nothing, so rounding never relays the time out. */
        const val SCALE_STEP = 0.005f
    }
}
