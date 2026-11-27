package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View

import kotlin.math.abs
import kotlin.math.hypot

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
 *
 * A large face its position turns ([ClockLayout.rotation]) is turned about its
 * middle, as a view property, so everything that reads where the face is drawn
 * (the depth effect's eraser, the card under the clock) follows it. The pin
 * then works with the face upright: the scene's scale is read from the length
 * of the face's turned x axis, and the top it aligns is that of the face
 * upright, its middle (which turning never moves) less half its height. The
 * turn is handed back to SystemUI with the placement as the display dozes, so
 * the always-on display shows the clock upright where SystemUI puts it.
 */
internal class ClockFacePlacement(
    private val viewId: Int,
    private val size: FaceSize,
    private val largeClockTop: Int,
    /** Where the large face stands awake within the band under [largeClockTop]. */
    private val layout: ClockLayout,
) {

    /** Where the face is drawn, measured again before each draw, and the matrix that drew it there. */
    private val drawn = RectF()
    private val toWindow = Matrix()
    private val rootInverse = Matrix()
    private val values = FloatArray(MATRIX_VALUES)

    /** Told the scale the scene draws the large face at, once it rests; null for a face that does not ask. */
    private var onDrawnScale: DrawnScale? = null

    /** Told the height the large face may fill, once it rests; null for a face that does not ask. */
    private var onRoom: ClockRoom? = null

    /** Told the height adaptive size gives the large face ([AdaptiveSize]) whenever it changes, NaN for none. */
    private var onFit: AdaptiveFit? = null
    private var lastFit = Float.NaN

    private var lastRoom = 0f
    private var lastScale = 0f

    /** The scale the scene draws the large face at once it rests, 0 before it has; adaptive size fits against it. */
    private var restingScale = 0f
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

    /**
     * Lays the face out in the picker preview. The large face stands where the lock screen pins it
     * ([onLockScreen]), not at the preview's own clock top, which leaves room above for SystemUI's date
     * row: every large face here writes its own date, and the preview drew the clock about 57 dp lower
     * than the lock screen.
     */
    fun inPreview(editor: ConstraintSetEditor, smallClockTop: Int) {
        editor.constrainWidth(viewId, WRAP_CONTENT)
        editor.constrainHeight(viewId, WRAP_CONTENT)
        val top = when (size) {
            FaceSize.LARGE -> largeClockTop.also { previewTop = it }
            FaceSize.SMALL -> smallClockTop
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
    fun inPickerPreview(view: View, onRoom: ClockRoom? = null, onFit: AdaptiveFit? = null) {
        if (size != FaceSize.LARGE) return
        this.onRoom = onRoom
        this.onFit = onFit
        ClockLayoutReport.follow(view.context.contentResolver)
        ClockLayoutReport.redrawOnChange(view)
        BeforeEachDraw.follow(view) { pinPreview(view) }
    }

    private fun pinPreview(view: View) {
        val parent = view.parent as? View ?: return
        if (previewTop < 0 || parent.height == 0) return
        turn(view, layout.rotation())
        // The preview lays the face out at the lock screen's own size, unscaled.
        reportRoom(view, parent.height * ClockLayoutReport.floorShare - previewTop)
        val moved = if (!layout.enabled) {
            0f
        } else {
            try {
                layout.reportedAdaptive = ClockLayoutReport.adaptiveShare
                layout.reportedEdge = ClockLayoutReport.edgeShare * parent.height
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
        // The preview lays the face out at the lock screen's own size, unscaled, in its parent's pixels.
        fitAcrossScreen(view, 0f, view.scaleX, layout.rotation(), parent.width.toFloat())
        // The preview lays the face out in its parent's pixels, unscaled.
        fitIfChanged(view, 1f)
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
    fun inComposedLockScreen(
        view: View,
        dozeFraction: DozeFraction,
        onDrawnScale: DrawnScale? = null,
        onRoom: ClockRoom? = null,
        onFit: AdaptiveFit? = null,
    ) {
        this.onDrawnScale = onDrawnScale
        this.onRoom = if (size == FaceSize.LARGE) onRoom else null
        this.onFit = if (size == FaceSize.LARGE) onFit else null
        // A face SystemUI builds anew, as it does for every clock setting changed in Wallpaper & style, starts from
        // the scale the last face of its size rested at: it fits at once, on the always-on display too, rather than
        // learning the scale 0.4 s into the next wake and jumping to its fitted size there.
        restedScales[size]?.let { rested ->
            restingScale = rested
            lastScale = rested
            onDrawnScale?.onScale(rested)
        }
        if (size != FaceSize.LARGE) {
            // The small face is never pinned, but may still need the scale it is drawn at.
            if (onDrawnScale != null) BeforeEachDraw.follow(view) { reportDrawnScale(view, dozeFraction.now()) }
            return
        }
        BeforeEachDraw.follow(view) { pinComposedTop(view, dozeFraction.now(), beforeDraw = true) }
        // Compose lays its holders out while drawing, so a pin made here reads where they are afresh.
        val holderMoved = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> pinComposedTop(view, dozeFraction.now(), beforeDraw = false) }
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
     * always-on position. [beforeDraw] as for [ViewBounds.inWindow].
     *
     * Measured in the root's own pixels: SystemUI also lays this scene out for
     * Wallpaper & style's lock screen preview, its root scaled down (0.49 on a
     * Pixel 8 Pro) to fit the preview card, while every top it is pinned to is
     * in the root's unscaled pixels. Measured in the window, the preview's clock
     * stood 175 px lower than on the lock screen.
     */
    private fun pinComposedTop(view: View, doze: Float, beforeDraw: Boolean) {
        if (view.height == 0) return
        val turned = layout.rotation() * (1f - AodClockMotion.towardAodPosition(doze))
        turn(view, turned)
        ViewBounds.toWindow(view, toWindow, beforeDraw)
        val root = view.rootView
        if (!root.matrix.isIdentity || root.left != 0 || root.top != 0) {
            toWindow.postTranslate(-root.left.toFloat(), -root.top.toFloat())
            if (root.matrix.invert(rootInverse)) toWindow.postConcat(rootInverse)
        }
        drawn.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        toWindow.mapRect(drawn)
        // The length of the face's x axis on screen: its scale whichever way it is turned.
        toWindow.getValues(values)
        val scale = hypot(values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y])
        if (scale <= 0f) return
        // The scene's own scale, without the face's own fit ([fitAcrossScreen]): an edge-to-edge face sizes its
        // time against it, and told the fit too, the turned iOS face grew to undo it, was fitted smaller again,
        // and shrank to nothing.
        val sceneScale = scale / view.scaleX.coerceAtLeast(MIN_ROOT_SCALE)
        // Told as drawn in the window, root scale included, as every other caller tells it.
        if (doze == 0f) reportRestingScale(view, sceneScale * root.scaleX)
        // The face upright and at its own size: its middle, which turning and fitting never move, less half its
        // height. Measured with the face's own fit, a face fitted smaller to turn stood higher by half of what it
        // lost, and Hyper on its side ran under the status bar. The view's translation is in its parent's
        // pixels, which the face's own fit does not scale.
        val uprightTop = drawn.centerY() - view.height * sceneScale / 2
        val awakePin = view.translationY + (awakeTop(view, doze) - uprightTop) / sceneScale
        // The always-on fit scales the face about its middle; at rest on the always-on display the face
        // keeps the top it had when that scale was taken about its top, as it was before faces turned.
        val fitShift = -(1f - view.scaleY) * view.height / 2
        var pinned = fitShift + AodClockMotion.placementCorrection(awakePin - fitShift, doze)
        if (doze > 0f) {
            // SystemUI's always-on place is made for its own clock; a taller face would reach down to the
            // fingerprint sensor there, so it is lifted to stand above the lock screen's floor.
            val bottom = drawn.bottom + (pinned - view.translationY) * sceneScale
            val floor = LockScreenFloor.of(view)
            if (bottom > floor) pinned -= (bottom - floor) / sceneScale
        }
        if (abs(view.translationY - pinned) >= SETTLED_PX) view.translationY = pinned
        fitAcrossScreen(view, doze, scale, turned, view.rootView.width.toFloat())
        // Against the scale the face rests at awake ([restingScale]), asleep too: a setting changed in Wallpaper &
        // style is then fitted while the display dozes, not 0.4 s after the next wake, where it showed as the clock
        // jumping to a new size. Only on the lock screen itself: SystemUI also draws its faces into the picker's
        // small previews, scaled-down windows that the subject's screen-pixel edge does not map onto.
        if (view.isInNotificationShade()) fitIfChanged(view, restingScale)
    }

    /**
     * Shrinks a large face by as much as it would run past the screen's text margins, in a [screenWidth]
     * wide window, as the face is drawn at [scale] (its own scale included) and turned [turned] degrees:
     *
     * - an edge-to-edge face ([EdgeToEdgeClockFace]) as the display dozes: the scene draws the clock
     *   larger on the always-on display than awake, and the time laid out to span the awake column was
     *   cut off at the screen's edges there;
     * - any face while turned: the box a turned face covers is wider than the face, and Hyper's and
     *   Cinema's ran off the screen's side at 30 degrees.
     *
     * Measured before every draw, so it follows the doze and a turn without a step; upright and awake
     * the face fits, and it is left at its own size.
     */
    private fun fitAcrossScreen(view: View, doze: Float, scale: Float, turned: Float, screenWidth: Float) {
        val room = screenWidth - 2 * EdgeToEdgeClockFace.EDGE_DP * view.resources.displayMetrics.density
        if (room <= 0f || view.scaleX <= 0f) return
        val naturalWidth = view.width * scale / view.scaleX
        val naturalHeight = view.height * scale / view.scaleX
        val dozeFit = if (onDrawnScale != null && doze > 0f && naturalWidth > room) room / naturalWidth else 1f
        val turnedWidth = ClockRotation.extentWidth(naturalWidth, naturalHeight, turned)
        val turnFit = if (turned != 0f && turnedWidth > room) room / turnedWidth else 1f
        val fit = minOf(dozeFit, turnFit)
        if (abs(view.scaleX - fit) < SCALE_SETTLED) return
        centrePivot(view)
        view.scaleX = fit
        view.scaleY = fit
    }

    /**
     * Tells the face the height adaptive size now gives it, in the face's own pixels (the band's, which
     * are the window's, over the [scale] the scene draws it at), when that has changed by
     * [FIT_SETTLED_PX] or more or come or gone; after this draw, as resizing the face lays it out again.
     */
    private fun fitIfChanged(view: View, scale: Float) {
        val fit = onFit ?: return
        if (scale <= 0f) return
        val height = layout.adaptiveHeight() / scale
        if (height.isNaN() == lastFit.isNaN() && (height.isNaN() || abs(height - lastFit) < FIT_SETTLED_PX)) return
        lastFit = height
        view.post { fit.fit(height) }
    }

    /** Turns [view] [degrees] clockwise about its middle, unless it is turned so already. */
    private fun turn(view: View, degrees: Float) {
        if (abs(view.rotation - degrees) < TURN_SETTLED) return
        centrePivot(view)
        view.rotation = degrees
    }

    /** Turns and fits about the face's middle, the one point both keep where it is. */
    private fun centrePivot(view: View) {
        if (view.pivotX != view.width / 2f) view.pivotX = view.width / 2f
        if (view.pivotY != view.height / 2f) view.pivotY = view.height / 2f
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
        if (onDrawnScale == null && onRoom == null && onFit == null) return
        // Against the window's own root, which a preview may draw scaled.
        val rootScale = view.rootView.scaleX
        if (rootScale <= 0f) return
        val scale = drawnScale / rootScale
        val now = System.nanoTime()
        if (abs(scale - lastScale) >= RESTING_SCALE_DELTA) {
            lastScale = scale
            steadySince = now
        } else if (now - steadySince >= RESTING_NANOS) {
            restingScale = scale
            restedScales[size] = scale
            onDrawnScale?.onScale(scale)
            if (onRoom != null) reportRoom(view, (LockScreenFloor.of(view) - largeClockTop) / scale)
        }
    }

    /**
     * Tells the face the height it may take in [room], when that differs from what it was last told by a
     * pixel or more: all of it upright, less turned, so the box the face covers turned stays in it.
     */
    private fun reportRoom(view: View, room: Float) {
        val listener = onRoom ?: return
        if (room <= 0f) return
        val upright = ClockRotation.uprightRoom(room, view.width.toFloat(), layout.position.rotation)
        if (abs(upright - lastRoom) < 1f) return
        lastRoom = upright
        listener.onRoom(upright)
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
            val top = layout.awakeTop(largeClockTop.toFloat(), floor, left, left + view.width, view.height.toFloat(), glide = doze == 0f)
            if (root.height > 0 && view.isInNotificationShade()) {
                ClockLayoutReport.putEdge(view.context.contentResolver, layout.subjectEdge / root.height)
            }
            top
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
        /** The scale the scene last drew a face of each size at while it rested; on the main thread. */
        val restedScales = java.util.EnumMap<FaceSize, Float>(FaceSize::class.java)

        /** A pin off by less than this is left alone, so rounding never keeps redrawing the face. */
        const val SETTLED_PX = 0.5f

        /** A fitting scale off by less than this is left alone. */
        const val SCALE_SETTLED = 0.001f

        /** An adaptive height off by less than this, in pixels, from the one the face was fitted to is left alone. */
        const val FIT_SETTLED_PX = 6f

        /** A turn off by less than this many degrees is left alone. */
        const val TURN_SETTLED = 0.01f

        const val MATRIX_VALUES = 9

        /** Drawn scales closer than this are taken for the same. */
        const val RESTING_SCALE_DELTA = 0.0005f

        /** A face scale below this is taken for none, so the scene's scale is never divided by nothing. */
        const val MIN_ROOT_SCALE = 0.01f

        /** How long the drawn scale must hold before the face is fitted to it: longer than the tail of a wake's zoom. */
        const val RESTING_NANOS = 250_000_000L
    }
}

/** Hears the scale the lock screen scene draws a face at. A primitive `fun interface`, so a frame boxes no `Float`. */
internal fun interface DrawnScale {
    fun onScale(scale: Float)
}

/** Hears the height a large face may fill ([my.github.MrxSiN.pixellockscreenevolved.clock.FillingClockFace]). */
internal fun interface ClockRoom {
    fun onRoom(height: Float)
}

/** Hears the upright height adaptive size gives a large face ([AdaptiveSize]), NaN where none is given. */
internal fun interface AdaptiveFit {
    fun fit(height: Float)
}

/** How far a face has gone into the always-on display, read before every frame, so it is handed over unboxed. */
internal fun interface DozeFraction {
    fun now(): Float
}
