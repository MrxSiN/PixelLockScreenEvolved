package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * On unlock, flies the lock screen's time to the status bar clock, the way the
 * smartspace card flies to its place on the home screen. Without a card on the
 * lock screen there is nothing for the time to fly with, so it does not.
 *
 * The flight ([ClockFlightOverlay]) starts as soon as an unlock begins, while
 * the lock screen is still solid. While a swipe drags the keyguard away, the
 * time follows how far the keyguard has faded, and goes back if the swipe is
 * let go. Once the unlock is committed, the flight finishes on its own over a
 * fixed time, well after the keyguard itself has gone, and waits on the status
 * bar clock until SystemUI shows it. The time follows where it should be on a
 * spring ([SpringFollower]), so a keyguard that jumps does not make it jump. Whatever stood in
 * front of the time on the lock screen ([occlusion]) is left in front of it as
 * the flight begins.
 */
internal class KeyguardClockUnlockMotion(
    private val unlockFrames: UnlockFrameLoop,
    private val statusBarClocks: StatusBarClocks,
    private val occlusion: TimeOcclusion,
    private val logger: Logger,
) : HostPatch {

    private val clocks = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()))

    private var flight: Flight? = null

    /** One flight in progress. */
    private class Flight(val overlay: ClockFlightOverlay, val time: View) {
        val spring = SpringFollower(PROGRESS_STIFFNESS)
        val progress get() = spring.value.toFloat()
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
        unlockFrames.add(
            name = "Clock flight",
            step = ::step,
            stop = {
                flight?.overlay?.remove()
                flight = null
            },
        )
    }

    /** Moves the flight on by one frame; false once it has landed, been called back, or cannot start. */
    private fun step(api: KeyguardUnlockApi, controller: Any, frameTimeNanos: Long): Boolean {
        val current = flight ?: begin(api, controller) ?: return false

        if (current.landedAtNanos != 0L) {
            val waitedMs = (frameTimeNanos - current.landedAtNanos) / NANOS_PER_MS
            return !current.overlay.handOver() && waitedMs < LONGEST_HANDOVER_MS
        }

        if (!current.committed && api.isCommitted(controller)) {
            current.committedAtNanos = frameTimeNanos
            current.progressAtCommit = current.progress
        }

        val statusBarClockShown = current.overlay.targetShown()
        val wanted = when {
            // SystemUI has brought the status bar back before the time landed; it hurries in.
            current.committed && statusBarClockShown -> 1f
            current.committed -> {
                val elapsed = (frameTimeNanos - current.committedAtNanos) / NANOS_PER_MS / FINISH_MS
                current.progressAtCommit + (1f - current.progressAtCommit) * elapsed.coerceIn(0f, 1f)
            }
            api.isUnlocking(controller) -> 1f - ViewPictures.parentAlpha(current.time)
            else -> 0f
        }
        current.spring.stiffness = when {
            current.committed && statusBarClockShown -> HURRY_STIFFNESS
            current.committed -> COMMITTED_STIFFNESS
            else -> PROGRESS_STIFFNESS
        }

        val frameMs = if (current.lastFrameNanos == 0L) FRAME_MS else (frameTimeNanos - current.lastFrameNanos) / NANOS_PER_MS
        current.lastFrameNanos = frameTimeNanos
        current.spring.follow(wanted, frameMs / MS_PER_SECOND)
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
        // The time flies alongside the smartspace card, which flies to the home screen; with no card it stays put.
        if (!SmartspaceCard.isShownAround(time)) return null
        if (1f - ViewPictures.parentAlpha(time) > MOST_FADE_TO_START) return null
        val target = statusBarClocks.find(time) ?: return null
        val overlay = ClockFlightOverlay(time, target, occlusion, logger)
        if (!overlay.show()) return null
        return Flight(overlay, time).also { flight = it }
    }

    /** The time of this module's clock now on the lock screen, if one is. */
    private fun shownTime(): View? =
        synchronized(clocks) { clocks.toList() }
            .flatMap { it.timeViews }
            .firstOrNull { it.isAttachedToWindow && it.isShown && it.width > 0 }

    private companion object {
        /**
         * How long a committed unlock takes to land the time on the status bar clock, before
         * the spring's own lag. SystemUI brings the status bar back about 400ms into an unlock,
         * and the time should be there first.
         */
        const val FINISH_MS = 280f

        /**
         * How firmly the time is pulled toward where it should be. The keyguard fades
         * within a few frames, so the time follows it on a critically damped spring:
         * it sets off gently rather than leaping, and never overshoots.
         */
        const val PROGRESS_STIFFNESS = 120f

        /** Once the unlock is committed the time follows its timed path closely, lagging about 70ms. */
        const val COMMITTED_STIFFNESS = 800f

        /** If the status bar clock is already back, the time closes in within a few frames. */
        const val HURRY_STIFFNESS = 3000f

        const val MS_PER_SECOND = 1000f

        /** Longest the landed time waits for the status bar clock before it is taken away regardless. */
        const val LONGEST_HANDOVER_MS = 1500f

        const val FRAME_MS = 8f
        const val NANOS_PER_MS = 1_000_000f
        const val LANDED = 0.995f
        const val CALLED_BACK = 0.005f

        /** Past this fade the time is too faint to pick up without visibly reappearing. */
        const val MOST_FADE_TO_START = 0.3f
    }
}
