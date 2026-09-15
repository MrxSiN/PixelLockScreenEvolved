package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * On unlock, flies the lock screen's time to the status bar clock, the way the
 * smartspace card flies to its place on the home screen, whether or not the
 * lock screen shows a card.
 *
 * The flight ([ClockFlightOverlay]), kept ready while the lock screen is up
 * ([ClockFlightStandby]), starts as soon as SystemUI reports an
 * unlock, however far a fast swipe has already faded the keyguard by then; the
 * picture starts as faded as the time was and comes up to full as it flies.
 * While a swipe drags the keyguard away, the time follows how far the keyguard
 * has faded, and goes back if the swipe is let go. Once the unlock is
 * committed, the flight heads for the status bar clock on the same spring
 * ([SpringFollower]), whose pull firms up over a moment rather than at once,
 * so the time never jumps or changes speed abruptly; it then waits on the
 * status bar clock until SystemUI shows it. Whatever stood in front of the
 * time on the lock screen ([occlusion]) is left in front of it as the flight
 * begins.
 */
internal class KeyguardClockUnlockMotion(
    private val unlockFrames: UnlockFrameLoop,
    private val standby: ClockFlightStandby,
) : HostPatch {

    private var flight: Flight? = null

    /** One flight in progress. */
    private class Flight(val overlay: ClockFlightOverlay, val time: View) {
        val spring = SpringFollower(PROGRESS_STIFFNESS)
        val progress get() = spring.value.toFloat().coerceIn(0f, 1f)
        var lastFrameNanos = 0L
        var committedAtNanos = 0L
        var landedAtNanos = 0L
        val committed get() = committedAtNanos != 0L
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

        if (!current.committed && api.isCommitted(controller)) current.committedAtNanos = frameTimeNanos

        val statusBarClockShown = current.overlay.targetShown()
        val wanted = when {
            current.committed -> LANDING_AIM
            api.isUnlocking(controller) -> 1f - ViewPictures.parentAlpha(current.time)
            else -> 0f
        }
        current.spring.stiffness = when {
            // SystemUI has brought the status bar back before the time landed; it hurries in.
            current.committed && statusBarClockShown -> HURRY_STIFFNESS
            current.committed -> {
                val firmed = ((frameTimeNanos - current.committedAtNanos) / NANOS_PER_MS / FIRM_UP_MS).coerceIn(0f, 1f)
                PROGRESS_STIFFNESS + (COMMITTED_STIFFNESS - PROGRESS_STIFFNESS) * firmed * firmed * (3f - 2f * firmed)
            }
            else -> PROGRESS_STIFFNESS
        }

        // A frame SystemUI was too busy to draw (the flight's own start takes a few) moves the
        // time on by no more than one frame's worth, so it never leaps on a late frame.
        val frameMs = if (current.lastFrameNanos == 0L) FRAME_MS else ((frameTimeNanos - current.lastFrameNanos) / NANOS_PER_MS).coerceAtMost(LONGEST_FRAME_MS)
        current.lastFrameNanos = frameTimeNanos
        current.spring.follow(wanted, frameMs / MS_PER_SECOND)
        current.overlay.setProgress(current.progress)

        if (current.committed && current.spring.value >= 1.0) {
            current.overlay.setProgress(1f)
            current.landedAtNanos = frameTimeNanos
        }
        val calledBack = !current.committed && wanted == 0f && current.progress < CALLED_BACK
        return !calledBack
    }

    private fun begin(api: KeyguardUnlockApi, controller: Any): Flight? {
        if (!api.isKeyguardShowing(controller)) {
            standby.discard()
            return null
        }
        val overlay = standby.take() ?: return null
        if (!overlay.show()) return null
        return Flight(overlay, overlay.time).also { flight = it }
    }

    private companion object {
        /**
         * How firmly the time is pulled toward where it should be. The keyguard fades
         * within a few frames, so the time follows it on a critically damped spring:
         * it sets off gently rather than leaping, and never overshoots.
         */
        const val PROGRESS_STIFFNESS = 120f

        /**
         * Once the unlock is committed the time heads for the status bar clock, landing in
         * about 260ms from rest, before SystemUI brings the status bar back some 400ms in.
         */
        const val COMMITTED_STIFFNESS = 500f

        /** How long the spring takes to firm up from following the swipe to heading for the status bar clock. */
        const val FIRM_UP_MS = 120f

        /** If the status bar clock is already back, the time closes in within a few frames. */
        const val HURRY_STIFFNESS = 3000f

        const val MS_PER_SECOND = 1000f

        /** Longest the landed time waits for the status bar clock before it is taken away regardless. */
        const val LONGEST_HANDOVER_MS = 1500f

        const val FRAME_MS = 8f

        /** The most one frame moves the flight on, a frame at 60Hz. */
        const val LONGEST_FRAME_MS = 17f

        /**
         * Where a committed flight's spring is aimed: a little past the status bar clock, so the time
         * reaches it still gently moving and lands, rather than creeping the last pixels and snapping.
         */
        const val LANDING_AIM = 1.02f

        const val NANOS_PER_MS = 1_000_000f
        const val CALLED_BACK = 0.005f
    }
}
