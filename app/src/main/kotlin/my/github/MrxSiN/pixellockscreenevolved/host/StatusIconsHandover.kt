package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.host.ViewPictures.screenLocation

/**
 * Keeps the lock screen's status icons and battery ([source]) steady on screen
 * through an unlock, until the status bar's own ([target]) take their place, so
 * the same icons do not fade out and back in.
 *
 * The keyguard fades within a few frames of an unlock starting and SystemUI only
 * brings the status bar back some time after, so a picture of the icons is held
 * in a window of its own; the lock screen's icons are hidden as soon as it has
 * reached the screen. As SystemUI shows the status bar's icons, the picture eases
 * onto them, in place and in colour ([statusBarClock]'s, which the status bar
 * tints its icons with; it can darken toward that tint but not lighten beyond
 * the lock screen's icons), and goes once they are on screen ([follow]).
 *
 * The two rows are not spaced alike: the lock screen keeps its status icons
 * further from the battery. The picture is therefore cut into [Piece]s, the
 * status icons and what is left around them, and each lands on its own
 * counterpart, so nothing jumps when the picture gives way.
 */
internal class StatusIconsHandover(
    private val source: View,
    private val target: View,
    private val statusBarClock: TextView,
    logger: Logger,
) {

    private val picture: Bitmap = requireNotNull(ViewPictures.of(source))
    private val ink = inkColour(picture)
    private val pieces = cut(picture)
    private val window = OverlayWindow(source.context, WINDOW_TITLE, logger).apply { pieces.forEach { root.addView(it.image) } }
    private val sourceTransitionAlpha = source.transitionAlpha
    private var waitingForCommit = false
    private var committedFrames = 0
    private var framesAfterCommit = 0

    /**
     * Shows the picture over the icons, hiding them once it has reached the
     * screen; false if the window could not be added. The picture is shown from
     * its window's first frame: waiting to show it together with hiding the icons
     * left neither on screen for a frame or two, since the two windows draw apart.
     */
    fun show(): Boolean {
        if (!window.add()) {
            recycle()
            return false
        }
        place(0f)
        window.onFirstFrameShown { source.transitionAlpha = 0f }
        return true
    }

    /**
     * Follows the status bar's icons for one frame. The picture stays solid over
     * them, which SystemUI shows beneath it, and goes once they are fully shown
     * and the status bar has committed a frame of them and drawn once more; true
     * then. Changes this module made to the status bar's own views did not reach
     * the screen reliably in time, so they are left entirely to SystemUI.
     */
    fun follow(): Boolean {
        val shown = ViewPictures.visibleAlpha(target)
        place(shown)
        if (shown < SHOWN) return false
        if (!waitingForCommit) {
            waitingForCommit = true
            target.viewTreeObserver.registerFrameCommitCallback { committedFrames++ }
            target.invalidate()
            return false
        }
        if (committedFrames == 0) {
            target.invalidate()
            return false
        }
        return ++framesAfterCommit > FRAMES_AFTER_COMMIT
    }

    /** Takes the picture away and shows both sets of icons again. */
    fun remove() {
        source.transitionAlpha = sourceTransitionAlpha
        if (!window.isAdded) return
        window.remove()
        recycle()
    }

    private fun recycle() {
        pieces.forEach { it.picture.recycle() }
    }

    private fun place(shown: Float) {
        val origin = window.root.screenLocation()
        val eased = shown * shown * (3f - 2f * shown)
        // The battery is drawn in more than one shade, its level in a dark cut against a light fill,
        // so the icons are multiplied by the tint rather than painted over with it.
        val tint = COLOUR.evaluate(eased, Color.WHITE, tintFrom(ink, statusBarClock.currentTextColor)) as Int
        val filter = if (tint == Color.WHITE) null else PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY)
        pieces.forEach { it.place(eased, origin, filter) }
    }

    /**
     * Cuts [whole] into a piece for each part both rows name alike, and the rest
     * of it, which lands on the whole of [target]. [whole] becomes that rest.
     */
    private fun cut(whole: Bitmap): List<Piece> {
        val parts = MATCHED_PARTS.mapNotNull { name ->
            val from = source.findViewByName<View>(name)?.takeIf { it.isShown && it.width > 0 } ?: return@mapNotNull null
            val to = target.findViewByName<View>(name)?.takeIf { it.width > 0 } ?: return@mapNotNull null
            val area = ViewBounds.inAncestor(source, from).let {
                Rect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt())
            }
            if (!area.intersect(0, 0, whole.width, whole.height)) return@mapNotNull null
            Piece(from, to, Bitmap.createBitmap(whole, area.left, area.top, area.width(), area.height()))
                .also { Canvas(whole).drawRect(area, ERASE) }
        }
        return parts + Piece(source, target, whole)
    }

    /**
     * One part of the lock screen's icons, pictured, easing onto its counterpart
     * on the status bar. The two line up by the right edge of what each shows and
     * by their middles. What they show, not their bounds: the lock screen's status
     * icons sit in a wider container than the status bar's, from the same left.
     */
    private class Piece(private val from: View, private val to: View, val picture: Bitmap) {

        private val fromLeft = from.screenLocation()[0]
        private val fromRight = shownRight(from)
        private val fromMiddle = from.screenLocation()[1] + from.height / 2f
        val image: ImageView = ViewPictures.image(from, picture)

        fun place(eased: Float, origin: IntArray, filter: PorterDuffColorFilter?) {
            val toRight = shownRight(to)
            val toMiddle = to.screenLocation()[1] + to.height / 2f
            image.translationX = fromLeft + (toRight - fromRight) * eased - origin[0]
            image.translationY = fromMiddle + (toMiddle - fromMiddle) * eased - picture.height / 2f - origin[1]
            image.colorFilter = filter
        }

        /** The right edge on screen of what [view] shows: its visible children's, or its own if it has none. */
        private fun shownRight(view: View): Float {
            val shown = (view as? ViewGroup)?.let { group -> (0 until group.childCount).map(group::getChildAt) }
                ?.filter { it.visibility == View.VISIBLE && it.alpha > 0f && it.width > 0 }
                ?.maxOfOrNull { it.screenLocation()[0] + it.width }
            return (shown ?: (view.screenLocation()[0] + view.width)).toFloat()
        }
    }

    private companion object {
        const val WINDOW_TITLE = "StatusIcons"

        /** Parts of the icons both rows name alike and space differently. */
        val MATCHED_PARTS = listOf("statusIcons")

        /** Frames the picture stays after the status bar has committed a frame of its icons. */
        const val FRAMES_AFTER_COMMIT = 2

        /** The status bar's icons count as shown from this opacity. */
        const val SHOWN = 0.99f

        const val OPAQUE = 255

        val COLOUR = ArgbEvaluator()

        val ERASE = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

        /** The tint that turns icons drawn in [ink] into ones drawn in [wanted], by multiplying. */
        fun tintFrom(ink: Int, wanted: Int): Int {
            fun channel(from: Int, to: Int) = if (from == 0) OPAQUE else (to * OPAQUE / from).coerceIn(0, OPAQUE)
            return Color.rgb(
                channel(Color.red(ink), Color.red(wanted)),
                channel(Color.green(ink), Color.green(wanted)),
                channel(Color.blue(ink), Color.blue(wanted)),
            )
        }

        /** The colour the icons are drawn in, read off the picture's first solid pixel along its middle. */
        fun inkColour(picture: Bitmap): Int {
            val y = picture.height / 2
            return (0 until picture.width).asSequence()
                .map { picture.getPixel(it, y) }
                .firstOrNull { Color.alpha(it) == OPAQUE }
                ?: Color.WHITE
        }
    }
}
