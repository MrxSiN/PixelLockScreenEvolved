package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.widget.TextView

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Every SystemUI clock text view as it is attached and detached, to find the
 * status bar's among them: the one laid out outside the window the lock screen
 * is in, highest on the screen.
 */
internal class StatusBarClocks(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val clocks = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<TextView, Boolean>()))

    override fun install(classLoader: ClassLoader) {
        val api = try {
            KeyguardUnlockApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Status bar clock not found; nothing is animated into the status bar on unlock", error)
            return
        }
        hooks.after(api.clockAttached) { clock, _ -> (clock as? TextView)?.let(clocks::add) }
        hooks.after(api.clockDetached) { clock, _ -> clocks.remove(clock) }
    }

    /** The status bar's clock on the display [lockScreenView] is on, outside that view's window. */
    fun find(lockScreenView: View): TextView? =
        synchronized(clocks) { clocks.toList() }
            .filter { it.isAttachedToWindow && it.width > 0 && it.rootView !== lockScreenView.rootView }
            .filter { it.display?.displayId == lockScreenView.display?.displayId }
            .minByOrNull { IntArray(2).also(it::getLocationOnScreen)[1] }
}
