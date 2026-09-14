package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The subject of the lock screen photo, drawn in front of one large clock face.
 *
 * It is the whole wallpaper surface with everything but the subject clear,
 * placed over the screen exactly as the window manager places the wallpaper
 * ([WallpaperPlacement]), and clipped to the clock's time: the time goes behind
 * the subject, and nothing else on the lock screen does. Before each frame it
 * follows the face: shown only while the face is, as faded as it is, and not
 * at all on the always-on display, where there is no photo behind it.
 */
internal class DepthLayer(
    private val face: ClockFaceAdapter,
    private val minScale: Float,
    private val maxScale: Float,
    private val wallpaperDim: () -> Float,
) {

    private val faceView = face.view
    private val image = ImageView(faceView.context).apply {
        id = View.generateViewId()
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private var cutout: Bitmap? = null
    private var crop: WallpaperPlacement.Crop? = null
    private var scrollX = 0f
    private var scrollY = 0.5f
    private var zoom = 1f
    private var enabled = false
    private var attachedTree: ViewTreeObserver? = null
    private val main = Handler(Looper.getMainLooper())
    private val clip = Rect()
    private var scrims: KeyguardScrims? = null
    private var tint = Color.TRANSPARENT

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

    /** The part of the surface cropped for the screen, in the surface's pixels. */
    fun setCrop(crop: WallpaperPlacement.Crop) {
        this.crop = crop
        faceView.invalidate()
    }

    /** Follows the wallpaper's scroll, from 0 to 1 across ([x]) and down ([y]) its spare width and height. */
    fun setScroll(x: Float, y: Float) {
        scrollX = x
        scrollY = y
        faceView.invalidate()
    }

    /** Shows the subject, or not, as [DepthEffectSetting] chooses. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        faceView.invalidate()
    }

    /** Follows the wallpaper's zoom, from 0 zoomed in to 1 zoomed out. */
    fun setZoom(zoom: Float) {
        this.zoom = zoom
        faceView.invalidate()
    }

    /**
     * Erases from [picture], a drawing of the face's time at its own size, what
     * the subject covers of it now, as faded as the subject is; false when no
     * subject is drawn over the time.
     */
    fun eraseSubjectFrom(picture: Bitmap): Boolean {
        val subject = cutout
        if (subject == null || image.parent == null || image.visibility != View.VISIBLE || image.alpha <= 0f) return false
        val time = face.timeView
        val windowToTime = Matrix().also { Matrix().also(time::transformMatrixToGlobal).invert(it) }
        val imageToTime = Matrix().also(image::transformMatrixToGlobal).apply { postConcat(windowToTime) }
        Canvas(picture).apply {
            concat(imageToTime)
            image.clipBounds?.let { clipRect(it) }
            drawBitmap(subject, null, Rect(0, 0, image.width, image.height), ERASE.apply { alpha = (image.alpha * OPAQUE).toInt() })
        }
        return true
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
        if (parent == null || picture == null || !enabled || !coversScreen(parent)) {
            (image.parent as? ViewGroup)?.removeView(image)
            return
        }

        val screen = faceView.resources.displayMetrics
        val placed = WallpaperPlacement.place(
            screenWidth = screen.widthPixels,
            screenHeight = screen.heightPixels,
            surfaceWidth = picture.width,
            surfaceHeight = picture.height,
            crop = crop ?: WallpaperPlacement.Crop(0, 0, picture.width, picture.height),
            scrollX = scrollX,
            scrollY = scrollY,
            zoom = zoom,
            minScale = minScale,
            maxScale = maxScale,
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

        clipToTime(parent, placed)
        val shown = followScrims()

        val alpha = faceView.alpha * (1f - face.dozeFraction) * shown
        val visibility = if (faceView.visibility == View.VISIBLE && alpha > 0f) View.VISIBLE else View.INVISIBLE
        if (image.visibility != visibility) image.visibility = visibility
        if (image.alpha != alpha) image.alpha = alpha
    }

    /** Clips the layer, in its own unscaled pixels, to where the face's time is in [parent]. */
    private fun clipToTime(parent: View, placed: WallpaperPlacement.Placed) {
        val time = ViewBounds.inAncestor(parent, face.timeView)
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
     * Dims the cut-out as the wallpaper's own dimming (battery saver, dark theme)
     * and SystemUI's scrims dim the photo beneath it, and
     * answers how much of the photo is still shown, so the subject goes and
     * comes back with the photo on the way into and out of the always-on display.
     */
    private fun followScrims(): Float {
        val root = faceView.rootView
        val found = scrims?.takeIf { it.root === root } ?: KeyguardScrims(root).also { scrims = it }
        val dim = found.dim(wallpaperDim())
        if (dim != tint) {
            tint = dim
            image.colorFilter = if (dim == Color.TRANSPARENT) null else PorterDuffColorFilter(dim, PorterDuff.Mode.SRC_ATOP)
        }
        return 1f - found.hidden()
    }

    /** Whether [parent] is the lock screen itself, as big as the screen, rather than a scaled-down preview of it. */
    private fun coversScreen(parent: ViewGroup): Boolean {
        val screen = parent.resources.displayMetrics
        return parent.width >= screen.widthPixels * SCREEN_SHARE && parent.height >= screen.heightPixels * SCREEN_SHARE
    }

    private companion object {
        const val SCREEN_SHARE = 0.9f
        const val OPAQUE = 255

        val ERASE = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    }
}
