package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * A full-screen window of SystemUI's own, above the keyguard and the status bar,
 * that touches pass through: somewhere to hold pictures of the lock screen while
 * the keyguard window fades away far sooner than they should go.
 */
internal class OverlayWindow(
    context: Context,
    private val title: String,
    private val logger: Logger,
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)

    /** Where the pictures go, laid out over the whole screen. */
    val root = FrameLayout(context)

    var isAdded = false
        private set

    /** Adds the window; false if it could not be. */
    fun add(): Boolean {
        try {
            windowManager.addView(root, params())
            isAdded = true
        } catch (error: RuntimeException) {
            logger.warn("$title window could not be added", error)
        }
        return isAdded
    }

    /**
     * Runs [action] on the frame after the window has first drawn, when its
     * pictures have reached the screen, so whatever they stand for can be hidden
     * in the same frame they are shown.
     */
    fun onFirstFrame(action: () -> Unit) {
        root.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var drawn = false

            override fun onDraw() {
                if (drawn) return
                drawn = true
                Choreographer.getInstance().postFrameCallback {
                    // A draw listener cannot be removed while the tree is drawing.
                    if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnDrawListener(this)
                    if (isAdded) action()
                }
            }
        })
    }

    fun remove() {
        if (!isAdded) return
        isAdded = false
        runCatching { windowManager.removeViewImmediate(root) }
            .onFailure { logger.warn("$title window could not be removed", it) }
    }

    private fun params() = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
        TYPE_SECURE_SYSTEM_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = WINDOW_TITLE_PREFIX + this@OverlayWindow.title
        fitInsetsTypes = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    private companion object {
        const val WINDOW_TITLE_PREFIX = "PixelLockScreenEvolved"

        /** `WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY`, hidden from apps; above the status bar. */
        const val TYPE_SECURE_SYSTEM_OVERLAY = 2015
    }
}
