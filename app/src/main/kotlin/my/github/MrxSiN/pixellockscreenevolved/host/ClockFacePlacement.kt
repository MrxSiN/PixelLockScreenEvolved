package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.RectF
import android.view.View

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.core.AndroidLogger
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.BOTTOM
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.MATCH_CONSTRAINT
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.PARENT_ID
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.TOP
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.WRAP_CONTENT

/**
 * Where a face sits on the lock screen and in the picker preview: centred, the
 * large face pinned under the status bar, or as far down the screen as its
 * clock setting places it ([ClockLayout]), rather than floated down the screen
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
    /** Where the large face stands awake within the band under [largeClockTop]. */
    private val layout: ClockLayout,
) {

    /** Where the face is drawn, measured again before each draw. */
    private val drawn = RectF()

    /** Told the scale the scene draws the large face at, once it rests; null for a face that does not ask. */
    private var onDrawnScale: DrawnScale? = null
    private var lastScale = 0f
    private var steadySince = 0L

    /** The top the picker preview's constraints last gave the large face, or -1 before they have. */
    private var previewTop = -1

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
        val top = when (size) {
            FaceSize.LARGE -> maxOf(largeClockTop, tops.largeClock).also { previewTop = it }
            FaceSize.SMALL -> tops.smallClock
        }
        pinToTop(editor, top)
        editor.centerHorizontally(viewId)
    }

    /**
     * Moves a large face in Wallpaper & style's preview from the top its
     * constraints give it ([inPreview]) to where its position puts it, before
     * every draw, so it follows the position slider as it moves. The preview
     * cannot read the fingerprint sensor or the photo, so the band's foot and
     * an adaptive clock's anchor are what SystemUI last reported
     * ([ClockLayoutReport]).
     */
    fun inPickerPreview(view: View) {
        if (size != FaceSize.LARGE) return
        ClockLayoutReport.follow(view.context.contentResolver)
        BeforeEachDraw.follow(view) { pinPreview(view) }
    }

    private fun pinPreview(view: View) {
        val parent = view.parent as? View ?: return
        if (previewTop < 0 || parent.height == 0) return
        val moved = if (!layout.enabled) {
            0f
        } else {
            try {
                layout.reportedAdaptive = ClockLayoutReport.adaptiveShare
                val left = (parent.width - view.width) / 2f
                layout.awakeTop(
                    previewTop.toFloat(), parent.height * ClockLayoutReport.floorShare,
                    left, left + view.width, view.height.toFloat(), glide = true,
                ) - previewTop
            } catch (error: RuntimeException) {
                layout.enabled = false
                AndroidLogger.warn("Clock position failed in the preview; the clock stands at the top", error)
                0f
            }
        }
        if (abs(view.translationY - moved) >= SETTLED_PX) view.translationY = moved
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
    fun inComposedLockScreen(view: View, dozeFraction: DozeFraction, onDrawnScale: DrawnScale? = null) {
        this.onDrawnScale = onDrawnScale
        if (size != FaceSize.LARGE) {
            // The small face is never pinned, but may still need the scale it is drawn at.
            if (onDrawnScale != null) BeforeEachDraw.follow(view) { reportDrawnScale(view, dozeFraction.now()) }
            return
        }
        val pin = { pinComposedTop(view, dozeFraction.now()) }
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
        ViewBounds.inWindow(view, drawn)
        val scale = drawn.height() / view.height
        if (scale <= 0f) return
        if (doze == 0f) reportRestingScale(view, scale)
        val awakePin = view.translationY + (awakeTop(view, doze) - drawn.top) / scale
        var pinned = AodClockMotion.placementCorrection(awakePin, doze)
        if (doze > 0f) {
            // SystemUI's always-on place is made for its own clock; a taller face would reach down to the
            // fingerprint sensor there, so it is lifted to stand above the lock screen's floor.
            val bottom = drawn.bottom + (pinned - view.translationY) * scale
            val floor = LockScreenFloor.of(view)
            if (bottom > floor) pinned -= (bottom - floor) / scale
        }
        if (abs(view.translationY - pinned) >= SETTLED_PX) view.translationY = pinned
        if (onDrawnScale != null) fitAcrossScreen(view, doze)
    }

    /**
     * Shrinks an edge-to-edge face ([EdgeToEdgeClockFace]) as the display dozes,
     * by as much as it would run past the screen's text margins: the scene draws
     * the clock larger on the always-on display than awake, and the time laid
     * out to span the awake column was cut off at the screen's edges there.
     * Measured before every draw, so it follows the doze without a step; awake
     * the face fits, and it is left at its own size.
     */
    private fun fitAcrossScreen(view: View, doze: Float) {
        val room = view.rootView.width - 2 * EdgeToEdgeClockFace.EDGE_DP * view.resources.displayMetrics.density
        val natural = drawn.width() / view.scaleX
        val fit = if (doze > 0f && natural > room && room > 0f) room / natural else 1f
        if (abs(view.scaleX - fit) < SCALE_SETTLED) return
        view.pivotX = view.width / 2f
        view.pivotY = 0f
        view.scaleX = fit
        view.scaleY = fit
    }

    private fun reportDrawnScale(view: View, doze: Float) {
        if (view.height == 0 || doze != 0f) return
        ViewBounds.inWindow(view, drawn)
        reportRestingScale(view, drawn.height() / view.height)
    }

    /**
     * Tells the face the scale the scene draws it at once that scale has held
     * for [RESTING_NANOS], so the zoom of a wake or an unlock, slow at its end,
     * never resizes the time part way; resting, the face ignores a scale it
     * already has. (The pin runs more than once a frame, so two readings in a
     * row could be one frame.)
     */
    private fun reportRestingScale(view: View, drawnScale: Float) {
        val listener = onDrawnScale ?: return
        // Against the window's own root, which a preview may draw scaled.
        val rootScale = view.rootView.scaleX
        if (rootScale <= 0f) return
        val scale = drawnScale / rootScale
        val now = System.nanoTime()
        if (abs(scale - lastScale) >= RESTING_SCALE_DELTA) {
            lastScale = scale
            steadySince = now
        } else if (now - steadySince >= RESTING_NANOS) {
            listener.onScale(scale)
        }
    }

    /**
     * Where the large face's top stands awake, in window pixels: in the band
     * from under the status bar to the lock screen's floor ([LockScreenFloor]),
     * as [layout] places it. The lock screen's own window reports the floor for
     * the picker's preview.
     */
    private fun awakeTop(view: View, doze: Float): Float {
        if (!layout.enabled) return largeClockTop.toFloat()
        return try {
            val root = view.rootView
            val floor = LockScreenFloor.of(view)
            if (root.height > 0 && view.isInNotificationShade()) ClockLayoutReport.putFloor(view.context.contentResolver, floor / root.height)
            val left = (root.width - view.width) / 2f
            layout.awakeTop(largeClockTop.toFloat(), floor, left, left + view.width, view.height.toFloat(), glide = doze == 0f)
        } catch (error: RuntimeException) {
            // A failing position never takes the clock, or SystemUI, down with it: the clock goes back to the top.
            layout.enabled = false
            AndroidLogger.warn("Clock position failed; the clock stands at the top", error)
            largeClockTop.toFloat()
        }
    }

    private fun pinToTop(editor: ConstraintSetEditor, top: Int) {
        editor.clear(viewId, TOP)
        editor.clear(viewId, BOTTOM)
        editor.connect(viewId, TOP, PARENT_ID, TOP, top)
    }

    private companion object {
        /** A pin off by less than this is left alone, so rounding never keeps redrawing the face. */
        const val SETTLED_PX = 0.5f

        /** A fitting scale off by less than this is left alone. */
        const val SCALE_SETTLED = 0.001f

        /** Drawn scales closer than this are taken for the same. */
        const val RESTING_SCALE_DELTA = 0.0005f

        /** How long the drawn scale must hold before the face is fitted to it: longer than the tail of a wake's zoom. */
        const val RESTING_NANOS = 250_000_000L
    }
}

/** Hears the scale the lock screen scene draws a face at. A primitive `fun interface`, so a frame boxes no `Float`. */
internal fun interface DrawnScale {
    fun onScale(scale: Float)
}

/** How far a face has gone into the always-on display, read before every frame, so it is handed over unboxed. */
internal fun interface DozeFraction {
    fun now(): Float
}
