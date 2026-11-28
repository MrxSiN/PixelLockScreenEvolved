package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import kotlin.math.atan2
import kotlin.math.hypot

import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * Resizes one clock into the other as Android 17's composed lock screen changes clock size, when the first
 * notification arrives or the last one goes.
 *
 * The scene fades the face being left out where it stands and slides the other in from above (its holder
 * runs from 233 px above the screen to its place in about 0.25 s, measured on a Pixel 8 Pro), so the time
 * vanished in one place and dropped in at another. Here the face being left is hidden at once and the
 * arriving face's time is drawn where, and as large as, the time just left, then carried to its own place
 * on the expressive spatial spring. Its place is read afresh before every frame, so the scene's slide is
 * taken out rather than added to. The rest of the arriving face, its date, fades in around it.
 *
 * A face turned by its position ([ClockLayout.rotation]) turns on the way, as it moves and resizes.
 */
internal class ComposedClockSizeMorph {

    private var running: Run? = null

    fun start(from: ClockFaceAdapter, to: ClockFaceAdapter) {
        running?.finish()
        to.view.transitionAlpha = 1f
        if (SystemMotion.isReduced) return
        val origin = Box.of(from.timeView) ?: return
        running = Run(origin, from, to).also { it.begin() }
    }

    /** Where a time stands in the window: its middle, its size there, and how far it is turned. */
    private class Box(var x: Float, var y: Float, var width: Float, var height: Float, var degrees: Float) {
        companion object {
            private val matrix = Matrix()
            private val values = FloatArray(9)
            private val point = FloatArray(2)

            /** [view]'s box in the window, as drawn this frame; null before it is laid out. */
            fun of(view: View): Box? {
                if (view.width == 0 || view.height == 0) return null
                ViewBounds.toWindow(view, matrix, beforeDraw = true)
                return box(matrix, view.width / 2f, view.height / 2f, view.width.toFloat(), view.height.toFloat(), Box(0f, 0f, 0f, 0f, 0f))
            }

            /** Writes into [out] the box a [width] by [height] rectangle centred on ([x], [y]) is drawn as through [matrix]. */
            fun box(matrix: Matrix, x: Float, y: Float, width: Float, height: Float, out: Box): Box {
                matrix.getValues(values)
                point[0] = x
                point[1] = y
                matrix.mapPoints(point)
                val scaleX = hypot(values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y])
                val scaleY = hypot(values[Matrix.MSKEW_X], values[Matrix.MSCALE_Y])
                out.x = point[0]
                out.y = point[1]
                out.width = width * scaleX
                out.height = height * scaleY
                out.degrees = Math.toDegrees(atan2(values[Matrix.MSKEW_Y], values[Matrix.MSCALE_X]).toDouble()).toFloat()
                return out
            }
        }
    }

    private inner class Run(private val origin: Box, private val from: ClockFaceAdapter, private val to: ClockFaceAdapter) {
        private val time = to.timeView
        private val toWindow = Matrix()
        private val fromWindow = Matrix()
        private val point = FloatArray(2)

        /** Where the time's own face puts it this frame, written again before every frame. */
        private val place = Box(0f, 0f, 0f, 0f, 0f)
        private var progress = 0f
        private var faded = 0f
        private var animator: ValueAnimator? = null
        private val around: List<View> = (to.view as? ViewGroup)
            ?.let { group -> (0 until group.childCount).map(group::getChildAt) }.orEmpty()
            .filter { it !== time }

        private val placing = ViewTreeObserver.OnPreDrawListener { apply(); true }

        fun begin() {
            to.view.viewTreeObserver.addOnPreDrawListener(placing)
            animator = MOVE.animator(
                update = { seconds ->
                    progress = MOVE.valueAt(seconds)
                    faded = FADE.valueAt(seconds).coerceIn(0f, 1f)
                    to.view.invalidate()
                },
                end = { finish() },
            ).apply { start() }
            apply()
        }

        /**
         * Draws the time between the box of the time just left and the box its own face puts it in this frame:
         * its middle, its size and its turn each part of the way. Worked out in the face's own pixels, as the
         * time's view properties are, so the scene's moves of the face are taken out.
         */
        private fun apply() {
            for (view in around) view.alpha = faded
            val parent = time.parent as? View ?: return
            val width = time.width.toFloat()
            val height = time.height.toFloat()
            if (width == 0f || height == 0f) return
            // Hidden as the time arriving is first drawn over it, not before: hidden at once, nothing showed a frame.
            from.view.transitionAlpha = 0f
            ViewBounds.toWindow(parent, toWindow, beforeDraw = true)
            if (!toWindow.invert(fromWindow)) return
            val middleX = time.left + width / 2
            val middleY = time.top + height / 2
            Box.box(toWindow, middleX, middleY, width, height, place)
            val parentScale = place.width / width
            if (!(parentScale > 0f)) return
            point[0] = lerp(origin.x, place.x, progress)
            point[1] = lerp(origin.y, place.y, progress)
            fromWindow.mapPoints(point)
            time.pivotX = width / 2
            time.pivotY = height / 2
            time.translationX = point[0] - middleX
            time.translationY = point[1] - middleY
            time.scaleX = lerp(origin.width, place.width, progress) / (width * parentScale)
            time.scaleY = lerp(origin.height, place.height, progress) / (height * parentScale)
            time.rotation = (origin.degrees - place.degrees) * (1f - progress)
        }

        fun finish() {
            if (running !== this) return
            running = null
            animator?.cancel()
            to.view.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(placing)
            time.translationX = 0f
            time.translationY = 0f
            time.scaleX = 1f
            time.scaleY = 1f
            time.rotation = 0f
            time.resetPivot()
            for (view in around) view.alpha = 1f
            // Hidden only while the scene fades it out, a tenth of a second, well inside the morph: the scene can show
            // the same view again without attaching it anew, and kept hidden, a clock went missing.
            from.view.transitionAlpha = 1f
        }
    }

    private companion object {
        val MOVE = ExpressiveSpring.DEFAULT_SPATIAL
        val FADE = ExpressiveSpring.DEFAULT_EFFECTS

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
    }
}
