package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator

/**
 * Carries the time from one clock face to the other as the lock screen changes
 * clock size, so it moves and resizes in one piece rather than fading out in
 * one place and back in at another.
 *
 * [start] is called while the face being left is still laid out. On the first
 * frame the face being shown is laid out, its time is moved and scaled back
 * over the time just left and then eased into its own place, while the rest of
 * that face (the large face's date) fades in around it.
 */
internal class ClockSizeMorph {

    private var running: ValueAnimator? = null

    fun start(from: ClockFaceAdapter, to: ClockFaceAdapter) {
        running?.end()
        val origin = ViewBounds.inWindow(from.timeView).takeUnless { it.isEmpty } ?: return
        val face = to.view
        face.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (face.viewTreeObserver.isAlive) face.viewTreeObserver.removeOnPreDrawListener(this)
                begin(origin, to)
                return true
            }
        })
    }

    private fun begin(origin: RectF, to: ClockFaceAdapter) {
        val time = to.timeView
        val place = ViewBounds.inWindow(time)
        if (place.isEmpty || time.width == 0 || time.height == 0) return

        // The face may itself be scaled, so the move is worked out in its own pixels.
        val faceScaleX = place.width() / time.width
        val faceScaleY = place.height() / time.height
        val fromX = (origin.left - place.left) / faceScaleX
        val fromY = (origin.top - place.top) / faceScaleY
        val fromScaleX = origin.width() / place.width()
        val fromScaleY = origin.height() / place.height()
        val around = (to.view as? ViewGroup)?.let { group -> (0 until group.childCount).map(group::getChildAt) }
            .orEmpty()
            .filter { it !== time }

        time.pivotX = 0f
        time.pivotY = 0f
        running = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION_MS
            interpolator = EMPHASIZED_DECELERATE
            addUpdateListener {
                val progress = it.animatedValue as Float
                time.translationX = fromX * (1f - progress)
                time.translationY = fromY * (1f - progress)
                time.scaleX = lerp(fromScaleX, 1f, progress)
                time.scaleY = lerp(fromScaleY, 1f, progress)
                around.forEach { view -> view.alpha = progress }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = settle(time, around)
            })
            start()
        }
    }

    private fun settle(time: View, around: List<View>) {
        time.translationX = 0f
        time.translationY = 0f
        time.scaleX = 1f
        time.scaleY = 1f
        time.resetPivot()
        around.forEach { it.alpha = 1f }
        running = null
    }

    private companion object {
        const val DURATION_MS = 500L

        /** Material's emphasized decelerate easing. */
        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
    }
}
