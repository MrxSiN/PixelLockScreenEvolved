package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.view.View
import android.view.animation.PathInterpolator

/**
 * A line of the time laid out a glyph per character, and the roll it makes as
 * the minute turns, for every face that writes digits.
 *
 * The digits that change roll: each old digit rises and fades while its new one
 * comes up from below into its place, and the digits that stay glide to where
 * the new text puts them ([DigitTransition]). The roll runs on the emphasized
 * curve, as Material 3 Expressive moves things into place, and follows the
 * animator duration scale.
 *
 * [host] is redrawn as it runs; [ink] draws one shape as opaque as asked, called
 * directly so no alpha is boxed per glyph per frame.
 */
class DigitRoll(private val host: View, private val ink: DozeInk) {

    /** One character as laid out: its shape, and where its advance starts. */
    class Glyph(val path: Path, val left: Float)

    /** The time as laid out now. */
    var glyphs: List<Glyph> = emptyList()
        private set

    private class Roll(
        val before: List<Glyph>,
        val places: List<DigitTransition.Place>,
        /** How much wider the new text is than the old on each side, as a centred line grows. */
        val shift: Float,
    ) {
        var progress = 0f
    }

    private var roll: Roll? = null
    private var rolling: ValueAnimator? = null
    private val placed = Path()
    private val move = Matrix()

    /** Whether the digits are rolling, so what is drawn now is not yet what the time will show. */
    val isChanging: Boolean get() = roll != null

    /**
     * Shows [glyphs], laid out for [text], rolling from [before] where the host is
     * on screen and there was a time before; [shift] is how far the old glyphs sit
     * off in the new layout.
     */
    fun show(before: String, text: String, glyphs: List<Glyph>, shift: Float) {
        val old = this.glyphs
        this.glyphs = glyphs
        if (before.isEmpty() || before == text || !host.isAttachedToWindow || !host.isShown) {
            finish()
            return
        }
        start(Roll(old, DigitTransition.between(before, text), shift))
    }

    /** Lays [glyphs] out at once, without rolling, as when the size changes. */
    fun replace(glyphs: List<Glyph>) {
        this.glyphs = glyphs
        finish()
    }

    /** Reshapes the glyphs as they are, as when their strokes thin, keeping any roll under way. */
    fun update(glyphs: List<Glyph>) {
        this.glyphs = glyphs
    }

    /** Draws the time, or the roll to it, [rise] being how far a rolling digit travels. */
    fun draw(canvas: Canvas, rise: Float) {
        // Walked by index, with no lambda or iterator: this runs on every frame of a roll.
        val glyphs = glyphs
        val current = roll
        if (current == null) {
            for (i in glyphs.indices) ink.draw(canvas, glyphs[i].path, 1f)
            return
        }
        val eased = EASING.getInterpolation(current.progress)
        val places = current.places
        for (i in places.indices) {
            val place = places[i]
            val before = place.before
            val after = place.after
            val old = if (before != null) current.before[before] else null
            val new = if (after != null) glyphs[after] else null
            // An old glyph laid out for the old width sits [Roll.shift] further right in the new one.
            // How far the place is from its new left across the roll: from its old left to none.
            val glide = if (old != null && new != null) (old.left + current.shift - new.left) * (1f - eased) else 0f
            if (!place.changes) {
                if (new != null) drawMoved(canvas, new.path, glide, 0f, 1f)
                continue
            }
            if (old != null) {
                val sideways = if (new != null) new.left + glide - old.left else current.shift
                drawMoved(canvas, old.path, sideways, -rise * eased, 1f - smoothstep(current.progress / OUT_BY))
            }
            if (new != null) {
                drawMoved(canvas, new.path, glide, rise * (1f - eased), smoothstep((current.progress - IN_FROM) / (1f - IN_FROM)))
            }
        }
    }

    /** Ends a roll at once, as when the host leaves the screen. */
    fun finish() {
        roll = null
        rolling?.let {
            rolling = null
            it.cancel()
        }
        host.invalidate()
    }

    private fun drawMoved(canvas: Canvas, shape: Path, dx: Float, dy: Float, alpha: Float) {
        if (alpha <= 0f) return
        if (dx == 0f && dy == 0f) return ink.draw(canvas, shape, alpha)
        move.setTranslate(dx, dy)
        placed.set(shape)
        placed.transform(move)
        ink.draw(canvas, placed, alpha)
    }

    private fun start(next: Roll) {
        rolling?.cancel()
        roll = next
        rolling = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ROLL_MS
            addUpdateListener {
                // From 0 to 1, the value is the eased fraction itself, read without boxing.
                next.progress = it.animatedFraction
                host.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (roll === next) finish()
                }
            })
            start()
        }
    }

    companion object {
        /** How long the digits take to roll to a new minute. */
        const val ROLL_MS = 600L

        /** How far a rolling digit rises, against the numerals' height. */
        const val RISE = 0.45f

        /** Share of the roll by which an old digit has faded out. */
        private const val OUT_BY = 0.55f

        /** Share of the roll after which a new digit starts to fade in. */
        private const val IN_FROM = 0.15f

        /** Material's emphasized decelerate: most of the way early, and a long settle. */
        private val EASING = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

        private fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    }
}
