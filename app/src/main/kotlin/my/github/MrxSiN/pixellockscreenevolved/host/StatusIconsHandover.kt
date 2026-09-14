package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.widget.TextView

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
 */
internal class StatusIconsHandover(
    private val source: View,
    private val target: View,
    private val statusBarClock: TextView,
    logger: Logger,
) {

    private val picture: Bitmap = requireNotNull(ViewPictures.of(source))
    private val ink = inkColour(picture)
    private val from = source.screenLocation()
    private val image = ViewPictures.image(source, picture).apply { alpha = 0f }
    private val window = OverlayWindow(source.context, WINDOW_TITLE, logger).apply { root.addView(image) }
    private val sourceTransitionAlpha = source.transitionAlpha
    private var waitingForCommit = false
    private var committedFrames = 0
    private var framesAfterCommit = 0

    /** Shows the picture over the icons, hiding them as it first appears; false if the window could not be added. */
    fun show(): Boolean {
        if (!window.add()) {
            picture.recycle()
            return false
        }
        place(0f)
        window.onFirstFrame {
            image.alpha = 1f
            source.transitionAlpha = 0f
        }
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
        picture.recycle()
    }

    private fun place(shown: Float) {
        val origin = window.root.screenLocation()
        val to = target.screenLocation()
        val eased = shown * shown * (3f - 2f * shown)
        // The two rows of icons line up by their right edges and their middles.
        val fromRight = from[0] + source.width
        val toRight = to[0] + target.width
        val fromMiddle = from[1] + source.height / 2f
        val toMiddle = to[1] + target.height / 2f
        image.translationX = fromRight + (toRight - fromRight) * eased - source.width - origin[0]
        image.translationY = fromMiddle + (toMiddle - fromMiddle) * eased - source.height / 2f - origin[1]
        // The battery is drawn in more than one shade, its level in a dark cut against a light fill,
        // so the icons are multiplied by the tint rather than painted over with it.
        val tint = COLOUR.evaluate(eased, Color.WHITE, tintFrom(ink, statusBarClock.currentTextColor)) as Int
        image.colorFilter = if (tint == Color.WHITE) null else PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY)
    }

    private companion object {
        const val WINDOW_TITLE = "StatusIcons"

        /** Frames the picture stays after the status bar has committed a frame of its icons. */
        const val FRAMES_AFTER_COMMIT = 2

        /** The status bar's icons count as shown from this opacity. */
        const val SHOWN = 0.99f

        const val OPAQUE = 255

        val COLOUR = ArgbEvaluator()

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
