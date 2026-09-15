package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.ViewGroup
import android.view.ViewTreeObserver

/**
 * Carries the time from one clock face to the other as the lock screen changes
 * clock size, so it moves and resizes in one piece rather than fading out in
 * one place and back in at another.
 *
 * [start] is called while the face being left is still laid out. On the first
 * frame the face being shown is laid out, its time is moved and scaled back
 * over the time just left and then carried into its own place on a spring
 * that sets off from rest, so it neither jumps at the start nor crawls at the
 * end, while the rest of that face (the large face's date, and the depth
 * effect's subject beside it) fades in around it ([ClockFaceAdapter.arrival]).
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
        fun show(progress: Float, faded: Float) {
            time.translationX = fromX * (1f - progress)
            time.translationY = fromY * (1f - progress)
            time.scaleX = lerp(fromScaleX, 1f, progress)
            time.scaleY = lerp(fromScaleY, 1f, progress)
            around.forEach { view -> view.alpha = faded }
            to.arrival = faded
        }
        show(0f, 0f)
        running = MOVE.animator(
            update = { seconds -> show(MOVE.valueAt(seconds), FADE.valueAt(seconds).coerceIn(0f, 1f)) },
            end = {
                show(1f, 1f)
                time.resetPivot()
                running = null
            },
        ).apply { start() }
    }

    private companion object {
        val MOVE = ExpressiveSpring.DEFAULT_SPATIAL
        val FADE = ExpressiveSpring.DEFAULT_EFFECTS

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
    }
}
