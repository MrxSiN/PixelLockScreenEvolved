package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.sqrt

/**
 * A value that follows a moving target on a critically damped spring: it sets
 * off gently, never jumps however far the target leaps, and settles without
 * overshooting. Kept free of Android so it can be unit tested.
 */
internal class SpringFollower(stiffness: Float, initial: Float = 0f) {

    /** How firmly the value is pulled toward its target; it can be changed as it moves, keeping its speed. */
    var stiffness: Float = stiffness

    var value = initial.toDouble()
        private set

    private var velocity = 0.0

    /** Moves toward [target] over [seconds], in steps short enough to stay stable, and answers where it is. */
    fun follow(target: Float, seconds: Float): Float {
        var left = seconds.toDouble().coerceIn(0.0, LONGEST_STEP_SECONDS * MAX_STEPS)
        while (left > 0.0) {
            val dt = minOf(left, LONGEST_STEP_SECONDS)
            val k = stiffness.toDouble()
            velocity += (k * (target - value) - 2.0 * sqrt(k) * velocity) * dt
            value += velocity * dt
            left -= dt
        }
        return value.toFloat()
    }

    private companion object {
        const val LONGEST_STEP_SECONDS = 0.004
        const val MAX_STEPS = 25
    }
}
