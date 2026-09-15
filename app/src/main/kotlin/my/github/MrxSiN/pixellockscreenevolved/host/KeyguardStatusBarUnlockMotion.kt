package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewGroup

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Carries the status bar through an unlock.
 *
 * The icons and battery on the right of the lock screen status bar are the
 * same ones the status bar shows after it, yet SystemUI faded them out with
 * the keyguard and back in with the status bar a moment later; they are held
 * steady instead ([StatusIconsHandover]). The notification icons beside the
 * status bar clock come in with Material 3 Expressive motion rather than
 * simply reappearing ([NotificationIconsEntrance]). Both give way if the unlock
 * is called back.
 */
internal class KeyguardStatusBarUnlockMotion(
    private val hooks: Hooks,
    private val unlockFrames: UnlockFrameLoop,
    private val statusBarClocks: StatusBarClocks,
    private val logger: Logger,
) : HostPatch {

    private var keyguardStatusBar = WeakReference<View>(null)
    private var unlock: Unlock? = null

    /** What is being carried through one unlock. */
    private class Unlock(
        val handover: StatusIconsHandover?,
        val entrance: NotificationIconsEntrance?,
        val startedAtNanos: Long,
    ) {
        var handedOver = handover == null
        var enteredAtNanos = if (entrance == null) 1L else 0L
        val entered get() = enteredAtNanos != 0L
        var committed = false
    }

    override fun install(classLoader: ClassLoader) {
        val statusBarApi = try {
            KeyguardStatusBarApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen status bar not found; the status bar fades on unlock as SystemUI animates it", error)
            return
        }

        hooks.after(statusBarApi.inflated) { view, _ -> keyguardStatusBar = WeakReference(view as View) }

        unlockFrames.add(
            name = "Status bar unlock motion",
            step = { unlockApi, controller, frameTimeNanos -> step(unlockApi, statusBarApi, controller, frameTimeNanos) },
            stop = ::finish,
        )
    }

    private fun step(unlockApi: KeyguardUnlockApi, statusBarApi: KeyguardStatusBarApi, controller: Any, frameTimeNanos: Long): Boolean {
        val current = unlock ?: begin(unlockApi, statusBarApi, controller, frameTimeNanos) ?: return false
        if (unlockApi.isCommitted(controller)) current.committed = true
        // Once committed the unlock runs on after the keyguard's own state has moved on; before, it can be called back.
        if (!current.committed && !unlockApi.isUnlocking(controller)) return false

        if (!current.handedOver && current.handover?.follow() == true) {
            current.handover.remove()
            current.handedOver = true
        }
        val entrance = current.entrance
        if (entrance != null) {
            when {
                current.entered -> entrance.admitNewcomers()
                entrance.rowShown() -> {
                    entrance.enter()
                    current.enteredAtNanos = frameTimeNanos
                }
                else -> entrance.hold()
            }
        }
        val waitedMs = (frameTimeNanos - current.startedAtNanos) / NANOS_PER_MS
        val sinceEnteredMs = if (entrance != null && current.entered) (frameTimeNanos - current.enteredAtNanos) / NANOS_PER_MS else Float.MAX_VALUE
        val done = current.handedOver && current.entered && sinceEnteredMs >= NEWCOMERS_MS
        return !done && waitedMs < LONGEST_WAIT_MS
    }

    private fun begin(unlockApi: KeyguardUnlockApi, statusBarApi: KeyguardStatusBarApi, controller: Any, frameTimeNanos: Long): Unlock? {
        if (!unlockApi.isKeyguardShowing(controller)) return null
        val keyguardBar = keyguardStatusBar.get()?.takeIf { it.isAttachedToWindow } ?: return null
        val icons = statusBarApi.systemIcons(keyguardBar) ?: return null
        val clock = statusBarClocks.find(keyguardBar) ?: return null
        val statusBar = clock.rootView

        val handover = statusBar.findViewByName<View>(END_SIDE_ID)
            ?.takeIf { ViewPictures.visibleAlpha(icons) >= MOST_FADE_TO_START && icons.width > 0 }
            ?.let { StatusIconsHandover(icons, it, clock, logger) }
            ?.takeIf { it.show() }
        val entrance = statusBar.findViewByName<ViewGroup>(START_SIDE_ID)
            ?.let { NotificationIconsEntrance(it, clock) }
            ?.also { it.hold() }
        if (handover == null && entrance == null) return null
        return Unlock(handover, entrance, frameTimeNanos).also { unlock = it }
    }

    /** Ends what the unlock was carrying, whether it finished, was called back, or took too long. */
    private fun finish() {
        val current = unlock ?: return
        unlock = null
        if (!current.handedOver) current.handover?.remove()
        if (!current.entered) current.entrance?.release()
    }

    private companion object {
        /** The status bar's right side: its status icons and, beside them, the battery. */
        const val END_SIDE_ID = "status_bar_end_side_content"
        /** The clock's side of the status bar: the clock, and the notification icons and chips after it. */
        const val START_SIDE_ID = "status_bar_start_side_except_heads_up"

        /** Longest anything is held for the status bar before it is given back regardless. */
        const val LONGEST_WAIT_MS = 3500f

        /** How long after the row comes in that icons and chips SystemUI adds late still come in the same way. */
        const val NEWCOMERS_MS = 1200f

        /** The lock screen's icons must still look this solid for a picture of them to stand in. */
        const val MOST_FADE_TO_START = 0.7f

        const val NANOS_PER_MS = 1_000_000f
    }
}
