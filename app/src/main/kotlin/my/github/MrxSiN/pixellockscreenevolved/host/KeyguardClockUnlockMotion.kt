package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.Choreographer
import android.view.View
import android.widget.TextView

import java.util.Collections
import java.util.WeakHashMap

import kotlin.math.exp

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * On unlock, flies the lock screen's time to the status bar clock, the way the
 * smartspace card flies to its place on the home screen.
 *
 * The flight ([ClockFlightOverlay]) starts as soon as an unlock begins, while
 * the lock screen is still solid. While a swipe drags the keyguard away, the
 * time follows how far the keyguard has faded, and goes back if the swipe is
 * let go. Once the unlock is committed, the flight finishes on its own over a
 * fixed time, well after the keyguard itself has gone, and waits on the status
 * bar clock until SystemUI shows it. Each frame eases toward where the time
 * should be, so a keyguard that jumps does not make it jump.
 */
internal class KeyguardClockUnlockMotion(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val clocks = weakSet<ClockControllerAdapter>()
    private val statusBarClocks = weakSet<TextView>()

    private var unlockController: Any? = null
    private var framesRunning = false

    private var flight: Flight? = null

    /** One flight in progress. */
    private class Flight(val overlay: ClockFlightOverlay, val time: View) {
        var progress = 0f
        var lastFrameNanos = 0L
        var committedAtNanos = 0L
        var progressAtCommit = 0f
        var landedAtNanos = 0L
        val committed get() = committedAtNanos != 0L
    }

    /** Remembers a clock SystemUI built, so its time can be found on unlock. */
    fun track(clock: ClockControllerAdapter) {
        clocks.add(clock)
    }

    override fun install(classLoader: ClassLoader) {
        val api = try {
            KeyguardUnlockApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Unlock state not found; the clock will not fly on unlock", error)
            return
        }

        hooks.after(api.clockAttached) { clock, _ -> (clock as? TextView)?.let(statusBarClocks::add) }
        hooks.after(api.clockDetached) { clock, _ -> statusBarClocks.remove(clock) }

        val onFrame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val more = runCatching { step(api, frameTimeNanos) }
                    .onFailure { logger.warn("Clock flight failed", it) }
                    .getOrDefault(false)
                if (more) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    flight?.overlay?.remove()
                    flight = null
                    framesRunning = false
                }
            }
        }

        api.unlockProgressed.forEach { method ->
            hooks.after(method) { controller, _ ->
                unlockController = controller
                if (!framesRunning && controller != null && api.isUnlocking(controller)) {
                    framesRunning = true
                    Choreographer.getInstance().postFrameCallback(onFrame)
                }
            }
        }
    }

    /** Moves the flight on by one frame; false once it has landed, been called back, or cannot start. */
    private fun step(api: KeyguardUnlockApi, frameTimeNanos: Long): Boolean {
        val controller = unlockController ?: return false
        val current = flight ?: begin(api, controller) ?: return false

        if (current.landedAtNanos != 0L) {
            val waitedMs = (frameTimeNanos - current.landedAtNanos) / NANOS_PER_MS
            return !current.overlay.handOver() && waitedMs < LONGEST_HANDOVER_MS
        }

        if (!current.committed && api.isCommitted(controller)) {
            current.committedAtNanos = frameTimeNanos
            current.progressAtCommit = current.progress
        }

        val wanted = when {
            current.committed -> {
                val elapsed = (frameTimeNanos - current.committedAtNanos) / NANOS_PER_MS / FINISH_MS
                current.progressAtCommit + (1f - current.progressAtCommit) * elapsed.coerceIn(0f, 1f)
            }
            api.isUnlocking(controller) -> fadeAround(current.time)
            else -> 0f
        }

        val frameMs = if (current.lastFrameNanos == 0L) FRAME_MS else (frameTimeNanos - current.lastFrameNanos) / NANOS_PER_MS
        current.lastFrameNanos = frameTimeNanos
        current.progress += (wanted - current.progress) * (1f - exp(-frameMs / EASE_MS))
        current.overlay.setProgress(current.progress)

        if (current.committed && wanted >= 1f && current.progress > LANDED) {
            current.overlay.setProgress(1f)
            current.landedAtNanos = frameTimeNanos
        }
        val calledBack = !current.committed && wanted == 0f && current.progress < CALLED_BACK
        return !calledBack
    }

    private fun begin(api: KeyguardUnlockApi, controller: Any): Flight? {
        if (!api.isKeyguardShowing(controller)) return null
        val time = shownTime() ?: return null
        if (fadeAround(time) > MOST_FADE_TO_START) return null
        val target = statusBarClock(time) ?: return null
        val overlay = ClockFlightOverlay(time, target, logger)
        if (!overlay.show()) return null
        return Flight(overlay, time).also { flight = it }
    }

    /** The time of this module's clock now on the lock screen, if one is. */
    private fun shownTime(): View? =
        synchronized(clocks) { clocks.toList() }
            .flatMap { it.timeViews }
            .firstOrNull { it.isAttachedToWindow && it.isShown && it.width > 0 }

    /** The status bar's clock: a SystemUI clock laid out outside the window the lock screen is in. */
    private fun statusBarClock(time: View): TextView? =
        synchronized(statusBarClocks) { statusBarClocks.toList() }
            .filter { it.isAttachedToWindow && it.width > 0 && it.rootView !== time.rootView }
            .filter { it.display?.displayId == time.display?.displayId }
            .minByOrNull { IntArray(2).also(it::getLocationOnScreen)[1] }

    private companion object {
        /** How long a committed unlock takes to land the time on the status bar clock. */
        const val FINISH_MS = 400f

        /** How quickly the time catches up with where it should be. */
        const val EASE_MS = 35f

        /** Longest the landed time waits for the status bar clock before it is taken away regardless. */
        const val LONGEST_HANDOVER_MS = 1500f

        const val FRAME_MS = 8f
        const val NANOS_PER_MS = 1_000_000f
        const val LANDED = 0.995f
        const val CALLED_BACK = 0.005f

        /** Past this fade the time is too faint to pick up without visibly reappearing. */
        const val MOST_FADE_TO_START = 0.3f

        /** How much the keyguard around [view] has faded: 0 while fully shown, 1 once gone. */
        fun fadeAround(view: View): Float {
            var alpha = 1f
            var parent = view.parent
            while (parent is View) {
                alpha *= parent.alpha
                parent = parent.parent
            }
            return 1f - alpha
        }

        fun <T> weakSet(): MutableSet<T> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<T, Boolean>()))
    }
}
