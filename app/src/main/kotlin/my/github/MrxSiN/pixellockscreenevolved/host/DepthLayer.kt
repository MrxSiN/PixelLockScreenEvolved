package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The subject of the lock screen photo, drawn in front of one large clock face.
 *
 * It is the whole wallpaper surface with everything but the subject clear,
 * placed over the screen exactly as the window manager places the wallpaper
 * ([LockWallpaperFeed]), and clipped to the clock's time: the time goes behind
 * the subject, and nothing else on the lock screen does. Where the subject
 * would hide more than half of the time ([TimeCoverage]), the time stays in
 * front of it instead, so it can still be read, as on iOS. Before each frame it
 * follows the face: shown only while the face is, as faded as it is (and as
 * far as it has come in while the clock changes size), darkened as the photo
 * is, and not at all on the always-on display, where there is no photo behind it, or in a
 * preview of another wallpaper, where the photo behind it is not this one.
 */
internal class DepthLayer(
    private val face: ClockFaceAdapter,
    private val feed: LockWallpaperFeed,
    private val overOtherWallpaper: () -> Boolean,
    /** Whether the always-on display shows a wallpaper of this module's ([KeyguardAodWallpaper]) rather than black. */
    private val aodShowsWallpaper: () -> Boolean,
) {

    private val faceView = face.view
    private val image = SubjectView(faceView.context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var cutout: Bitmap? = null
    private var enabled = false
    private var attachedTree: ViewTreeObserver? = null
    private val main = Handler(Looper.getMainLooper())
    private val clip = Rect()
    private var scrims: KeyguardScrims? = null
    private var tint = Color.TRANSPARENT
    private var revealing = false
    private var measured: Measured? = null
    private var unsettled: Measured? = null
    private var timeReadable = true

    /** What the share of the time the subject hides depends on. */
    private data class Measured(
        val cutout: Bitmap,
        val placement: Any,
        val time: RectF,
        val timeChanges: Int,
        val dozeFraction: Float,
    )

    private val follow = ViewTreeObserver.OnPreDrawListener {
        place()
        true
    }

    init {
        faceView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = listen()
            override fun onViewDetachedFromWindow(view: View) = stopListening()
        })
        if (faceView.isAttachedToWindow) listen()
    }

    /** Shows [cutout], as big as the wallpaper surface, or nothing. */
    fun show(cutout: Bitmap?) {
        this.cutout = cutout
        image.setImageBitmap(cutout)
        faceView.invalidate()
    }

    /** Places the layer again, as the wallpaper's placement has changed ([LockWallpaperFeed]). */
    fun invalidate() {
        faceView.invalidate()
    }

    /** Shows the subject, or not, as [DepthEffectSetting] chooses. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        faceView.invalidate()
    }

    /**
     * Erases from [picture], a drawing of the face's time at its own size, what
     * the subject covers of it now, as faded as the subject is; false when no
     * subject is drawn over the time.
     */
    fun eraseSubjectFrom(picture: Bitmap): Boolean {
        if (!drawsOverTime()) return false
        eraseSubject(Canvas(picture), (image.alpha * OPAQUE).toInt())
        return true
    }

    /**
     * What [eraseSubjectFrom] erases at rest depends on: the cut-out, how the layer is
     * placed and faded, and where the time is laid out in the layer's parent; null when
     * it would erase nothing. Moves SystemUI makes to the clock itself are left out, as
     * a swipe to unlock lifts it against the photo, so a drawing made at rest still
     * stands for the time as that swipe begins.
     */
    fun occlusionState(): Any? {
        if (!drawsOverTime()) return null
        val parent = image.parent as? View ?: return null
        val placed = FloatArray(MATRIX_VALUES).also(image.matrix::getValues).map { (it * HUNDREDTHS).roundToInt() }
        return listOf(cutout, placed, image.left, image.top, image.layoutParams.width, image.layoutParams.height, image.alpha, laidOutOffset(parent, face.timeView))
    }

    /** Where [view] is laid out in [ancestor], before any view's moves or scales. */
    private fun laidOutOffset(ancestor: View, view: View): Pair<Int, Int> {
        var x = 0
        var y = 0
        var at: View? = view
        while (at != null && at !== ancestor) {
            x += at.left - at.scrollX
            y += at.top - at.scrollY
            at = at.parent as? View
        }
        return x to y
    }

    private fun drawsOverTime(): Boolean =
        cutout != null && image.parent != null && image.visibility == View.VISIBLE && image.alpha > 0f

    /** The layer's pixels in the face's time's own pixels. */
    private fun imageToTime(): Matrix {
        val windowToTime = Matrix().also { Matrix().also(face.timeView::transformMatrixToGlobal).invert(it) }
        return Matrix().also(image::transformMatrixToGlobal).apply { postConcat(windowToTime) }
    }

    /**
     * Erases the subject, as opaque as [alpha] makes it, from [canvas], whose units
     * are the face's time's own pixels. The layer's size is read from its layout
     * params, which [place] sets before the layer is first laid out.
     */
    private fun eraseSubject(canvas: Canvas, alpha: Int) {
        val subject = cutout ?: return
        canvas.save()
        canvas.concat(imageToTime())
        image.clipBounds?.let(canvas::clipRect)
        val size = image.layoutParams
        canvas.drawBitmap(subject, null, Rect(0, 0, size.width, size.height), ERASE.apply { this.alpha = alpha })
        canvas.restore()
    }

    private fun listen() {
        stopListening()
        attachedTree = faceView.viewTreeObserver.also { it.addOnPreDrawListener(follow) }
    }

    /**
     * Stops following the face. The face's parent is still walking its
     * children to detach them, so taking the layer out now would pull a view
     * from under that walk and crash SystemUI; it is taken out once the walk
     * is done.
     */
    private fun stopListening() {
        attachedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(follow)
        attachedTree = null
        main.post { if (attachedTree == null) (image.parent as? ViewGroup)?.removeView(image) }
    }

    private fun place() {
        val parent = faceView.parent as? ViewGroup
        val picture = cutout
        if (parent == null || picture == null || !enabled || !coversScreen(parent) || overOtherWallpaper()) {
            (image.parent as? ViewGroup)?.removeView(image)
            return
        }

        val screen = faceView.resources.displayMetrics
        val placed = feed.place(
            screen = WallpaperPlacement.Size(screen.widthPixels, screen.heightPixels),
            surface = WallpaperPlacement.Size(picture.width, picture.height),
        )
        val width = placed.width.toInt()
        val height = placed.height.toInt()

        if (image.parent !== parent || parent.indexOfChild(image) != parent.indexOfChild(faceView) + 1) {
            (image.parent as? ViewGroup)?.removeView(image)
            parent.addView(image, parent.indexOfChild(faceView) + 1, ViewGroup.LayoutParams(width, height))
        } else if (image.layoutParams.width != width || image.layoutParams.height != height) {
            image.layoutParams = image.layoutParams.apply {
                this.width = width
                this.height = height
            }
        }

        // The parent is as big as the screen, so its own pixels are the screen's: on the lock
        // screen it fills the screen, and in the picker's preview SystemUI scales a parent of
        // it down to the preview card, taking the layer and the time along.
        image.translationX = placed.left
        image.translationY = placed.top
        image.pivotX = placed.pivotX - placed.left
        image.pivotY = placed.pivotY - placed.top
        image.scaleX = placed.scale
        image.scaleY = placed.scale

        val time = ViewBounds.inAncestor(parent, face.timeView)
        clipToTime(time, placed)
        followScrims()

        val faded = faceView.alpha * face.arrival * (1f - face.dozeFraction)
        val alpha = if (faded > 0f && leavesTimeReadable(picture, time)) faded else 0f
        val visibility = if (faceView.visibility == View.VISIBLE && alpha > 0f) View.VISIBLE else View.INVISIBLE
        if (image.visibility != visibility) image.visibility = visibility
        if (image.alpha != alpha) image.alpha = alpha
    }

    /** Clips the layer, in its own unscaled pixels, to [time], where the face's time is in the layer's parent. */
    private fun clipToTime(time: RectF, placed: WallpaperPlacement.Placed) {
        fun localX(parentX: Float) = placed.pivotX - placed.left + (parentX - placed.pivotX) / placed.scale
        fun localY(parentY: Float) = placed.pivotY - placed.top + (parentY - placed.pivotY) / placed.scale
        clip.set(
            floor(localX(time.left)).toInt(),
            floor(localY(time.top)).toInt(),
            ceil(localX(time.right)).toInt(),
            ceil(localY(time.bottom)).toInt(),
        )
        if (image.clipBounds != clip) image.clipBounds = Rect(clip)
    }

    /**
     * Whether [subject], placed over the face's [time] as the layer is now, hides
     * no more than [MOST_HIDDEN] of the time's ink. A measure takes about a
     * millisecond, so while the time or the subject moves, as the screen wakes,
     * the last answer holds, and the share is measured again once they have held
     * still for a frame; only the first answer is measured at once. The
     * wallpaper's zoom is left out: it only breathes as the lock screen comes and
     * goes, and the time should not jump in front of the subject or behind it midway.
     */
    private fun leavesTimeReadable(subject: Bitmap, time: RectF): Boolean {
        // Digits rolling to a new minute are not yet the time to measure; the last answer holds until they land.
        if (face.timeChanging) {
            faceView.postInvalidateOnAnimation()
            return timeReadable
        }
        val now = Measured(subject, feed.placementKey, time, face.timeChanges, face.dozeFraction)
        if (now == measured) return timeReadable
        if (measured != null && now != unsettled) {
            unsettled = now
            faceView.postInvalidateOnAnimation()
            return timeReadable
        }
        measured = now
        unsettled = null
        timeReadable = TimeCoverage.of(face.timeView) { eraseSubject(it, OPAQUE) } <= MOST_HIDDEN
        return timeReadable
    }

    /**
     * Dims the cut-out as the wallpaper's own dimming (battery saver, dark theme)
     * and SystemUI's scrims dim the photo beneath it, and darkens it with the
     * light reveal scrim while that hides the photo ([SubjectView]), so the
     * subject goes and comes back with the photo on the way into and out of the
     * always-on display, pixel for pixel, and never lets the time show through.
     */
    private fun followScrims() {
        val root = faceView.rootView
        val found = scrims?.takeIf { it.root === root } ?: KeyguardScrims(root).also { scrims = it }
        val dim = found.dim(feed.dim())
        if (dim != tint) {
            tint = dim
            image.colorFilter = if (dim == Color.TRANSPARENT) null else PorterDuffColorFilter(dim, PorterDuff.Mode.SRC_ATOP)
        }
        // The scrim changes every frame while it reveals, and once more as it finishes. Only then is the
        // cut-out drawn in a layer of its own, which the darkening needs; the layer is as big as the photo.
        // Over the always-on wallpaper the scrim's black is hidden, so the subject only fades, with the doze.
        val nowRevealing = found.isRevealing() && !aodShowsWallpaper()
        if (nowRevealing != revealing) image.setLayerType(if (nowRevealing) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE, null)
        if (nowRevealing || revealing) image.invalidate()
        revealing = nowRevealing
    }

    /** The cut-out, with the light reveal scrim laid over it while that hides the photo beneath. */
    private inner class SubjectView(context: Context) : ImageView(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (revealing) scrims?.darkenAsRevealed(canvas, this)
        }
    }

    /** Whether [parent] is the lock screen itself, as big as the screen, rather than a scaled-down preview of it. */
    private fun coversScreen(parent: ViewGroup): Boolean {
        val screen = parent.resources.displayMetrics
        return parent.width >= screen.widthPixels * SCREEN_SHARE && parent.height >= screen.heightPixels * SCREEN_SHARE
    }

    private companion object {
        const val SCREEN_SHARE = 0.9f
        const val OPAQUE = 255
        const val MATRIX_VALUES = 9
        const val HUNDREDTHS = 100f

        /** The most of the time's ink the subject may hide and still stand in front of it. */
        const val MOST_HIDDEN = 0.5f

        val ERASE = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    }
}
