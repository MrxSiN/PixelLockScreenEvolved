package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

/**
 * Runs [onFrame] on every animation frame while [view] is attached and visible.
 *
 * Android 17's lock screen scene scales and moves what it hosts in graphics
 * layers of its own ([ComposedViewPlacement]), as it wakes from the always-on
 * display and as the clock settles, and those invalidate no view and run no
 * pre-draw listener. Whatever has to line up with a hosted view on screen is
 * therefore checked each frame instead, only for as long as it is shown.
 */
internal class SceneFrames(private val view: View, private val onFrame: () -> Unit) {

    private var running = false

    private val frame = object : Runnable {
        override fun run() {
            if (!view.isAttachedToWindow || view.visibility != View.VISIBLE) {
                running = false
                return
            }
            onFrame()
            view.postOnAnimation(this)
        }
    }

    /** Starts following, unless it already is; it stops by itself once [view] is no longer shown. */
    fun start() {
        if (running) return
        running = true
        view.postOnAnimation(frame)
    }
}
