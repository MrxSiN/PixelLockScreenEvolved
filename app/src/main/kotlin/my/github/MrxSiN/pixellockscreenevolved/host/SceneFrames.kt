package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

/**
 * Runs [onFrame] once on the animation frame after each [start], while [view]
 * is attached and visible.
 *
 * Android 17's lock screen scene scales and moves what it hosts in graphics
 * layers of its own ([ComposedViewPlacement]), as it wakes from the always-on
 * display and as the clock settles, and lays its holders out while drawing,
 * after the pre-draw listeners have run. So whatever has to line up with a
 * hosted view on screen is checked again on the frame after the window draws:
 * callers start this before each draw ([BeforeEachDraw]). Nothing on screen
 * moves without the window drawing, so once a check finds nothing to redraw,
 * no frame is asked for until the window draws again. An earlier version asked
 * for every frame for as long as the view was shown, which kept SystemUI's
 * main thread waking at the display's rate on an unchanging lock screen and
 * all through the always-on display.
 */
internal class SceneFrames(private val view: View, private val onFrame: () -> Unit) {

    private var pending = false

    private val frame = Runnable {
        pending = false
        if (view.isAttachedToWindow && view.visibility == View.VISIBLE) onFrame()
    }

    /** Asks for one check on the next frame, unless one is already asked for. */
    fun start() {
        if (pending) return
        pending = true
        view.postOnAnimation(frame)
    }
}
