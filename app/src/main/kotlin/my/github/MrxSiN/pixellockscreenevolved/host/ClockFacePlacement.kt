package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.BOTTOM
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.MATCH_CONSTRAINT
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.PARENT_ID
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.TOP
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.WRAP_CONTENT

/**
 * Where a face sits on the lock screen and in the picker preview: centred, the
 * large face pinned under the status bar rather than floated down the screen
 * the way a Pixel clock is, then returned to SystemUI's fingerprint-aware
 * region on the always-on display; the small face as tall as its drawing rather
 * than the fixed band SystemUI gives a Pixel small clock. The small face spans
 * the width, which is what keeps SystemUI's date and weather line below it; a
 * narrower clock leaves room beside it, where SystemUI puts the line instead.
 *
 * On the lock screen SystemUI places both clock views by id before asking the
 * face, so only what differs is changed. The preview has no placement of its
 * own, so it is given one whole.
 */
internal class ClockFacePlacement(
    private val viewId: Int,
    private val size: FaceSize,
    private val largeClockTop: Int,
) {

    fun onLockScreen(editor: ConstraintSetEditor) {
        when (size) {
            FaceSize.LARGE -> pinToTop(editor, largeClockTop)
            FaceSize.SMALL -> {
                editor.constrainWidth(viewId, MATCH_CONSTRAINT)
                editor.constrainHeight(viewId, WRAP_CONTENT)
            }
        }
        editor.centerHorizontally(viewId)
    }

    fun inPreview(editor: ConstraintSetEditor, tops: PreviewTops) {
        editor.constrainWidth(viewId, WRAP_CONTENT)
        editor.constrainHeight(viewId, WRAP_CONTENT)
        pinToTop(
            editor,
            when (size) {
                FaceSize.LARGE -> maxOf(largeClockTop, tops.largeClock)
                FaceSize.SMALL -> tops.smallClock
            },
        )
        editor.centerHorizontally(viewId)
    }

    /**
     * Pins a large face hosted through Compose to the same top as the legacy
     * constraint path. As the display dozes, the pin is released back to the
     * movable element SystemUI places around the fingerprint sensor, as far as
     * [dozeFraction] says ([AodClockMotion]), after the time has come out in
     * front of the depth effect's subject. SystemUI eases the doze in and out as
     * the display sleeps and wakes, so the face glides between the two with it;
     * a notification arriving on the always-on
     * display swaps to the small face, which [ClockSizeMorph] carries from the
     * middle to its own place. The scene owns the outer movable element, so only the
     * plugin's inner view is translated; SystemUI can still move and scale its
     * holder's layers for transitions and burn-in protection. The scene moves
     * the holder as the display dozes, wakes and unlocks, which the view's own
     * layout never hears of, so the pin is checked before every draw
     * ([BeforeEachDraw]) and whenever a view holding it is laid out. Compose
     * lays its holders out while drawing, after the draw was prepared, so only
     * the second catches a move made there before the face is drawn at the
     * wrong height for a frame.
     */
    fun inComposedLockScreen(view: View, dozeFraction: () -> Float) {
        if (size != FaceSize.LARGE) return
        val pin = { pinComposedTop(view, dozeFraction()) }
        BeforeEachDraw.follow(view, pin)
        val holderMoved = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pin() }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            private var holders = emptyList<View>()

            override fun onViewAttachedToWindow(attached: View) {
                holders = generateSequence(attached.parent as? View) { it.parent as? View }.toList()
                holders.forEach { it.addOnLayoutChangeListener(holderMoved) }
            }

            override fun onViewDetachedFromWindow(detached: View) {
                holders.forEach { it.removeOnLayoutChangeListener(holderMoved) }
                holders = emptyList()
            }
        })
    }

    /**
     * Moves [view] so its top, as drawn with the scene's scale of the clock
     * ([ViewBounds]), lands on the large clock's awake top. As [doze] advances,
     * removes that correction so the scene's movable element alone owns the
     * always-on position.
     */
    private fun pinComposedTop(view: View, doze: Float) {
        if (view.height == 0) return
        val drawn = ViewBounds.inWindow(view)
        val scale = drawn.height() / view.height
        if (scale <= 0f) return
        val awakePin = view.translationY + (largeClockTop - drawn.top) / scale
        val pinned = AodClockMotion.placementCorrection(awakePin, doze)
        if (abs(view.translationY - pinned) >= SETTLED_PX) view.translationY = pinned
    }

    private fun pinToTop(editor: ConstraintSetEditor, top: Int) {
        editor.clear(viewId, TOP)
        editor.clear(viewId, BOTTOM)
        editor.connect(viewId, TOP, PARENT_ID, TOP, top)
    }

    private companion object {
        /** A pin off by less than this is left alone, so rounding never keeps redrawing the face. */
        const val SETTLED_PX = 0.5f
    }
}
