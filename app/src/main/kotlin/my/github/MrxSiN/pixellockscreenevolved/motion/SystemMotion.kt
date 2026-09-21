package my.github.MrxSiN.pixellockscreenevolved.motion

import android.animation.ValueAnimator

/**
 * How much motion the person has asked the system for, read where this module
 * moves something by its own clock rather than through a `ValueAnimator`.
 *
 * Android's "Remove animations" and the developer animator duration scale
 * both set the animator duration scale, which every `ValueAnimator` already
 * follows: at 0 it ends as it starts. A spring stepped by frame time, such as
 * the depth effect's, has to be told the same, which is what this is for.
 */
object SystemMotion {

    /** Whether animations are off, so anything moving should be at its end at once. */
    val isReduced: Boolean get() = !ValueAnimator.areAnimatorsEnabled()

    /** [seconds] of real time as the animator duration scale stretches it: slower above 1, faster below. */
    fun scaled(seconds: Float): Float {
        val scale = ValueAnimator.getDurationScale()
        return if (scale > 0f) seconds / scale else seconds
    }
}
