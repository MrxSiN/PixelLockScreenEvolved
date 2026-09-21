package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewTreeObserver

/**
 * Runs something just before every draw of a view's window, for as long as the
 * view is attached, following it across windows as it is detached and attached
 * again.
 *
 * Whatever places a view against where its parents are laid out has to run
 * here: a layout change listener hears only of the view's own bounds, and a
 * Compose holder moved by its scene leaves those untouched, so a placement made
 * there lagged frames behind and the view jumped.
 */
internal object BeforeEachDraw {

    /** Runs [action] before each draw of [view]'s window. */
    fun follow(view: View, action: () -> Unit) {
        val beforeDraw = ViewTreeObserver.OnPreDrawListener {
            action()
            true
        }
        var observed: ViewTreeObserver? = null
        fun stop() {
            observed?.takeIf { it.isAlive }?.removeOnPreDrawListener(beforeDraw)
            observed = null
        }
        fun listen() {
            stop()
            observed = view.viewTreeObserver.also { it.addOnPreDrawListener(beforeDraw) }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) = listen()

            override fun onViewDetachedFromWindow(detached: View) = stop()
        })
        if (view.isAttachedToWindow) listen()
    }
}
