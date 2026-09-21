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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = FlexCut.typeface }
    private val ink = DozeInk(context)
    private val digits = DigitRoll(this) { canvas, shape, alpha -> ink.draw(canvas, shape, alpha) }
    private val numerals = Rect()

    private var text = ""
    private var numeralHeight = 0f
    private var maxWidth = Float.MAX_VALUE
    private var maxStretch = 1f

    /** How much taller than the font designs them the numerals are drawn, from 1 ([fit]). */
    private var stretch = 1f

    /** How far into the always-on display, from 0 awake to 1 dozing. */
    private var doze = 0f

    /** How far an enter or charge animation holds the strokes toward the light cut, from 0 to 1. */
    private var lift = 0f
    private var pulse: android.animation.Animator? = null

    /** [Gravity.START], [Gravity.CENTER_HORIZONTAL] or [Gravity.END]. */
    var gravity: Int = Gravity.CENTER_HORIZONTAL
        set(value) {
            field = value
            invalidate()
        }

    fun setText(text: String) {
        if (this.text == text) return
        val before = this.text
        val beforeWidth = paint.measureText(before)
        this.text = text
        size()
        digits.show(before, text, glyphs(), shiftFor(paint.measureText(text) - beforeWidth))
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

    /** Thins the strokes toward the always-on display's light cut, from 0 awake to 1 dozing. */
    fun setDoze(fraction: Float) {
        val next = fraction.coerceIn(0f, 1f)
        if (next == doze) return
        doze = next
        restroke()
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
        val width = ceil(paint.measureText(text)).toInt() + 2 * ink.inset
        val height = ceil(drawnHeight()).toInt() + 2 * ink.inset
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        digits.finish()
        pulse?.end()
    }

    override fun onDraw(canvas: Canvas) {
        val room = width - 2 * ink.inset - paint.measureText(text)
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

    /** The cut the strokes are drawn in now: the awake cut, thinned as far as the doze or a pulse takes it. */
    private fun shownCut(): FlexCut = cut.toward(cut.copy(weight = min(cut.weight, DOZE_WEIGHT)), max(doze, lift))

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut, and lays the numerals out again at once. */
    private fun resize() {
        size()
        digits.replace(glyphs())
        requestLayout()
    }

    /** Sets the text size for [numeralHeight] and [maxWidth] in the awake cut. */
    private fun size() {
        paint.fontVariationSettings = null
        paint.fontVariationSettings = cut.settings
        paint.textSize = PROBE_SIZE
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, numerals)
        val numeralPerSize = numerals.height() / PROBE_SIZE
        // Every digit measured as the widest, so the line keeps one size whichever minute it shows.
        val widest = text.map { if (it.isDigit()) WIDE_DIGIT else it }.joinToString("")
        val widthPerSize = paint.measureText(widest) / PROBE_SIZE
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

    /** The glyphs of [text] in the cut shown now, placed by the awake cut's advances so nothing shifts. */
    private fun glyphs(): List<DigitRoll.Glyph> {
        paint.fontVariationSettings = null
        paint.fontVariationSettings = cut.settings
        val lefts = text.indices.map { paint.measureText(text, 0, it) }
        val advances = text.indices.map { paint.measureText(text, it, it + 1) }
        paint.fontVariationSettings = shownCut().settings
        // Drawn from the baseline, so stretching about it leaves the line's own top where the draw expects it.
        val stretching = Matrix().apply { setScale(1f, stretch) }
        val glyphs = text.indices.map { index ->
            val path = Path()
            // A thinner glyph is centred in the awake one's advance, so the line keeps its rhythm as it thins.
            val offset = (advances[index] - paint.measureText(text, index, index + 1)) / 2
            paint.getTextPath(text, index, index + 1, lefts[index] + offset, 0f, path)
            if (stretch != 1f) path.transform(stretching)
            DigitRoll.Glyph(path, lefts[index])
        }
        paint.fontVariationSettings = null
        paint.fontVariationSettings = cut.settings
        return glyphs
    }

    private companion object {
        const val NUMERALS = "0123456789"
        const val PROBE_SIZE = 200f
        const val WIDE_DIGIT = '0'

        /** The always-on display's cut: light, so few pixels stay lit. */
        const val DOZE_WEIGHT = 200f

        /** As Google's clocks time their appearing and charging on the lock screen. */
        const val ENTER_MS = 833L
        const val CHARGE_OUT_MS = 500L
        const val CHARGE_BACK_MS = 1000L

        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val EMPHASIZED_ACCELERATE = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    }
}
