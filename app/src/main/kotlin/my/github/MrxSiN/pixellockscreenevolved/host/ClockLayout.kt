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
 * this reads. Only a large face moves ([movable]).
 *
 * A turned clock ([ClockPosition.rotation]) is placed by the box it covers
 * turned, so its corners stay in the band, and is scored by that box when
 * adaptive. Its turn is told before each frame ([rotation]) and applied about
 * the face's middle by the pin, so a turn never moves the clock's middle.
 *
 * A move or a turn once a clock is placed glides on the system's spatial
 * spring while the lock screen is up, awake and animating; otherwise it is
 * made at once.
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

    /** Where SystemUI last found the subject's top edge, in this window's pixels, for a face that cannot read the photo; NaN for none. */
    var reportedEdge: Float = Float.NaN
        set(value) {
            if (field.toBits() == value.toBits()) return
            field = value
            view.postInvalidate()
        }

    /**
     * Where the subject first rises into the face's columns below the band's top, in window pixels, as
     * last worked out with the anchor; NaN where there is no subject there, or none is known.
     */
    var subjectEdge: Float = Float.NaN
        private set

    /** The upright height adaptive size gives the face ([AdaptiveSize]), or NaN with no subject edge. */
    fun adaptiveHeight(): Float =
        AdaptiveSize.height(subjectEdge, bandTop, bandBottom, if (position.adaptive) 0f else position.share)

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
    private var edgeFor = Float.NaN
    private var inkFor: SubjectInk? = null
    private var cropFor: Any? = null
    private var scrollFor: Any? = null

    private var adaptive = -1
    private val covered = FloatArray(ClockPosition.ANCHORS.size)

    private var placed = false
    private var target = 0f
    private var from = 0f
    private var turnTarget = 0f
    private var turnFrom = 0f
    private var progress = 1f
    private var move: ValueAnimator? = null
    private var dragTop = Float.NaN
    private var dragTurn = Float.NaN

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
            // The face turns about its middle, so it is placed and scored by the box it covers turned.
            val turned = position.rotation
            val extentWidth = ClockRotation.extentWidth(right - left, height, turned)
            val extent = ClockRotation.extentHeight(right - left, height, turned)
            val middle = (left + right) / 2
            // Where the lock screen reads the photo, the adaptive anchor is kept chosen whatever position is
            // set, so the picker's preview can show it before Adaptive is applied.
            val adaptiveShare = if (feed != null || position.adaptive) {
                chooseAdaptive(top, bottom, middle - extentWidth / 2, middle + extentWidth / 2, extent)
            } else {
                0f
            }
            val share = if (position.adaptive) adaptiveShare else position.share
            subjectEdge = findEdge(top, bottom, left, right)
            moveTo(ClockPosition.topAt(share, top, bottom, height, extent), turned, glide && dragTop.isNaN() && dragTurn.isNaN())
        }
        return if (dragTop.isNaN()) from + (target - from) * progress else dragTop
    }

    /**
     * How far the face is turned now, in degrees clockwise: under a turning finger, gliding to a turn
     * just chosen, or as its position turns it; upright where positions do not apply.
     */
    fun rotation(): Float = when {
        !movable || !enabled -> 0f
        !dragTurn.isNaN() -> dragTurn
        else -> turnFrom + (turnTarget - turnFrom) * progress
    }

    /** Whether the face can be moved at all, as its style and the feature allow. */
    val isMovable: Boolean get() = movable && enabled

    /** The top, in the pin's pixels, of the face upright when it stands at [share] of the band as its position turns it. */
    fun topAt(share: Float): Float =
        ClockPosition.topAt(share, bandTop, bandBottom, faceHeight, ClockRotation.extentHeight(faceRight - faceLeft, faceHeight, position.rotation))

    /** The share of the band the face upright with its top at [top] stands at, as its position turns it; unclamped. */
    fun shareAt(top: Float): Float =
        ClockPosition.shareAt(top, bandTop, bandBottom, faceHeight, ClockRotation.extentHeight(faceRight - faceLeft, faceHeight, position.rotation))

    /** Turns the face to [degrees] under a turning finger. On the main thread. */
    fun turnTo(degrees: Float) {
        move?.cancel()
        move = null
        dragTurn = degrees
        view.invalidate()
    }

    /** Lets the face go from under a turning finger: it glides from there to what [position] turns it to. */
    fun endTurn() {
        if (dragTurn.isNaN()) return
        val shownTop = from + (target - from) * progress
        from = shownTop
        target = shownTop
        turnFrom = dragTurn
        turnTarget = dragTurn
        progress = 1f
        dragTurn = Float.NaN
        chosenOnce = false
        view.invalidate()
    }

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
        val shownTurn = turnFrom + (turnTarget - turnFrom) * progress
        from = dragTop
        target = dragTop
        turnFrom = shownTurn
        turnTarget = shownTurn
        progress = 1f
        dragTop = Float.NaN
        chosenOnce = false
        view.invalidate()
    }

    private fun changed(top: Float, bottom: Float, left: Float, right: Float, height: Float): Boolean {
        if (top != bandTop || bottom != bandBottom || height != faceHeight || position != chosenFor) return true
        if (feed == null && reportedEdge.toBits() != edgeFor.toBits()) return true
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
        edgeFor = reportedEdge
        inkFor = ink
        cropFor = feed?.placementCrop
        scrollFor = feed?.placementScroll
    }

    /** The share an adaptive clock stands at, scoring the box from [left] to [right], [height] tall, it covers. */
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
                val middle = anchorTop + height / 2
                fun cover(from: Float, to: Float) = subject.coverage(
                    toPhotoX(placed, left, photo.width),
                    toPhotoY(placed, from, photo.height),
                    toPhotoX(placed, right, photo.width),
                    toPhotoY(placed, to, photo.height),
                )
                covered[i] = AdaptivePlacement.weigh(cover(anchorTop, middle), cover(middle, anchorTop + height))
            }
            AdaptivePlacement.choose(covered, adaptive)
        }
        if (chosen != adaptive) {
            adaptive = chosen
            onAdaptiveChosen(ClockPosition.ANCHORS[chosen])
        }
        return ClockPosition.ANCHORS[chosen]
    }

    /**
     * Where the subject first rises into the columns from [left] to [right] between the band's [top] and
     * [bottom], in window pixels ([SubjectInk.topEdge]); where the photo cannot be read, as in the
     * picker's preview, where SystemUI last found it ([reportedEdge]).
     */
    private fun findEdge(top: Float, bottom: Float, left: Float, right: Float): Float {
        val feed = feed ?: return reportedEdge
        val subject = ink ?: return Float.NaN
        val photo = cutout ?: return Float.NaN
        val metrics = view.resources.displayMetrics
        val placed = feed.place(
            WallpaperPlacement.Size(metrics.widthPixels, metrics.heightPixels),
            WallpaperPlacement.Size(photo.width, photo.height),
            zoomed = false,
        )
        val edge = subject.topEdge(
            toPhotoX(placed, left, photo.width),
            toPhotoX(placed, right, photo.width),
            toPhotoY(placed, top, photo.height),
            toPhotoY(placed, bottom, photo.height),
            AdaptiveSize.EDGE_SHARE,
        )
        return if (edge.isNaN()) edge else placed.toScreenY(edge, photo.height)
    }

    /** Glides from where the face stands now and how it is turned to [top] and [turn], or goes there at once. */
    private fun moveTo(top: Float, turn: Float, glide: Boolean) {
        if (placed && top == target && turn == turnTarget) return
        val shown = from + (target - from) * progress
        val shownTurn = turnFrom + (turnTarget - turnFrom) * progress
        move?.cancel()
        move = null
        from = shown
        target = top
        turnFrom = shownTurn
        turnTarget = turn
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
