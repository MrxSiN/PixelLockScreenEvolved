package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator

/**
 * The shared X axis transition Android 17's Settings moves between a page and
 * the one it opens with, read off its `shared_x_axis_activity_*` animations:
 *
 * - opening, the new page comes in from a quarter of the width to the right,
 *   over 450ms on the emphasized curve, fading in over 350ms after 100ms,
 *   while the page under it goes a quarter to the left and fades out in 100ms;
 * - closing, the same in mirror.
 *
 * [page] is the page that opens and closes; [under] is what it opens over.
 * Between the two fades, nothing but what lies behind [under] shows, as
 * Settings shows its backdrop. The animator duration scale, and Remove
 * animations, apply as to any `ValueAnimator`.
 *
 * A back gesture can also hold the page part closed ([follow]), as the
 * system's predictive back does: it shrinks a little and leans toward the edge
 * the gesture came from, then closes from there or springs back.
 */
internal class SharedAxisTransition(private val page: View, private val under: View?) {

    private var running: ValueAnimator? = null

    /** Opens the page. */
    fun open() = run(opening = true) {}

    /** Closes the page, then [onEnd]. */
    fun close(onEnd: () -> Unit) = run(opening = false) {
        reset(under)
        onEnd()
    }

    /**
     * Holds the page part closed by a back gesture, [progress] from 0 to 1, from the
     * left edge where [fromLeft], as predictive back shows what going back would do.
     */
    fun follow(progress: Float, fromLeft: Boolean) {
        running?.cancel()
        val shrink = 1f - BACK_SHRINK * progress
        page.scaleX = shrink
        page.scaleY = shrink
        page.translationX = page.width * BACK_LEAN * progress * if (fromLeft) 1f else -1f
    }

    /** Lets go of a back gesture that was cancelled: the page returns to its place. */
    fun settle() {
        val scale = page.scaleX
        val lean = page.translationX
        running?.cancel()
        running = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = SETTLE_MS
            interpolator = EMPHASIZED_DECELERATE
            addUpdateListener {
                // The value from 1 to 0, as the animator's evaluator works it out, read without boxing.
                val left = 1f + it.animatedFraction * (0f - 1f)
                page.scaleX = 1f + (scale - 1f) * left
                page.scaleY = page.scaleX
                page.translationX = lean * left
            }
            start()
        }
    }

    private fun run(opening: Boolean, onEnd: () -> Unit) {
        running?.cancel()
        // A back gesture may have shrunk the page; it closes from there, easing back to full size as it goes.
        val scale = page.scaleX
        val lean = page.translationX
        frame(0f, opening, scale, lean)
        running = ValueAnimator.ofFloat(0f, SLIDE_MS.toFloat()).apply {
            duration = SLIDE_MS
            interpolator = null
            // The value from 0 to SLIDE_MS, as the animator's evaluator works it out, read without boxing.
            addUpdateListener { frame(0f + it.animatedFraction * (SLIDE_MS.toFloat() - 0f), opening, scale, lean) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    frame(SLIDE_MS.toFloat(), opening, scale, lean)
                    if (!cancelled) onEnd()
                }
            })
            start()
        }
    }

    /** Places both views [elapsed] milliseconds into opening or closing. */
    private fun frame(elapsed: Float, opening: Boolean, scale: Float, lean: Float) {
        val slide = EMPHASIZED.getInterpolation((elapsed / SLIDE_MS).coerceIn(0f, 1f))
        val fadeOut = 1f - ACCELERATE.getInterpolation((elapsed / FADE_OUT_MS).coerceIn(0f, 1f))
        val fadeIn = DECELERATE.getInterpolation(((elapsed - FADE_IN_DELAY_MS) / FADE_IN_MS).coerceIn(0f, 1f))
        val shift = page.width * SHIFT
        if (opening) {
            page.translationX = shift * (1f - slide)
            page.alpha = fadeIn
            under?.translationX = -shift * slide
            under?.alpha = fadeOut
        } else {
            page.translationX = lean * (1f - slide) + shift * slide
            page.scaleX = scale + (1f - scale) * slide
            page.scaleY = page.scaleX
            page.alpha = fadeOut
            under?.translationX = -shift * (1f - slide)
            under?.alpha = fadeIn
        }
    }

    private fun reset(view: View?) {
        view ?: return
        view.translationX = 0f
        view.alpha = 1f
    }

    private companion object {
        /** How far each page moves, against the width. */
        const val SHIFT = 0.25f

        const val SLIDE_MS = 450L
        const val FADE_OUT_MS = 100f
        const val FADE_IN_DELAY_MS = 100f
        const val FADE_IN_MS = 350f
        const val SETTLE_MS = 300L

        /** How much a back gesture shrinks the page, and leans it toward the edge, at its end. */
        const val BACK_SHRINK = 0.1f
        const val BACK_LEAN = 0.05f

        /** `fast_out_extra_slow_in`, Material's emphasized curve. */
        val EMPHASIZED = PathInterpolator(
            android.graphics.Path().apply {
                moveTo(0f, 0f)
                cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
                cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
            },
        )
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val ACCELERATE = PathInterpolator(0.3f, 0f, 1f, 1f)
        val DECELERATE = PathInterpolator(0f, 0f, 0f, 1f)
    }
}
