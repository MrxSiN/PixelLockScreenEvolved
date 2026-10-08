package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

import java.util.Collections
import java.util.WeakHashMap

/**
 * Clock position on the lock screen: lets each large face SystemUI builds
 * stand where its clock setting places it ([ClockLayout]), and hands the
 * faces what an adaptive position is chosen from: the subject the depth
 * effect has already cut out of the photo ([onSubject]), and where the photo
 * is placed ([LockWallpaperFeed]), which each face compares before its frames;
 * the depth effect redraws the face whenever the photo moves, which is the
 * only time there is a subject to move away from.
 *
 * Nothing here hooks SystemUI: the faces are this module's own, and SystemUI
 * keeps laying them out, fading them and moving them for burn-in. Without this
 * feature they stand at the top, as they did before there were positions.
 */
internal class KeyguardClockLayout(private val feed: LockWallpaperFeed) {

    private val faces = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockFaceAdapter, Boolean>()))
    private val main = Handler(Looper.getMainLooper())
    private var cutout: Bitmap? = null

    /** Lets [clock]'s large face take its position. */
    fun track(clock: ClockControllerAdapter) {
        val face = clock.largeFace
        main.post {
            face.clockLayout.onAdaptiveChosen = { chosen ->
                if (face.view.isInNotificationShade()) ClockLayoutReport.putAdaptive(face.context.contentResolver, chosen)
            }
            face.clockLayout.showSubject(cutout, feed)
            face.clockLayout.enabled = true
            faces.add(face)
        }
    }

    /** The depth effect's cut-out of the lock screen photo, or none. On the main thread. */
    fun onSubject(cutout: Bitmap?, @Suppress("UNUSED_PARAMETER") context: Context) {
        this.cutout = cutout
        each { it.clockLayout.showSubject(cutout, feed) }
    }

    private fun each(action: (ClockFaceAdapter) -> Unit) = synchronized(faces) { faces.toList() }.forEach(action)
}
