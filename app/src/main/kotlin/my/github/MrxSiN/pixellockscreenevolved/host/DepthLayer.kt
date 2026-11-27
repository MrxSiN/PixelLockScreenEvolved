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
import android.provider.Settings
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
 * lock screen goes behind it. Where the subject would leave the time hard to
 * read ([TimeOcclusion]: too much of its ink, any one digit, the hour or the
 * minute, weighed by how thin the clock is, its contrast against the wallpaper
 * and the person's accessibility settings), the time stays in front of it
 * instead, as on iOS. Before each frame it follows the face: erased only as far
 * as the face has come in while the clock changes size, and not at all in a
 * preview of another wallpaper, where the photo behind it is not this one. It
 * stays through an unlock: whatever the app coming up behind the keyguard does
 * to the wallpaper shows through it as it shows around it.
 *
 * In a preview of another wallpaper, the subject Wallpaper & style found for
 * that photo stands in front instead ([showPreview]), spread over the whole
 * preview as the picker spreads the photo. A clock that follows the phone's
 * tilt ([DepthTarget.followsMotion]) is shifted a little against the photo
 * ([MotionParallax]), and erased again where it now stands.
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
    private val face: DepthTarget,
    private val feed: LockWallpaperFeed,
    private val overOtherWallpaper: () -> Boolean,
) {

    private val faceView = face.view

    /** The context of the clock the subject stands in front of. */
    val context: android.content.Context get() = faceView.context
    private val eraser = Eraser()

    private var cutout: Bitmap? = null
    private var preview: Bitmap? = null
    private var previewing = false
    private var luma: LumaGrid? = null
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
    private val viewToSubject = Matrix()
    private val timeOnSubject = RectF()

    // The sizes the subject is placed with, kept while they hold, so a draw allocates none ([LockWallpaperFeed.place]).
    private var screenSize = WallpaperPlacement.Size(0, 0)
    private var subjectSize = WallpaperPlacement.Size(0, 0)

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
        BeforeEachDraw.follow(faceView, action = ::place)
    }

    /** Shows [cutout], the subject's matte as big as the wallpaper surface, or nothing; [luma] is its photo's. */
    fun show(cutout: Bitmap?, luma: LumaGrid?) {
        this.cutout = cutout
        this.luma = luma
        faceView.invalidate()
    }

    /** Shows [matte], the subject of a photo Wallpaper & style previews before it is set, over such a preview, or none. */
    fun showPreview(matte: Bitmap?) {
        preview = matte
        faceView.invalidate()
    }

    /** Weighs the time again, as what the answer depends on beyond it has changed ([DepthOcclusion]). */
    fun remeasure() {
        measured.clear()
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
        drawSubject(canvas, face.depthView, ERASE.apply { this.alpha = alpha })
    }

    /** Draws the cut-out, through [paint], where the wallpaper's own pixels are on screen, into [canvas] in [view]'s units. */
    private fun drawSubject(canvas: Canvas, view: View, paint: Paint) {
        if (previewing) {
            val subject = preview ?: return
            // The picker spreads the photo it previews over the whole preview, so its subject is spread the same way.
            val root = faceView.rootView
            subjectToView.setScale(root.width.toFloat() / subject.width, root.height.toFloat() / subject.height)
            subjectToView.postConcat(ViewBounds.fromWindow(view, windowToView))
            canvas.drawBitmap(subject, subjectToView, paint)
            return
        }
        val subject = cutout ?: return
        val screen = faceView.resources.displayMetrics
        if (screenSize.width != screen.widthPixels || screenSize.height != screen.heightPixels) {
            screenSize = WallpaperPlacement.Size(screen.widthPixels, screen.heightPixels)
        }
        if (subjectSize.width != subject.width || subjectSize.height != subject.height) {
            subjectSize = WallpaperPlacement.Size(subject.width, subject.height)
        }
        val placed = feed.place(screenSize, subjectSize)
        subjectToView.setScale(placed.width / subject.width, placed.height / subject.height)
        subjectToView.postTranslate(placed.left, placed.top)
        subjectToView.postScale(placed.scale, placed.scale, placed.pivotX, placed.pivotY)
        subjectToView.postConcat(ViewBounds.fromWindow(view, windowToView))
        canvas.drawBitmap(subject, subjectToView, paint)
    }

    private fun place() {
        if (!enabled) {
            follow(0f)
            detach()
            return
        }
        previewing = overOtherWallpaper()
        val picture = if (previewing) preview else cutout
        if (picture == null || !(previewing || faceView.isInNotificationShade())) {
            follow(0f)
            detach()
            return
        }
        // The clock follows the tilt whichever of it and the subject is in front, but not dozing or in a preview.
        follow(if (face.followsMotion && !previewing && face.dozeFraction == 0f) MotionParallax.offsetFor(faceView) else 0f)

        val time = ViewBounds.inAncestor(faceView, face.depthView, timeInFace, beforeDraw = true)
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
    /** Shifts the clock [shift] pixels across, as the phone's tilt asks ([MotionParallax]); only a clock that follows it is moved. */
    private fun follow(shift: Float) {
        if (face.followsMotion && faceView.translationX != shift) faceView.translationX = shift
    }

    private fun detach() {
        if (!attached) return
        attached = false
        eraser.strength = 0f
        faceView.foreground = null
        if (faceView.layerType != faceLayerType) faceView.setLayerType(faceLayerType, null)
    }

    /**
     * Whether [subject], placed over the face's [time] as it is now, leaves the
     * time readable ([TimeOcclusion]). A measure takes about a
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
        val reading = TimeCoverage.read(face.depthView) { eraseSubject(it, OPAQUE) }
        timeReadable = TimeOcclusion.inFront(reading, conditions(subject), timeReadable)
        return timeReadable
    }

    /** What the answer depends on beyond the time; read just after the subject was erased from it, so [subjectToView] places the time. */
    private fun conditions(subject: Bitmap): TimeOcclusion.Conditions {
        val context = faceView.context
        val resolver = context.contentResolver
        val accessible = runCatching { Settings.Secure.getInt(resolver, HIGH_TEXT_CONTRAST, 0) == 1 }.getOrDefault(false) ||
            context.resources.configuration.fontScale >= LARGE_FONT_SCALE
        return TimeOcclusion.Conditions(DepthOcclusion.get(resolver), contrast(subject), accessible)
    }

    /** The contrast of the clock against the photo under its time, or null where the photo's light is not known. */
    private fun contrast(subject: Bitmap): Float? {
        val grid = luma?.takeUnless { previewing } ?: return null
        val time = face.depthView
        if (!subjectToView.invert(viewToSubject)) return null
        timeOnSubject.set(0f, 0f, time.width.toFloat(), time.height.toFloat())
        viewToSubject.mapRect(timeOnSubject)
        val under = grid.mean(
            timeOnSubject.left / subject.width, timeOnSubject.top / subject.height,
            timeOnSubject.right / subject.width, timeOnSubject.bottom / subject.height,
        ) ?: return null
        val ink = face.inkColor ?: return null
        return TimeOcclusion.contrast(LumaGrid.luminance(ink), under)
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

        /** Settings.Secure's high-contrast text switch, which Android does not make public. */
        const val HIGH_TEXT_CONTRAST = "high_text_contrast_enabled"

        /** A font scale from which a person is taken to want large text. */
        const val LARGE_FONT_SCALE = 1.3f

        val ERASE = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    }
}
