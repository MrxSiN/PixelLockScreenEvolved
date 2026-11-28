package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.animation.PathInterpolator

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * One line of numerals in a [FlexCut], as tall as a face asks ([fit]).
 *
 * It moves as Google's own clocks move on Android 17, through the font's axes
 * rather than by fading: going into the always-on display its strokes thin to a
 * light cut ([setDoze]) so few pixels stay lit, and coming back they fill out
 * again; appearing on the lock screen they grow from that light cut
 * ([playEnter]); plugging in the charger they thin and fill once
 * ([playCharge]). When the minute turns, the digits that change roll
 * ([DigitRoll]). The text size stays that of the awake cut throughout, so the
 * line keeps its size as its strokes change.
 *
 * The view is exactly as tall as the numerals, without the font's ascent and
 * descent, so faces can stack lines and rows as tightly as their design wants.
 * A face that wants numerals taller than the room across allows lets them be
 * drawn stretched ([fit]); the glyphs themselves are scaled, not the canvas, so
 * the always-on outline keeps one width around every stroke.
 * Where it is given more width than the text needs, [gravity] says where the
 * text sits in it; it is also the side it stays put on as its width changes.
 */
class NumeralView(context: Context, private var cut: FlexCut) : View(context) {

    /** In the awake cut, always: it measures and places the line. */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = FlexCut.typeface }

    /**
     * In the cut the strokes are drawn in now ([shownCut]). A paint of its own, so neither is switched to the
     * other's cut and back on every step of a doze: each switch builds a new varied typeface, and four lines doing
     * three each took Headline's doze to 14 ms a step on the lock screen's main thread (MEASURED with atrace).
     */
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = FlexCut.typeface }
    private val ink = DozeInk(context)
    private val digits = DigitRoll(this, ink)
    private val numerals = Rect()
    private val stretching = Matrix()

    private var text = ""
    private var numeralHeight = 0f
    private var maxWidth = Float.MAX_VALUE
    private var maxStretch = 1f

    /** Space between the figures, in ems ([setTracking]). */
    private var tracking = 0f

    /** How much taller than the font designs them the numerals are drawn, from 1 ([fit]). */
    private var stretch = 1f

    /** How far into the always-on display, from 0 awake to 1 dozing. */
    private var doze = 0f

    /** How far an enter or charge animation holds the strokes toward the light cut, from 0 to 1. */
    private var lift = 0f
    private var pulse: android.animation.Animator? = null

    /** The awake and the light cut's glyphs for the text and size set now ([glyphs]); null until asked for again. */
    private var ends: Pair<List<DigitRoll.Glyph>, List<DigitRoll.Glyph>>? = null

    /**
     * The glyphs between [ends], their paths written over on each step of a doze or a pulse rather than made
     * anew; built with the ends, so a list a minute's roll still holds is never written over.
     */
    private var tweened: List<DigitRoll.Glyph>? = null

    /**
     * Whether the always-on display traces the awake cut in [DozeInk]'s thin outline rather than
     * thinning its strokes: for figures so narrow that the light cut, kept on their wide advances,
     * would stand apart as hairlines. On by default for every cut narrower than [OUTLINE_BELOW_WIDTH]:
     * Headline's and Cinema's figures, thinned to weight 200 at their bold advances, fell apart on the
     * always-on display into widely spaced strokes.
     */
    var dozesAsOutline: Boolean = cut.width < OUTLINE_BELOW_WIDTH

    /** [Gravity.START], [Gravity.CENTER_HORIZONTAL] or [Gravity.END]. */
    var gravity: Int = Gravity.CENTER_HORIZONTAL
        set(value) {
            field = value
            invalidate()
        }

    fun setText(text: String) {
        if (this.text == text) return
        val before = this.text
        val beforeWidth = widthOf(before)
        this.text = text
        size()
        digits.show(before, text, glyphs(), shiftFor(widthOf(text) - beforeWidth))
        requestLayout()
    }

    /** Sets the awake cut, reshaping the glyphs in place so a roll under way carries on. */
    fun setCut(cut: FlexCut) {
        if (this.cut == cut) return
        this.cut = cut
        size()
        restroke()
        requestLayout()
    }

    /**
     * Sets the numerals [numeralHeight] pixels tall, or smaller where the widest
     * time this line can show would be wider than [maxWidth]. Where the width
     * caps the size, the numerals are drawn up to [maxStretch] times taller than
     * the font designs them to reach the height anyway; left at 1 they are drawn
     * as designed and the line is simply shorter.
     */
    fun fit(numeralHeight: Float, maxWidth: Float = Float.MAX_VALUE, maxStretch: Float = 1f) {
        this.numeralHeight = numeralHeight
        this.maxWidth = maxWidth
        this.maxStretch = maxStretch
        resize()
    }

    fun setColor(color: Int) {
        ink.color = color
        invalidate()
    }

    /**
     * Sets extra space between the figures, in ems. Roboto Flex's heaviest, narrowest figures are
     * wider than the advance it gives them, so set tight they run into each other.
     */
    fun setTracking(em: Float) {
        if (tracking == em) return
        tracking = em
        resize()
    }

    /** Inks the numerals awake with a fill and an outline as opaque as [fill] and [outline] say ([DozeInk.awakeFill]). */
    fun setInk(fill: Float, outline: Float, outlineDp: Float = DozeInk.OUTLINE_DP) {
        ink.awakeFill = fill
        ink.awakeOutline = outline
        ink.awakeOutlineDp = outlineDp
        requestLayout()
        invalidate()
    }

    /** How opaque the awake fill is, from 0 to 1, without laying the line out again: for a fill that animates. */
    fun setFill(fill: Float) {
        ink.awakeFill = fill
        invalidate()
    }

    /** Thins the strokes toward the always-on display's light cut, or its outline ([dozesAsOutline]), from 0 awake to 1 dozing. */
    fun setDoze(fraction: Float) {
        val next = fraction.coerceIn(0f, 1f)
        if (next == doze) return
        val before = thinned()
        doze = next
        if (dozesAsOutline) ink.outline = next
        // An outlined cut keeps its strokes through the doze; only the ink changes, and the glyphs stand.
        if (thinned() != before) restroke() else invalidate()
    }

    /** Grows the strokes from the light cut to the awake one, as the lock screen appears. */
    fun playEnter() = play(
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = ENTER_MS
            interpolator = EMPHASIZED_DECELERATE
        },
    )

    /** Thins the strokes and fills them again, once, as the charger is plugged in. */
    fun playCharge() = play(
        AnimatorSet().apply {
            playSequentially(
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = CHARGE_OUT_MS
                    interpolator = EMPHASIZED_ACCELERATE
                },
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = CHARGE_BACK_MS
                    interpolator = EMPHASIZED_DECELERATE
                },
            )
        },
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = ceil(widthOf(text)).toInt() + 2 * ink.inset
        val height = ceil(drawnHeight()).toInt() + 2 * ink.inset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digits.finish()
        pulse?.end()
    }

    override fun onDraw(canvas: Canvas) {
        val room = width - 2 * ink.inset - widthOf(text)
        val start = when (horizontalGravity()) {
            Gravity.LEFT -> 0f
            Gravity.RIGHT -> room
            else -> room / 2
        }
        canvas.save()
        canvas.translate(ink.inset + start, ink.inset - numerals.top * stretch)
        digits.draw(canvas, drawnHeight() * DigitRoll.RISE)
        canvas.restore()
    }

    /**
     * The space between figures, in pixels at the text size set now. Added here rather than through the paint's
     * letter spacing, which Android measures and places differently, so the line is drawn exactly as wide as measured.
     */
    private fun spacing(): Float = tracking * paint.textSize

    /** How wide [text] is set, the space between its figures included. */
    private fun widthOf(text: String): Float = paint.measureText(text) + spacing() * max(0, text.length - 1)

    /** How tall the numerals are drawn, the stretch included. */
    private fun drawnHeight(): Float = numerals.height() * stretch

    private fun play(animation: android.animation.Animator) {
        pulse?.cancel()
        lift = 0f
        if (!isAttachedToWindow || !isShown) return restroke()
        pulse = animation
        val parts = (animation as? AnimatorSet)?.childAnimations ?: listOf(animation)
        parts.filterIsInstance<ValueAnimator>().forEach { part ->
            part.addUpdateListener {
                lift = it.animatedValue as Float
                restroke()
            }
        }
        animation.start()
    }

    /** How far glyphs laid out for a text [grown] pixels narrower sit off in the new layout, by which side stays put. */
    private fun shiftFor(grown: Float): Float = when (horizontalGravity()) {
        Gravity.LEFT -> 0f
        Gravity.RIGHT -> grown
        else -> grown / 2
    }

    private fun horizontalGravity(): Int = Gravity.getAbsoluteGravity(gravity, layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK

    /** How far the strokes are thinned toward the light cut now, by the doze or a pulse, from 0 to 1. */
    private fun thinned(): Float = max(if (dozesAsOutline) 0f else doze, lift)

    /** The light cut the strokes thin toward. */
    private fun lightCut(): FlexCut = cut.copy(weight = min(cut.weight, DOZE_WEIGHT))

    /** The cut the strokes are drawn in now: the awake cut, thinned as far as the doze or a pulse takes it. */
    private fun shownCut(): FlexCut = cut.toward(lightCut(), thinned())

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut, and lays the numerals out again at once. */
    private fun resize() {
        size()
        digits.replace(glyphs())
        requestLayout()
    }

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut. */
    private fun size() {
        ends = null
        tweened = null
        paint.fontVariationSettings = null
        paint.fontVariationSettings = cut.settings
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, numerals)
        val numeralPerSize = numerals.height() / PROBE_SIZE
        // Every digit measured as the widest, so the line keeps one size whichever minute it shows.
        val widest = text.map { if (it.isDigit()) WIDE_DIGIT else it }.joinToString("")
        val widthPerSize = widthOf(widest) / PROBE_SIZE
        val fit = StretchFit.of(numeralHeight, maxWidth, numeralPerSize, widthPerSize, maxStretch)
        stretch = fit.stretch
        paint.textSize = fit.textSize.coerceAtLeast(1f)
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, numerals)
    }

    /** Lays the glyphs out again in the cut shown now, keeping the size and any roll under way. */
    private fun restroke() {
        digits.update(glyphs())
        invalidate()
    }

    /**
     * The glyphs of [text] in the cut shown now. Between the awake and the light cut they are the two cuts'
     * outlines interpolated: a variable font's outlines share one structure at every weight, and building a
     * varied typeface and its glyphs for each step of a doze or a pulse kept the lock screen's main thread
     * from drawing every frame (One's doze cost 3.6 ms a step, MEASURED with atrace). Where the outlines do
     * not interpolate, the shown cut is set as it was.
     */
    private fun glyphs(): List<DigitRoll.Glyph> {
        // Both ends are built as the text or size changes, once a minute, so a doze never starts with building them.
        val (awake, light) = ends ?: (glyphsIn(cut) to glyphsIn(lightCut())).also { ends = it }
        val t = thinned()
        if (t <= 0f) return awake
        if (t >= 1f) return light
        val glyphs = tweened ?: List(awake.size) { DigitRoll.Glyph(Path(), awake[it].left) }.also { tweened = it }
        for (i in awake.indices) {
            // The weight is this path's share, not the other's (MEASURED: at 0.98 it gave the awake outline).
            if (!awake[i].path.interpolate(light[i].path, 1f - t, glyphs[i].path)) return glyphsIn(shownCut())
        }
        return glyphs
    }

    /** The glyphs of [text] in [shown], placed by the awake cut's advances so nothing shifts. */
    private fun glyphsIn(shown: FlexCut): List<DigitRoll.Glyph> {
        val advances = FloatArray(text.length) { paint.measureText(text, it, it + 1) }
        val lefts = FloatArray(text.length) { paint.measureText(text, 0, it) + spacing() * it }
        strokePaint.textSize = paint.textSize
        // Set only when it changes: Paint builds a typeface for each new setting and keeps it while it holds.
        strokePaint.fontVariationSettings = shown.settings
        // Drawn from the baseline, so stretching about it leaves the line's own top where the draw expects it.
        stretching.setScale(1f, stretch)
        val glyphs = text.indices.map { index ->
            val path = Path()
            // A thinner glyph is centred in the awake one's advance, so the line keeps its rhythm as it thins.
            val offset = (advances[index] - strokePaint.measureText(text, index, index + 1)) / 2
            strokePaint.getTextPath(text, index, index + 1, lefts[index] + offset, 0f, path)
            if (stretch != 1f) path.transform(stretching)
            DigitRoll.Glyph(path, lefts[index])
        }
        return glyphs
    }

    private companion object {
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
        const val WIDE_DIGIT = '0'

        /** The always-on display's cut: light, so few pixels stay lit. */
        const val DOZE_WEIGHT = 200f

        /** `wdth` below which a cut is condensed enough that the always-on display outlines it instead ([dozesAsOutline]). */
        const val OUTLINE_BELOW_WIDTH = 60f

        /** As Google's clocks time their appearing and charging on the lock screen. */
        const val ENTER_MS = 833L
        const val CHARGE_OUT_MS = 500L
        const val CHARGE_BACK_MS = 1000L

        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}
