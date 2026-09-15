package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.ImageView
import android.widget.TextView

import kotlin.math.pow

import my.github.MrxSiN.pixellockscreenevolved.host.ViewPictures.screenLocation

/**
 * A picture of a lock screen [time] on its way to the status bar clock
 * [target], placed by [setProgress]: at 0 exactly over the time, at 1 exactly
 * over the status bar clock. It lands there and hands over to that clock
 * ([handOver]), which SystemUI brings back some time after the unlock.
 *
 * The two clocks are set in different fonts, so digits of the same height are
 * not the same width. Over the last part of the flight the picture of the time
 * crossfades into a picture of the status bar clock's text, drawn with that
 * clock's own paint, while its width eases to that text's; what lands is what
 * the status bar clock draws.
 *
 * The keyguard window fades within a few frames of an unlock starting, far
 * sooner than a move can be followed, so the pictures live in a window of
 * their own above everything, which, like the pictures, is usually ready
 * before the unlock begins ([ClockFlightStandby]). The time is hidden once the
 * pictures have reached the screen; once landed, the picture hands over to the
 * status bar clock beneath it ([handOver]).
 *
 * The time starts at the size it is drawn on screen, which SystemUI may scale,
 * and as faded as the keyguard has made it, coming up to full over the first
 * part of its way. It shrinks by the same ratio each moment rather than the
 * same number of pixels, so it does not seem to collapse at the end.
 * Where something is drawn in front of it ([TimeOcclusion], the depth effect's
 * subject), the flight starts with those parts missing, as they were on the
 * lock screen, and fills them in over the first part of its way, as the time
 * comes out from behind the subject.
 */
internal class ClockFlightOverlay(
    val time: View,
    private val target: TextView,
    private val pictures: TimePictures,
    private val landing: LandingPicture,
    /** The window the pictures are shown in, added or not yet. */
    private val window: OverlayWindow,
) {

    private val startColor = pictures.color
    private val endColor = target.currentTextColor

    /** Where the time is drawn on screen, with SystemUI's scale of the clock applied. */
    private val from = ViewBounds.inWindow(time)
    private val path = flightPath()

    private val timeImage = ViewPictures.image(time, pictures.whole).apply { alpha = 0f }
    private val hiddenTimeImage = pictures.hidden?.let { ViewPictures.image(time, it).apply { alpha = 0f } }
    private val landingImage = ViewPictures.image(time, landing.picture).apply { alpha = 0f }
    private val root = window.root.apply {
        addView(timeImage)
        hiddenTimeImage?.let(::addView)
        addView(landingImage)
    }

    private val timeAlpha = time.alpha

    /** How shown the time was as the pictures first appeared over it; a fast swipe may have faded it a long way. */
    private var startAlpha = 1f

    /** How far along the flight was as the pictures first appeared. */
    private var appearedAt = 0f
    private var progress = 0f
    private var shown = false

    /** Shows the pictures over the time, hiding the time as they first appear; false if the window could not be added. */
    fun show(): Boolean {
        if (!window.isAdded && !window.add()) {
            recycle()
            return false
        }
        place()
        window.onFirstFrame {
            shown = true
            startAlpha = ViewPictures.visibleAlpha(time)
            appearedAt = progress
            place()
            time.alpha = 0f
        }
        return true
    }

    fun setProgress(progress: Float) {
        this.progress = progress.coerceIn(0f, 1f)
        if (window.isAdded) place()
    }

    /**
     * Holds the landed picture, solid, over the status bar clock, following it,
     * while SystemUI fades that clock in beneath it; true once it has fully, when
     * the picture can go without a change. The status bar clock is never hidden
     * or faded by this module: on an otherwise idle status bar such a change was
     * not on screen when the picture went, and the clock blinked out.
     */
    fun handOver(): Boolean {
        timeImage.alpha = 0f
        hiddenTimeImage?.alpha = 0f
        landingImage.alpha = 1f
        landingImage.placeAt(landing.origin(target), landing.width.toFloat(), landing.height.toFloat())
        // The status bar's tint can change as the app behind it appears.
        landingImage.colorFilter = PorterDuffColorFilter(target.currentTextColor, PorterDuff.Mode.SRC_IN)
        return ViewPictures.visibleAlpha(target) >= SHOWN
    }

    /** Whether SystemUI is showing the status bar clock the time is flying to. */
    fun targetShown(): Boolean = ViewPictures.visibleAlpha(target) > 0f

    /** Takes the pictures away and shows the time again. */
    fun remove() {
        time.alpha = timeAlpha
        if (!window.isAdded) return
        window.remove()
        recycle()
    }

    private fun place() {
        if (!shown) return

        // The spring already eases the progress in and out, so the path follows it as it is.
        val along = progress
        // Shrinks as it moves, a little ahead, so it is small by the time it passes the top left,
        // where the home screen's smartspace appears, without its size changing faster than its place.
        val shrink = smoothstep(along / SHRUNK_BY)
        val morph = smoothstep((along - MORPH_FROM) / (1f - MORPH_FROM))
        root.alpha = lerp(startAlpha, 1f, smoothstep((along - appearedAt) / APPEARED_BY))

        val height = from.height() * (landing.height / from.height()).pow(shrink)
        val sameShapeWidth = from.width() * height / from.height()
        val width = lerp(sameShapeWidth, landing.width.toFloat(), morph)
        val topLeft = PointF(path.x(along) - width / 2f, path.y(along))

        val tint = PorterDuffColorFilter(COLOUR.evaluate(shrink, startColor, endColor) as Int, PorterDuff.Mode.SRC_IN)
        timeImage.placeAt(topLeft, width, height)
        timeImage.colorFilter = tint
        val hidden = hiddenTimeImage
        if (hidden == null) {
            timeImage.alpha = 1f - morph
        } else {
            // The whole time fills in beneath the one with parts cut away, which then goes;
            // the two are never both part-faded, so the time never dims.
            val emerged = smoothstep(progress / EMERGED_BY)
            hidden.placeAt(topLeft, width, height)
            hidden.colorFilter = tint
            hidden.alpha = if (emerged < 1f) 1f - morph else 0f
            timeImage.alpha = (1f - morph) * emerged
        }

        landingImage.placeAt(topLeft, width, height)
        landingImage.alpha = morph
    }

    /**
     * The path of the time's top centre: a curve that leaves the time straight
     * up, bends toward the status bar clock at [bendHeight], and comes in to
     * that clock from the side. Its control points hold it at or below that
     * height until it is well past the middle of the screen.
     */
    private fun flightPath(): Bezier {
        val startX = from.centerX()
        val end = landing.origin(target)
        val endX = end.x + landing.width / 2f
        val bend = bendHeight(startX, endX, end.y)
        return Bezier(
            startX, from.top,
            startX, bend,
            lerp(startX, endX, BEND_LEAD), bend,
            endX, end.y,
        )
    }

    /** At the status bar clock's height, or just below a camera cutout that lies between the time and it. */
    private fun bendHeight(startX: Float, endX: Float, landingY: Float): Float {
        val left = minOf(startX, endX) - from.width() / 2f
        val right = maxOf(startX, endX) + from.width() / 2f
        val cutoutBottom = time.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
            .filter { it.right > left && it.left < right && it.bottom > landingY }
            .maxOfOrNull { it.bottom }
            ?: return landingY
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

    /** Puts the image's top left corner at [topLeft] on screen, stretched to [width] by [height]. */
    private fun ImageView.placeAt(topLeft: PointF, width: Float, height: Float) {
        val origin = root.screenLocation()
        translationX = topLeft.x - origin[0]
        translationY = topLeft.y - origin[1]
        scaleX = width / layoutParams.width
        scaleY = height / layoutParams.height
    }

    private fun recycle() {
        pictures.recycle()
        landing.recycle()
    }

    companion object {
        const val WINDOW_TITLE = "ClockFlight"

        /** Share of the way along the path after which the time turns into the status bar clock's text. */
        const val MORPH_FROM = 0.6f

        /** How far toward the status bar clock the curve's second control point sits. */
        const val BEND_LEAD = 0.5f

        /** Space kept below a camera cutout while passing under it. */
        const val CUTOUT_CLEARANCE_DP = 8f

        /** The status bar clock counts as shown from this opacity. */
        const val SHOWN = 0.99f

        /** How far along its path the time has shrunk to the status bar clock's size. */
        const val SHRUNK_BY = 0.85f

        /** How far along the flight the time has fully come out from behind what was in front of it. */
        const val EMERGED_BY = 0.35f

        /** How much further along the flight a time faded by a fast swipe has come back up to full. */
        const val APPEARED_BY = 0.3f


        val COLOUR = ArgbEvaluator()

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

        fun smoothstep(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    }
}
