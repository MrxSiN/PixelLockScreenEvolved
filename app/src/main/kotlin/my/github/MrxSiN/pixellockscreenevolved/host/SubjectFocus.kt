package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectAwareClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectFrame

/**
 * Tells a face that lays itself out around the photo's subject where that
 * subject stands ([SubjectAwareClockFace]).
 *
 * The subject's box is read from the cut-out the depth effect made
 * ([SubjectBounds]) and put on screen exactly as the window manager puts the
 * wallpaper ([LockWallpaperFeed]), then measured from the top of the large
 * clock as the lock screen pins it ([LockScreenInsets.largeClockTop]). The
 * face's own measured size is never part of that sum: it changes because of
 * what it is told here, and a frame that followed it would chase itself.
 *
 * The wallpaper's zoom is left out for the same reason the depth effect leaves
 * it out: it only breathes as the lock screen comes and goes, and the time
 * should not grow and shrink with that breath.
 *
 * A face hears of the subject only while the depth effect is on and has one; it
 * is told of none otherwise, and lays itself out around the middle of the
 * screen instead.
 */
internal class SubjectFocus(
    adapter: ClockFaceAdapter,
    private val feed: LockWallpaperFeed,
) {

    private val face = adapter.viewFace as? SubjectAwareClockFace
    private val context: Context = adapter.context

    private var cutout: Bitmap? = null
    private var bounds: RectF? = null
    private var enabled = false
    private var shown: SubjectFrame? = null
    private var told = false

    /** Shows the subject of [cutout], as big as the wallpaper surface, or none. */
    fun show(cutout: Bitmap?) {
        if (this.cutout === cutout) return
        this.cutout = cutout
        bounds = cutout?.let(SubjectBounds::of)
        tell()
    }

    /** Lays the face out around the subject, or not, as [DepthEffectSetting] chooses. */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        tell()
    }

    /** The subject stands somewhere else on screen, as the wallpaper's placement has changed. */
    fun invalidate() = tell()

    private fun tell() {
        val face = face ?: return
        val frame = frame()
        if (told && frame == shown) return
        told = true
        shown = frame
        face.setSubject(frame)
    }

    /** The subject's box as the face is told of it, or null while there is none to place. */
    private fun frame(): SubjectFrame? {
        val subject = cutout?.takeIf { enabled } ?: return null
        val box = bounds ?: return null
        val screen = context.resources.displayMetrics
        val placed = feed.place(
            WallpaperPlacement.Size(screen.widthPixels, screen.heightPixels),
            WallpaperPlacement.Size(subject.width, subject.height),
            zoomed = false,
        )
        val clockTop = LockScreenInsets.largeClockTop(context)
        return SubjectFrame(
            left = placed.toScreenX(box.left, subject.width),
            top = placed.toScreenY(box.top, subject.height) - clockTop,
            right = placed.toScreenX(box.right, subject.width),
            bottom = placed.toScreenY(box.bottom, subject.height) - clockTop,
        )
    }
}
