package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator

import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A spring from 0 to 1, starting at rest, as Material 3 Expressive moves things:
 * by a damping ratio and a stiffness rather than a duration and an easing curve.
 * Its values are worked out without Android, so they can be unit tested.
 */
internal class ExpressiveSpring(private val dampingRatio: Float, stiffness: Float) {

    private val naturalFrequency = sqrt(stiffness.toDouble())

    /** Where the spring is [seconds] after it is let go. */
    fun valueAt(seconds: Float): Float {
        val t = seconds.toDouble().coerceAtLeast(0.0)
        val decay = exp(-dampingRatio * naturalFrequency * t)
        val value = if (dampingRatio < 1f) {
            val damped = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
            1.0 - decay * (cos(damped * t) + dampingRatio * naturalFrequency / damped * sin(damped * t))
        } else {
            1.0 - decay * (1.0 + naturalFrequency * t)
        }
        return value.toFloat()
    }

    /**
     * Where the spring is [seconds] after being knocked while at rest: it sets off
     * from 0, swings out and comes back to 0. Scaled so its farthest swing is 1.
     */
    fun kickAt(seconds: Float): Float {
        val t = seconds.toDouble().coerceAtLeast(0.0)
        val rate = dampingRatio * naturalFrequency
        return if (dampingRatio < 1f) {
            val damped = naturalFrequency * sqrt(1.0 - dampingRatio * dampingRatio)
            val peakTime = atan(damped / rate) / damped
            (exp(-rate * t) * sin(damped * t) / (exp(-rate * peakTime) * sin(damped * peakTime))).toFloat()
        } else {
            // Critically damped: t·e^(-ωt), farthest at t = 1/ω.
            (naturalFrequency * t * exp(1.0 - naturalFrequency * t)).toFloat()
        }
    }

    /** How long until the spring is within a thousandth of its end and stays there, in milliseconds. */
    val settleMillis: Long
        get() {
            val envelope = ln(1.0 / SETTLED) / (dampingRatio * naturalFrequency)
            // A critically damped spring also carries a linear term; allow it some extra time.
            val extra = if (dampingRatio < 1f) 1.0 else 1.5
            return (envelope * extra * 1000.0).toLong()
        }

    /**
     * An animator that runs for as long as this spring takes to settle and hands
     * [update] the seconds since it started, after [delayMillis]; [end] runs once
     * it is done or ended early.
     */
    fun animator(delayMillis: Long = 0L, update: (seconds: Float) -> Unit, end: () -> Unit): ValueAnimator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = settleMillis
            startDelay = delayMillis
            addUpdateListener { update(it.currentPlayTime / MILLIS_PER_SECOND) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = end()
            })
        }

    companion object {
        private const val SETTLED = 0.001
        private const val MILLIS_PER_SECOND = 1000f

        /** `MotionScheme.expressive().fastSpatialSpec()`: small things moving and resizing, with a little bounce. */
        val FAST_SPATIAL = ExpressiveSpring(dampingRatio = 0.6f, stiffness = 800f)

        /** `MotionScheme.expressive().defaultSpatialSpec()`: larger things moving and resizing, with a little bounce. */
        val DEFAULT_SPATIAL = ExpressiveSpring(dampingRatio = 0.8f, stiffness = 380f)

        /** `MotionScheme.expressive().fastEffectsSpec()`: opacity and colour, without bounce. */
        val FAST_EFFECTS = ExpressiveSpring(dampingRatio = 1f, stiffness = 3800f)

        /** `MotionScheme.expressive().defaultEffectsSpec()`: opacity and colour over a larger area, without bounce. */
        val DEFAULT_EFFECTS = ExpressiveSpring(dampingRatio = 1f, stiffness = 1600f)
    }
}
