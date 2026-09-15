package my.github.MrxSiN.pixellockscreenevolved.host

import android.widget.TextView

/**
 * Keeps a flight of the lock screen time to the status bar clock ready while
 * the lock screen is up, so an unlock can start it at once ([take]).
 *
 * Starting one from nothing drew its pictures (about 13ms) and added its window
 * (about 7ms, then some 46ms until that window first drew) in the first frames
 * of the unlock, when SystemUI is busiest. So once the lock screen has settled
 * ([LockScreenReadiness]), the window is added, empty ([OverlayWindowStandby]),
 * and the pictures are drawn and sent to the GPU ([TimePictures],
 * [LandingPicture]); each time the lock screen settles again, whatever no longer
 * matches (a new minute, a new size, the depth effect's subject) is drawn again.
 * It is all let go as the display dozes or the clock leaves the lock screen.
 */
internal class ClockFlightStandby(
    private val lockScreen: LockScreenReadiness,
    private val windows: OverlayWindowStandby,
    private val statusBarClocks: StatusBarClocks,
    private val occlusion: TimeOcclusion,
) : LockScreenReadiness.Listener {

    private var time: TimePictures? = null
    private var landing: LandingPicture? = null

    override fun onLockScreenSettled(face: ClockFaceAdapter) {
        val target = statusBarClocks.find(face.timeView) ?: return
        windows.onLockScreenSettled(face)
        if (!draw(face, target)) discard()
    }

    override fun onLockScreenLeft() = discard()

    /**
     * The flight for the time now on the lock screen, with whatever was ready and
     * still matches, and the rest drawn now; null if there is no time or no status
     * bar clock to fly to. What was ready is handed over, and none is kept.
     */
    fun take(): ClockFlightOverlay? {
        val face = lockScreen.shownFace()
        val target = face?.let { statusBarClocks.find(it.timeView) }
        if (face == null || target == null || !draw(face, target)) {
            discard()
            return null
        }
        val overlay = ClockFlightOverlay(
            face.timeView, target, requireNotNull(time), requireNotNull(landing), windows.take(face.timeView.context),
        )
        time = null
        landing = null
        return overlay
    }

    /** Lets go of whatever is ready. */
    fun discard() {
        windows.discard()
        time?.recycle()
        landing?.recycle()
        time = null
        landing = null
    }

    /** Draws again whatever of the pictures no longer matches [face] and [target]; false if the time cannot be drawn. */
    private fun draw(face: ClockFaceAdapter, target: TextView): Boolean {
        val timeView = face.timeView
        if (time?.match(timeView, face.timeChanges, occlusion) != true) {
            time?.recycle()
            time = TimePictures.of(timeView, face.timeChanges, occlusion)?.also(TimePictures::prepareToDraw)
            if (time == null) return false
        }
        if (landing?.match(target) != true) {
            landing?.recycle()
            landing = LandingPicture.of(target).also(LandingPicture::prepareToDraw)
        }
        return true
    }
}
