package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * Where one large face of this module's stands while awake: as far down as its
 * clock setting places it ([ClockPosition]), or, for an adaptive position, at
 * the anchor that hides least of the photo's subject ([AdaptivePlacement]);
 * while a finger drags it in Wallpaper & style's preview, under the finger
 * ([dragTo]).
 *
 * [awakeTop] is asked before every frame ([ClockFacePlacement]) and answers
 * from what it last worked out, comparing its inputs field by field; only when
 * one of them changes (the band the clock may stand in, the face's size, the
 * position chosen, the subject's cut-out or where the photo is placed) is the
 * anchor chosen again, so a frame never scores anchors or reads the subject.
 *
 * The subject's cut-out is only ever read from the depth effect's, as it has
 * already been found ([showSubject]); nothing is segmented for the clock. With
 * none, an adaptive clock stands at the top. Where the photo cannot be read at
 * all, as in Wallpaper & style's preview, an adaptive clock stands where
 * SystemUI last chose ([ClockLayoutReport]).
 *
 * The dependency order is one way: the photo's subject and the band give the
 * anchor, the anchor gives the face's place, and the face's place feeds the
 * depth effect's erasing ([DepthLayer]) and the card under it, neither of which
 * this reads. A face laid out around the subject ([SubjectAwareClockFace])
 * would close that loop, so it is never moved ([movable]).
 *
 * A move once a clock is placed glides on the system's spatial spring while
 * the lock screen is up, awake and animating; otherwise it is made at once.
 */
internal class ClockLayout(private val view: View, private val movable: Boolean) {

    /** Whether positions apply at all; off, the face stands at the top, as it did before there were any. */
    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            view.postInvalidate()
        }

    /** The position the clock setting chooses. */
    var position: ClockPosition = ClockPosition.TOP
        set(value) {
            if (field == value) return
            field = value
            view.postInvalidate()
        }

    /** The share SystemUI last put an adaptive clock at, for a face that cannot read the photo. */
    var reportedAdaptive: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            view.postInvalidate()
        }

    /** Called with the share of the anchor an adaptive clock moved to, on the main thread. */
    var onAdaptiveChosen: (Float) -> Unit = {}

    private var feed: LockWallpaperFeed? = null
    private var cutout: Bitmap? = null
    private var ink: SubjectInk? = null

    // What the anchor was last chosen from, compared before each frame.
    private var chosenOnce = false
    /** The band the clock last stood in and its height, in the pin's pixels, for a drag to measure against. */
    var bandTop = 0f
        private set
    var bandBottom = 0f
        private set
    var faceHeight = 0f
        private set
    private var faceLeft = 0f
    private var faceRight = 0f
    private var chosenFor = ClockPosition.TOP
    private var reportedFor = 0f
    private var inkFor: SubjectInk? = null
    private var cropFor: Any? = null
    private var scrollFor: Any? = null

    private var adaptive = -1
    private val covered = FloatArray(ClockPosition.ANCHORS.size)

    private var placed = false
    private var target = 0f
    private var from = 0f
    private var progress = 1f
    private var move: ValueAnimator? = null
    private var dragTop = Float.NaN

    /**
     * Uses the depth effect's [cutout] of the photo [feed] places, or none,
     * for an adaptive clock. On the main thread.
     */
    fun showSubject(cutout: Bitmap?, feed: LockWallpaperFeed) {
        this.feed = feed
        if (this.cutout === cutout) return
        this.cutout = cutout
        ink = cutout?.let(SubjectInk::of)?.takeUnless(SubjectInk::isEmpty)
        view.invalidate()
    }

    /**
     * The top, in window pixels, for the face whose box runs from [left] to
     * [right] and is [height] tall, in the band from [top] to [bottom]; [glide]
     * says whether a move now may be seen. The box is the face as laid out,
     * not as the scene scales it while waking, so a waking frame changes none
     * of it.
     */
    fun awakeTop(top: Float, bottom: Float, left: Float, right: Float, height: Float, glide: Boolean): Float {
        if (!movable || !enabled) return top
        if (!chosenOnce || changed(top, bottom, left, right, height)) {
            remember(top, bottom, left, right, height)
            // Where the lock screen reads the photo, the adaptive anchor is kept chosen whatever position is
            // set, so the picker's preview can show it before Adaptive is applied.
            val adaptiveShare = if (feed != null || position.adaptive) chooseAdaptive(top, bottom, left, right, height) else 0f
            val share = if (position.adaptive) adaptiveShare else position.share
            moveTo(ClockPosition.topAt(share, top, bottom, height), glide && dragTop.isNaN())
        }
        return if (dragTop.isNaN()) from + (target - from) * progress else dragTop
    }

    /** Whether the face can be moved at all, as its style and the feature allow. */
    val isMovable: Boolean get() = movable && enabled

    /** Holds the face's top at [top], in the pin's pixels, under a dragging finger. On the main thread. */
    fun dragTo(top: Float) {
        move?.cancel()
        move = null
        dragTop = top
        view.invalidate()
    }

    /** Lets the face go from under the finger: it glides from there to where [position] puts it. */
    fun endDrag() {
        if (dragTop.isNaN()) return
        from = dragTop
        target = dragTop
        progress = 1f
        dragTop = Float.NaN
        chosenOnce = false
        view.invalidate()
    }

    private fun changed(top: Float, bottom: Float, left: Float, right: Float, height: Float): Boolean {
        if (top != bandTop || bottom != bandBottom || height != faceHeight || position != chosenFor) return true
        if (feed == null && !position.adaptive) return false
        if (left != faceLeft || right != faceRight || reportedAdaptive != reportedFor || ink !== inkFor) return true
        val feed = feed ?: return false
        return ink != null && (feed.placementCrop != cropFor || feed.placementScroll != scrollFor)
    }

    private fun remember(top: Float, bottom: Float, left: Float, right: Float, height: Float) {
        chosenOnce = true
        bandTop = top
        bandBottom = bottom
        faceLeft = left
        faceRight = right
        faceHeight = height
        chosenFor = position
        reportedFor = reportedAdaptive
        inkFor = ink
        cropFor = feed?.placementCrop
        scrollFor = feed?.placementScroll
    }

    /** The share an adaptive clock stands at. */
    private fun chooseAdaptive(top: Float, bottom: Float, left: Float, right: Float, height: Float): Float {
        val feed = feed
        // No photo read here, as in the picker's preview: where SystemUI last put an adaptive clock.
        if (feed == null) return reportedAdaptive
        val subject = ink
        val photo = cutout
        val chosen = if (subject == null || photo == null) {
            0
        } else {
            val metrics = view.resources.displayMetrics
            val placed = feed.place(
                WallpaperPlacement.Size(metrics.widthPixels, metrics.heightPixels),
                WallpaperPlacement.Size(photo.width, photo.height),
                zoomed = false,
            )
            for (i in covered.indices) {
                val anchorTop = ClockPosition.topAt(ClockPosition.ANCHORS[i], top, bottom, height)
                covered[i] = subject.coverage(
                    toPhotoX(placed, left, photo.width),
                    toPhotoY(placed, anchorTop, photo.height),
                    toPhotoX(placed, right, photo.width),
                    toPhotoY(placed, anchorTop + height, photo.height),
                )
            }
            AdaptivePlacement.choose(covered, adaptive)
        }
        if (chosen != adaptive) {
            adaptive = chosen
            onAdaptiveChosen(ClockPosition.ANCHORS[chosen])
        }
        return ClockPosition.ANCHORS[chosen]
    }

    /** Glides from where the face stands now to [top], or goes there at once. */
    private fun moveTo(top: Float, glide: Boolean) {
        if (placed && top == target) return
        val shown = from + (target - from) * progress
        move?.cancel()
        move = null
        from = shown
        target = top
        progress = 1f
        val first = !placed
        placed = true
        if (first || !glide || SystemMotion.isReduced || !view.isShown) return
        progress = 0f
        move = MOVE.animator(
            update = { seconds ->
                progress = MOVE.valueAt(seconds)
                view.invalidate()
            },
            end = {
                progress = 1f
                move = null
                view.invalidate()
            },
        ).apply { start() }
    }

    private companion object {
        /** A whole clock moving down the screen: the expressive scheme's spatial spring for large things. */
        val MOVE = ExpressiveSpring.DEFAULT_SPATIAL

        /** Where a point on screen falls on the photo, in the photo's pixels: [WallpaperPlacement.Placed.toScreenX] undone. */
        fun toPhotoX(placed: WallpaperPlacement.Placed, screenX: Float, photoWidth: Int): Float =
            ((screenX - placed.pivotX) / placed.scale + placed.pivotX - placed.left) * photoWidth / placed.width

        fun toPhotoY(placed: WallpaperPlacement.Placed, screenY: Float, photoHeight: Int): Float =
            ((screenY - placed.pivotY) / placed.scale + placed.pivotY - placed.top) * photoHeight / placed.height
    }
}
