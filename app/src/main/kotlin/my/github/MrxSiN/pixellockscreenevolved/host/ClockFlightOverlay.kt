package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * A picture of a lock screen [time] on its way to the status bar clock
 * [target], placed by [setProgress]: at 0 exactly over the time, at 1 exactly
 * over the status bar clock. It stays there until the status bar clock has
 * fully appeared ([handOver]), since SystemUI brings the status bar back some
 * time after the unlock.
 *
 * The two clocks are set in different fonts, so digits of the same height are
 * not the same width. Over the last part of the flight the picture of the time
 * crossfades into a picture of the status bar clock's text, drawn with that
 * clock's own paint, while its width eases to that text's; what lands is what
 * the status bar clock draws.
 *
 * The keyguard window fades within a few frames of an unlock starting, far
 * sooner than a move can be followed, so the pictures live in a window of
 * their own above everything. The time is hidden once they are on screen and
 * shown again by [remove].
 */
internal class ClockFlightOverlay(
    private val time: View,
    private val target: TextView,
    private val logger: Logger,
) {

    private val windowManager = time.context.getSystemService(WindowManager::class.java)

    private val timePicture = Bitmap.createBitmap(time.width, time.height, Bitmap.Config.ARGB_8888)
        .also { time.draw(Canvas(it)) }
    private val startColor = inkColour(timePicture)
    private val endColor = target.currentTextColor

    private val from = time.screenLocation()
    private val landing = Landing.of(target, endColor)
    private val path = flightPath()

    private val timeImage = image(timePicture, time.width, time.height)
    private val landingImage = image(landing.picture, landing.width, landing.height).apply { alpha = 0f }
    private val root = FrameLayout(time.context).apply {
        addView(timeImage)
        addView(landingImage)
    }

    private val timeAlpha = time.alpha
    private var progress = 0f
    private var added = false

    /** Shows the pictures over the time and then hides the time; false if the window could not be added. */
    fun show(): Boolean {
        try {
            windowManager.addView(root, windowParams())
            added = true
        } catch (error: RuntimeException) {
            logger.warn("Clock flight window could not be added", error)
            recycle()
            return false
        }
        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                root.viewTreeObserver.removeOnPreDrawListener(this)
                place()
                time.alpha = 0f
                return true
            }
        })
        return true
    }

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        if (added) place()
    }

    /**
     * Holds the landed picture, fully solid, over the status bar clock while
     * that clock fades in beneath it; true once it has fully, when the picture
     * can go without a change. Fading the picture out as the clock fades in
     * would dim the two together midway.
     */
    fun handOver(): Boolean {
        val shown = if (target.isShown) visibleAlpha(target) else 0f
        timeImage.alpha = 0f
        landingImage.alpha = 1f
        // The status bar's tint can change as the app behind it appears.
        landingImage.colorFilter = PorterDuffColorFilter(target.currentTextColor, PorterDuff.Mode.SRC_IN)
        return shown >= SHOWN
    }

    /** Takes the pictures away and shows the time again. */
    fun remove() {
        time.alpha = timeAlpha
        if (!added) return
        added = false
        runCatching { windowManager.removeViewImmediate(root) }
            .onFailure { logger.warn("Clock flight window could not be removed", it) }
        recycle()
    }

    private fun place() {
        val origin = root.screenLocation()
        val remaining = 1f - progress
        // Shrinks ahead of its move, so it is small by the time it passes the top left, where
        // the home screen's smartspace appears.
        val shrink = 1f - remaining * remaining * remaining * remaining * remaining
        val along = 1f - remaining * remaining * remaining
        val morph = smoothstep((along - MORPH_FROM) / (1f - MORPH_FROM))

        val height = lerp(time.height.toFloat(), landing.height.toFloat(), shrink)
        val sameShapeWidth = time.width * height / time.height
        val width = lerp(sameShapeWidth, landing.width.toFloat(), morph)
        val x = path.x(along) - width / 2f - origin[0]
        val y = path.y(along) - origin[1]

        timeImage.fit(x, y, width, height, time.width, time.height)
        timeImage.alpha = 1f - morph
        timeImage.colorFilter = PorterDuffColorFilter(COLOUR.evaluate(shrink, startColor, endColor) as Int, PorterDuff.Mode.SRC_IN)

        landingImage.fit(x, y, width, height, landing.width, landing.height)
        landingImage.alpha = morph
    }

    /**
     * The path of the time's top centre: a curve that leaves the time straight
     * up, bends toward the status bar clock at [bendHeight], and comes in to
     * that clock from the side. Its control points hold it at or below that
     * height until it is well past the middle of the screen.
     */
    private fun flightPath(): Bezier {
        val startX = from[0] + time.width / 2f
        val endX = landing.x + landing.width / 2f
        val bend = bendHeight(startX, endX)
        return Bezier(
            startX, from[1].toFloat(),
            startX, bend,
            lerp(startX, endX, BEND_LEAD), bend,
            endX, landing.y,
        )
    }

    /** At the status bar clock's height, or just below a camera cutout that lies between the time and it. */
    private fun bendHeight(startX: Float, endX: Float): Float {
        val left = minOf(startX, endX) - time.width / 2f
        val right = maxOf(startX, endX) + time.width / 2f
        val cutoutBottom = time.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
            .filter { it.right > left && it.left < right && it.bottom > landing.y }
            .maxOfOrNull { it.bottom }
            ?: return landing.y
        return cutoutBottom + CUTOUT_CLEARANCE_DP * time.resources.displayMetrics.density
    }

    /** A cubic Bézier curve through four points. */
    private class Bezier(
        private val x0: Float, private val y0: Float,
        private val x1: Float, private val y1: Float,
        private val x2: Float, private val y2: Float,
        private val x3: Float, private val y3: Float,
    ) {
        fun x(t: Float) = at(t, x0, x1, x2, x3)
        fun y(t: Float) = at(t, y0, y1, y2, y3)

        private fun at(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
            val u = 1f - t
            return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
        }
    }

    private fun ImageView.fit(x: Float, y: Float, width: Float, height: Float, pictureWidth: Int, pictureHeight: Int) {
        translationX = x
        translationY = y
        scaleX = width / pictureWidth
        scaleY = height / pictureHeight
    }

    private fun image(picture: Bitmap, width: Int, height: Int) = ImageView(time.context).apply {
        setImageBitmap(picture)
        pivotX = 0f
        pivotY = 0f
        layoutParams = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START)
    }

    private fun recycle() {
        timePicture.recycle()
        landing.picture.recycle()
    }

    private fun windowParams() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        TYPE_SECURE_SYSTEM_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = WINDOW_TITLE
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    /** The status bar clock's text as that clock draws it, and where on screen its ink sits. */
    private class Landing(val picture: Bitmap, val x: Float, val y: Float) {
        val width get() = picture.width
        val height get() = picture.height

        companion object {
            fun of(target: TextView, colour: Int): Landing {
                val text = target.text.toString()
                val paint = Paint(target.paint).apply { color = colour }
                val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }

                val picture = Bitmap.createBitmap(ink.width() + 2 * EDGE, ink.height() + 2 * EDGE, Bitmap.Config.ARGB_8888)
                Canvas(picture).drawText(text, (EDGE - ink.left).toFloat(), (EDGE - ink.top).toFloat(), paint)

                val on = target.screenLocation()
                val lineLeft = target.layout?.getLineLeft(0) ?: 0f
                val x = on[0] + target.compoundPaddingLeft + lineLeft + ink.left - EDGE
                val y = (on[1] + target.baseline + ink.top - EDGE).toFloat()
                return Landing(picture, x, y)
            }
        }
    }

    private companion object {
        /** Share of the way along the path after which the time turns into the status bar clock's text. */
        const val MORPH_FROM = 0.6f

        /** How far toward the status bar clock the curve's second control point sits. */
        const val BEND_LEAD = 0.5f

        /** Space kept below a camera cutout while passing under it. */
        const val CUTOUT_CLEARANCE_DP = 8f

        /** The status bar clock counts as shown from this opacity. */
        const val SHOWN = 0.99f

        /** Room around the landing text so its antialiased edges are not cut off. */
        const val EDGE = 2

        const val OPAQUE = 255
        const val WINDOW_TITLE = "PixelLockScreenEvolvedClockFlight"

        /** `WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY`, hidden from apps; above the status bar. */
        const val TYPE_SECURE_SYSTEM_OVERLAY = 2015

        val COLOUR = ArgbEvaluator()

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

        fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

        fun View.screenLocation(): IntArray = IntArray(2).also(::getLocationOnScreen)

        /** The colour the time is drawn in, read off the picture's first solid pixel along its middle. */
        fun inkColour(picture: Bitmap): Int {
            val y = picture.height / 2
            return (0 until picture.width).asSequence()
                .map { picture.getPixel(it, y) }
                .firstOrNull { Color.alpha(it) == OPAQUE }
                ?: Color.WHITE
        }

        /** How opaque [view] looks, with its parents' alpha applied. */
        fun visibleAlpha(view: View): Float {
            var alpha = view.alpha
            var parent = view.parent
            while (parent is View) {
                alpha *= parent.alpha
                parent = parent.parent
            }
            return alpha
        }
    }
}
