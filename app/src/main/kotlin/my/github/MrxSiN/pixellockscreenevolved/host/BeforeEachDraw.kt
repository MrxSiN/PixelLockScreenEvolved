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
 *
 * A view SystemUI keeps, such as the smartspace card, outlives a hot reload of
 * this module, and so would the listeners the replaced code left on it, still
 * placing it by that code's state: the card stood back above the clock every
 * frame. Once the code is replaced ([retire]) its listeners take themselves off
 * as they next run, and do nothing.
 */
internal object BeforeEachDraw {

    /** Whether this copy of the module's code has been replaced by a hot reload; its setting observers stop too ([SecureIntSetting]). */
    @Volatile
    var retired = false
        private set

    /** Stops every action this copy of the code runs on a view; called as a hot reload replaces it. */
    fun retire() {
        retired = true
    }

    /**
     * Runs [action] before each draw of [view]'s window. Once retired, [onRetired] runs once instead, before the
     * view's next draw or as it is next attached, to undo what this copy left on the view: the new code cannot
     * know of it, and the view may be detached as the code is replaced.
     */
    fun follow(view: View, onRetired: () -> Unit = {}, action: () -> Unit) {
        var observed: ViewTreeObserver? = null
        var handedOver = false
        fun handOver() {
            if (handedOver) return
            handedOver = true
            onRetired()
        }
        val beforeDraw = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (retired) {
                    view.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
                    handOver()
                    return true
                }
                action()
                return true
            }
        }
        fun stop() {
            observed?.takeIf { it.isAlive }?.removeOnPreDrawListener(beforeDraw)
            observed = null
        }
        fun listen() {
            stop()
            if (retired) return handOver()
            observed = view.viewTreeObserver.also { it.addOnPreDrawListener(beforeDraw) }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) = listen()

            override fun onViewDetachedFromWindow(detached: View) = stop()
        })
        if (view.isAttachedToWindow) listen()
    }
}
