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
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DigitRoll
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk
import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * The iOS time on one line, as the iOS lock screen draws it.
 *
 * Set at one text size ([IosClockScale]) with the font's own spacing and
 * kerning, centred on its ink, so its width follows the figures as on iOS.
 * The size chosen stretches the numerals taller, all by the same amount, as
 * the iOS 26 clock stretches: tops and bottoms keep their shape and the
 * middle grows ([TallFigures]); the colon's dots stay round, moved apart
 * with the figures. The view is exactly as tall as the numerals, so the date
 * sits right above them. A new minute replaces the digits at once, as iOS
 * does; the always-on display keeps the time filled, as iOS does.
 *
 * Set in the owner's own font where there is one on the phone ([IosClockFont]):
 * iOS 26's clock font draws its figures tall through its own `HGHT` axis,
 * strokes and colon as iOS draws them, so none of what follows is needed.
 * Otherwise in Inter's Display cut, the free counterpart of the SF Pro Display
 * iOS uses, at the weight the width slider chooses; where Inter cannot be
 * loaded, in the Pixel's own numerals.
 * Stretching a figure tall thickens its horizontal strokes with it while its
 * vertical ones stay as they were, which drew bloated bars over hairline stems.
 * A figure stretched more than [EVEN_FROM] is therefore drawn from Inter's
 * thinnest weight, stretched, and thickened evenly all round until its stems
 * are as thick as the chosen weight's ([evenStrokes]), so every stroke of a
 * tall figure stands about as thick as every other, as on iOS's tall clock.
 */
internal class IosTimeView(context: Context) : View(context) {

    /** The owner's font or Inter, or null where neither loads and the Pixel's numerals stand in. */
    private val font = IosClockFont.font(context)

    /** Whether the font makes its own figures tall (`HGHT`), rather than this view stretching them. */
    private val adaptive = font?.adaptive == true

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font?.typeface ?: FlexClockNumerals.typeface
        fontFeatureSettings = FlexClockNumerals.FEATURES
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
    }

    /** The same figures at Inter's thinnest weight, drawn for a stretched figure before it is thickened ([evenStrokes]). */
    private val thinPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Thickens a stretched thin figure all round ([evenStrokes]). */
    private val thickening = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    /** How much thicker the chosen weight's stems are than the thinnest weight's, at the text size laid out; 0 without Inter. */
    private var stemGain = 0f

    private val thinInk = RectF()
    private val thick = Path()

    private val ink = DozeInk(context)

    private val metrics = context.resources.displayMetrics
    private val shortSide = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
    private val bounds = Rect()
    private val stretching = Matrix()
    private val scratch = Path()
    private val glyphInk = RectF()

    /** The time as laid out, a glyph per character. */
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

    /** The time's ink: how wide, and where its centre falls from the first character's origin. */
    private var inkWidth = 0f
    private var inkCentre = 0f

    /** Where each character's origin goes: the font's own advances and kerning. */
    private var origins = FloatArray(0)

    /** How tall the numerals are drawn, stretch included, in the view's own pixels. */
    private val numeralHeight: Float get() = (numeralBottom - numeralTop) * stretch

    init {
        relayout()
    }

    fun setText(text: String) {
        if (this.text == text) return
        this.text = text
        // The cut, the text size and the stretch do not depend on the text: a new minute only places its characters.
        layOutAdvances()
        requestLayout()
        invalidate()
        glyphsPending = false
        digits.replace(layOutGlyphs())
    }

    /** Sets how much smaller than laid out the host draws the time, so its height is chosen as drawn. */
    fun setDrawnScale(scale: Float) {
        if (scale <= 0f || abs(drawnScale - scale) < SCALE_STEP) return
        drawnScale = scale
        relayoutAndReplace()
    }

    fun setSize(size: Float) {
        glideTo(size, targetWidth)
    }

    /** Lays the time out at [size] now, ending any glide, so it can be measured there. */
    fun setSizeAtOnce(size: Float) {
        glide?.cancel()
        glide = null
        targetSize = size
        this.size = size
        faceWidth = targetWidth
        // Only measured, as adaptive size tries one size after another: the glyphs, whose even strokes cost a
        // path union each, are built once, for the size the search settles on, before the next frame.
        relayout()
        if (!glyphsPending) {
            glyphsPending = true
            post(buildGlyphs)
        }
    }

    /** Whether the glyphs wait to be built for a size set at once ([setSizeAtOnce]). */
    private var glyphsPending = false
    private val buildGlyphs = Runnable {
        if (glyphsPending) {
            glyphsPending = false
            digits.replace(layOutGlyphs())
        }
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Room for anti-aliasing round the ink, which is drawn centred so the view holds it whole however the bearings fall.
        val width = ceil(inkWidth).toInt() + 2 * outlineInset
        val height = ceil(numeralHeight).toInt() + 2 * outlineInset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // A resize under way lands where it was going.
        glide?.end()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.translate(width / 2f - inkCentre, outlineInset.toFloat())
        digits.draw(canvas, 0f)
        canvas.restore()
    }

    private val outlineInset: Int get() = ink.inset

    private fun relayoutAndReplace() {
        relayout()
        glyphsPending = false
        digits.replace(layOutGlyphs())
    }

    /** Sets the cut, the text size and the stretch for the current size and width ([IosClockScale]), and lays the time out. */
    private fun relayout() {
        paint.textSize = PROBE_SIZE
        val weight = FlexClockNumerals.weightFor(faceWidth)
        paint.fontVariationSettings = when {
            adaptive -> IosClockFont.adaptiveVariation(weight, 1f)
            font != null -> IosClockFont.variation(weight)
            else -> FlexClockNumerals.variationFor(faceWidth)
        }
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)
        // Heights are chosen as drawn, so the preview, drawn at its laid-out size, matches the lock screen.
        // Read from the outline: the variable font's text bounds do not match what is drawn.
        paint.getTextPath(IosClockScale.REFERENCE, 0, IosClockScale.REFERENCE.length, 0f, 0f, scratch)
        scratch.computeBounds(glyphInk, true)
        val fit = IosClockScale.fit(size, shortSide / drawnScale, bounds.height() / PROBE_SIZE, glyphInk.width() / PROBE_SIZE)
        stretch = fit.stretch
        if (adaptive) {
            // The font draws the figures tall itself, from the same top; only what its axis cannot reach is left to stretch.
            val height = fit.stretch.coerceIn(1f, IosClockFont.MAX_HEIGHT)
            paint.fontVariationSettings = IosClockFont.adaptiveVariation(weight, height)
            paint.getTextBounds(NUMERALS, 0, NUMERALS.length, bounds)
            stretch = fit.stretch / height
        }
        val toSize = fit.textSize / PROBE_SIZE
        numeralTop = bounds.top * toSize
        numeralBottom = bounds.bottom * toSize
        paint.textSize = fit.textSize
        measureStems()
        layOutAdvances()
        requestLayout()
        invalidate()
    }

    /** Lays each character out as a glyph: the digits stretched from the numerals' top ([tall]); the colon as two round dots. */
    private fun layOutGlyphs(): List<DigitRoll.Glyph> = text.indices.map { index ->
        val left = origins[index]
        val path = Path()
        if (text[index] == COLON && !adaptive) {
            addColon(path, index, left)
        } else {
            paint.getTextPath(text, index, index + 1, left, 0f, path)
            stretching.setTranslate(0f, -numeralTop)
            if (stretch > EVEN_FROM && stemGain > 0f) {
                evenStrokes(path, index, left)
            } else {
                path.transform(stretching)
                tall(path)
            }
        }
        DigitRoll.Glyph(path, left)
    }

    /** Makes [path], its top at 0, [stretch] times as tall, its top and bottom kept as drawn ([TallFigures]). */
    private fun tall(path: Path) {
        if (stretch == 1f) return
        TallFigures.stretch(path, 0f, numeralBottom - numeralTop, stretch, tallPath)
        path.set(tallPath)
    }

    private val tallPath = Path()

    /**
     * Reads how much thicker the chosen weight's stems are than Inter's thinnest, at the text size laid
     * out, from the ink of a capital I, which is a stem and nothing else.
     */
    private fun measureStems() {
        if (font == null || adaptive) {
            stemGain = 0f
            return
        }
        thinPaint.set(paint)
        thinPaint.fontVariationSettings = IosClockFont.variation(THINNEST)
        paint.getTextPath(STEM, 0, 1, 0f, 0f, scratch)
        scratch.computeBounds(glyphInk, true)
        val chosen = glyphInk.width()
        thinPaint.getTextPath(STEM, 0, 1, 0f, 0f, scratch)
        scratch.computeBounds(glyphInk, true)
        stemGain = (chosen - glyphInk.width()).coerceAtLeast(0f)
    }

    /**
     * Writes into [path], which holds the figure at [index] at the chosen weight, the same figure from
     * Inter's thinnest weight, centred on the chosen one's ink, stretched ([tall]), and
     * thickened by [stemGain] all round, so its stems are the chosen weight's and its bars, though
     * stretched, near them.
     */
    private fun evenStrokes(path: Path, index: Int, left: Float) {
        path.computeBounds(glyphInk, true)
        thinPaint.getTextPath(text, index, index + 1, left, 0f, scratch)
        scratch.computeBounds(thinInk, true)
        scratch.offset(glyphInk.centerX() - thinInk.centerX(), 0f)
        scratch.transform(stretching)
        tall(scratch)
        thickening.strokeWidth = stemGain
        thick.reset()
        thickening.getFillPath(scratch, thick)
        thick.op(scratch, Path.Op.UNION)
        path.set(thick)
    }

    /** Places each character at the font's own advance, kerning included, and reads where the whole time's ink falls. */
    private fun layOutAdvances() {
        if (origins.size != text.length) origins = FloatArray(text.length)
        var inkLeft = Float.MAX_VALUE
        var inkRight = -Float.MAX_VALUE
        for (i in text.indices) {
            origins[i] = paint.getRunAdvance(text, 0, text.length, 0, text.length, false, i)
            // Read from the outlines themselves: the variable font's text bounds do not match what is drawn.
            paint.getTextPath(text, i, i + 1, 0f, 0f, scratch)
            scratch.computeBounds(glyphInk, true)
            inkLeft = minOf(inkLeft, origins[i] + glyphInk.left)
            inkRight = maxOf(inkRight, origins[i] + glyphInk.right)
        }
        inkWidth = if (text.isEmpty()) 0f else inkRight - inkLeft
        inkCentre = if (text.isEmpty()) 0f else (inkLeft + inkRight) / 2
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

        /** Stretched more than this, a figure is drawn thin and thickened evenly ([evenStrokes]). */
        const val EVEN_FROM = 1.05f

        /** Inter's thinnest weight. */
        const val THINNEST = 100f

        /** A capital I: a stem and nothing else, whose ink width is the weight's stem. */
        const val STEM = "I"

        /** As SystemUI's own clocks resize: the expressive scheme's default spatial spring. */
        val RESIZE = ExpressiveSpring.DEFAULT_SPATIAL

        const val NUMERALS = "0123456789"
        // Large, so the whole-pixel numeral bounds read at it scale to any size within a fraction of a pixel.
        const val PROBE_SIZE = 1000f

        /** A drawn scale closer than this to the one in use changes nothing, so rounding never relays the time out. */
        const val SCALE_STEP = 0.005f
    }
}
