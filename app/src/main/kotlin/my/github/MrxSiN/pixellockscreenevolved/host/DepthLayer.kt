package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * The subject of the lock screen photo, standing in front of one large clock face.
 *
 * The subject is not drawn: its shape is erased from the face's time, so the
 * photo itself shows through where the subject stands. The cut-out is placed
 * over the screen exactly as the window manager places the wallpaper
 * ([LockWallpaperFeed]) and erased from the time alone, so nothing else on the
 * lock screen goes behind it. Where the subject would hide more than half of the
 * time ([TimeCoverage]), the time stays in front of it instead, so it can still
 * be read, as on iOS. Before each frame it follows the face: erased only as far
 * as the face has come in while the clock changes size, and not at all in a
 * preview of another wallpaper, where the photo behind it is not this one. It
 * stays through an unlock: whatever the app coming up behind the keyguard does
 * to the wallpaper shows through it as it shows around it.
 *
 * While it erases, the face is drawn as one layer, the erasing included
 * ([Eraser]). An earlier version laid a copy of the subject over the time
 * instead, and had to match everything laid over the photo beneath it; and
 * Android 17's lock screen scene fades the clock, as the display wakes or a
 * screen saver ends, by fading each thing it draws rather than the picture they
 * make together, so the half-faded copy let the digits show through, and the
 * time seemed to come in over the subject and then go behind it. Erased from
 * one layer, whatever fades, dims or darkens the photo and the face does so to
 * the subject too.
 */
internal class DepthLayer(
    private val face: ClockFaceAdapter,
    private val feed: LockWallpaperFeed,
    private val overOtherWallpaper: () -> Boolean,
) {

    private val faceView = face.view
    private val eraser = Eraser()

    private var cutout: Bitmap? = null
    private var enabled = false
    private var attached = false
    private var faceLayerType = View.LAYER_TYPE_NONE
    private val measured = Measured()
    private val unsettled = Measured()
    private var timeReadable = true
    private val inFrontSpring = SpringFollower(IN_FRONT_STIFFNESS, initial = 1f)
    private var easedAt = 0L

    // Scratch for the work done before each frame, on the main thread.
    private val faceToWindow = Matrix()
    private val timeInFace = RectF()
    private val subjectToView = Matrix()
    private val windowToView = Matrix()

    /** What the share of the time the subject hides depends on, as last taken; kept in place, since it is compared before each frame. */
    private class Measured {
        private var taken = false
        private var cutout: Bitmap? = null
        private var crop: Any? = null
        private var scroll: Any? = null
        private val time = RectF()
        private var timeChanges = 0

        fun isEmpty(): Boolean = !taken

        fun matches(cutout: Bitmap, crop: Any?, scroll: Any?, time: RectF, timeChanges: Int): Boolean =
            taken && cutout == this.cutout && crop == this.crop && scroll == this.scroll && time == this.time && timeChanges == this.timeChanges

        fun take(cutout: Bitmap, crop: Any?, scroll: Any?, time: RectF, timeChanges: Int) {
            taken = true
            this.cutout = cutout
            this.crop = crop
            this.scroll = scroll
            this.time.set(time)
            this.timeChanges = timeChanges
        }

        fun clear() {
            taken = false
            cutout = null
            crop = null
            scroll = null
        }
    }

    /**
     * Erases the subject again on each frame the scene has moved the face on screen since it
     * last did, so it never slides off the photo ([SceneFrames]); the face's layer is otherwise
     * kept as it was drawn.
     */
    private val sceneFrames = SceneFrames(faceView) {
        if (!eraser.erased || ViewBounds.toWindow(faceView, faceToWindow) != eraser.erasedAt) faceView.invalidate()
    }

    init {
        BeforeEachDraw.follow(faceView, ::place)
    }

    /** Shows [cutout], as big as the wallpaper surface, or nothing. */
    fun show(cutout: Bitmap?) {
        this.cutout = cutout
        faceView.invalidate()
    }

    /** Places the subject again, as the wallpaper's placement has changed ([LockWallpaperFeed]). */
    fun invalidate() {
        faceView.invalidate()
    }

    /** Shows the subject, or not, as [DepthEffectSetting] chooses. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        faceView.invalidate()
    }

    /** Erases the subject, as opaque as [alpha] makes it, from [canvas], whose units are the face's time's own pixels. */
    private fun eraseSubject(canvas: Canvas, alpha: Int) {
        drawSubject(canvas, face.timeView, ERASE.apply { this.alpha = alpha })
    }

    /** Draws the cut-out, through [paint], where the wallpaper's own pixels are on screen, into [canvas] in [view]'s units. */
    private fun drawSubject(canvas: Canvas, view: View, paint: Paint) {
        val subject = cutout ?: return
        val screen = faceView.resources.displayMetrics
        val placed = feed.place(
            WallpaperPlacement.Size(screen.widthPixels, screen.heightPixels),
            WallpaperPlacement.Size(subject.width, subject.height),
        )
        subjectToView.setScale(placed.width / subject.width, placed.height / subject.height)
        subjectToView.postTranslate(placed.left, placed.top)
        subjectToView.postScale(placed.scale, placed.scale, placed.pivotX, placed.pivotY)
        subjectToView.postConcat(ViewBounds.fromWindow(view, windowToView))
        canvas.drawBitmap(subject, subjectToView, paint)
    }

    private fun place() {
        val picture = cutout
        if (picture == null || !enabled || !faceView.isInNotificationShade() || overOtherWallpaper()) {
            detach()
            return
        }

        val time = ViewBounds.inAncestor(faceView, face.timeView, timeInFace)
        // None on the always-on display, where the time is drawn in outline and nothing goes over it. The
        // time comes out in front of the subject before SystemUI moves it to its AOD region, and goes
        // back behind it only once it has moved back ([AodClockMotion]).
        val shown = face.arrival * AodClockMotion.subjectInFront(face.dozeFraction)
        val readable = if (shown > 0f) leavesTimeReadable(picture, time) else timeReadable
        val strength = shown * inFront(readable)
        if (strength <= 0f) {
            detach()
            return
        }
        attach()
        if (eraser.strength != strength || eraser.time != time) {
            eraser.strength = strength
            eraser.time.set(time)
            faceView.invalidate()
        }
        sceneFrames.start()
    }

    /** Draws the face as one layer, erased where the subject stands. */
    private fun attach() {
        if (attached) return
        attached = true
        faceLayerType = faceView.layerType
        faceView.foreground = eraser
        if (faceLayerType != View.LAYER_TYPE_HARDWARE) faceView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    /** Lets the face draw as it did before. */
    private fun detach() {
        if (!attached) return
        attached = false
        eraser.strength = 0f
        faceView.foreground = null
        if (faceView.layerType != faceLayerType) faceView.setLayerType(faceLayerType, null)
    }

    /**
     * Whether [subject], placed over the face's [time] as it is now, hides no
     * more than [MOST_HIDDEN] of the time's ink. A measure takes about a
     * millisecond, so while the time or the subject moves, as the screen wakes,
     * the last answer holds, and the share is measured again once they have held
     * still for a frame; only the first answer is measured at once. The
     * wallpaper's zoom is left out: it only breathes as the lock screen comes and
     * goes, and the time should not jump in front of the subject or behind it midway.
     * Nor is the time measured on its way into or out of the always-on display: it
     * is drawn in outline there, whose thin ink the subject hides a different share
     * of, and the subject popped in front of it as the display finished waking.
     */
    private fun leavesTimeReadable(subject: Bitmap, time: RectF): Boolean {
        // Digits rolling to a new minute are not yet the time to measure; the last answer holds until they land.
        if (face.timeChanging) {
            faceView.postInvalidateOnAnimation()
            return timeReadable
        }
        if (face.dozeFraction > 0f) return timeReadable
        val crop = feed.placementCrop
        val scroll = feed.placementScroll
        val timeChanges = face.timeChanges
        if (measured.matches(subject, crop, scroll, time, timeChanges)) return timeReadable
        if (!measured.isEmpty() && !unsettled.matches(subject, crop, scroll, time, timeChanges)) {
            unsettled.take(subject, crop, scroll, time, timeChanges)
            faceView.postInvalidateOnAnimation()
            return timeReadable
        }
        measured.take(subject, crop, scroll, time, timeChanges)
        unsettled.clear()
        timeReadable = TimeCoverage.of(face.timeView) { eraseSubject(it, OPAQUE) } <= MOST_HIDDEN
        return timeReadable
    }

    /**
     * How far the subject stands in front of the time, from 0 to 1, easing toward all the
     * way when [readable] and none otherwise, so the time goes behind the subject or comes
     * out in front of it smoothly as the answer changes, rather than in one frame.
     */
    private fun inFront(readable: Boolean): Float {
        val now = SystemClock.uptimeMillis()
        val target = if (readable) 1f else 0f
        val elapsed = (now - easedAt) / MS_PER_SECOND
        easedAt = now
        // After a pause, as when the subject was not shown at all, it starts where it belongs; with animations
        // off it is always where it belongs, and otherwise it keeps to the system's animator duration scale.
        val shown = if (elapsed > LONGEST_EASING_STEP_SECONDS || SystemMotion.isReduced) inFrontSpring.also { it.jumpTo(target) }.value.toFloat()
        else inFrontSpring.follow(target, SystemMotion.scaled(elapsed))
        if (abs(shown - target) > SETTLED) faceView.postInvalidateOnAnimation()
        return shown.coerceIn(0f, 1f)
    }

    /**
     * The face's foreground while the subject stands in front of it: the subject's
     * shape, erased from the face's layer within the time's bounds, as far as
     * [strength] says.
     */
    private inner class Eraser : Drawable() {
        var strength = 0f
        val time = RectF()

        /** Whether the subject has been erased at all, so [erasedAt] holds a place. */
        var erased = false
            private set

        /** Where the face was on screen when the subject was last erased from it. */
        val erasedAt = Matrix()

        override fun draw(canvas: Canvas) {
            ViewBounds.toWindow(faceView, erasedAt)
            erased = true
            if (strength <= 0f) return
            val saved = canvas.save()
            canvas.clipRect(time)
            drawSubject(canvas, faceView, ERASE.apply { alpha = (strength * OPAQUE).toInt() })
            canvas.restoreToCount(saved)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val OPAQUE = 255

        /** How firmly the subject eases in front of the time or out from it: settled in about a third of a second. */
        const val IN_FRONT_STIFFNESS = 256f
        const val LONGEST_EASING_STEP_SECONDS = 0.1f
        const val SETTLED = 0.002f
        const val MS_PER_SECOND = 1000f

        /** The most of the time's ink the subject may hide and still stand in front of it. */
        const val MOST_HIDDEN = 0.5f

        val ERASE = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    }
}
