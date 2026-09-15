package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * Keeps one [OverlayWindow] added, empty, while the lock screen is up, so an
 * unlock that needs it does not wait for a new window: adding one took about
 * 7ms of the unlock's first frame, and it first drew some 46ms later.
 */
internal class OverlayWindowStandby(
    private val title: String,
    private val logger: Logger,
) : LockScreenReadiness.Listener {

    private var ready: OverlayWindow? = null

    override fun onLockScreenSettled(face: ClockFaceAdapter) {
        if (ready != null) return
        ready = OverlayWindow(face.timeView.context, title, logger).takeIf { it.add() }
    }

    override fun onLockScreenLeft() = discard()

    /** The window kept ready, added, or a new one, not yet added; none is kept after. */
    fun take(context: Context): OverlayWindow = ready?.also { ready = null } ?: OverlayWindow(context, title, logger)

    fun discard() {
        ready?.remove()
        ready = null
    }
}
