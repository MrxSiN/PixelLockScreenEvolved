package my.github.MrxSiN.pixellockscreenevolved.host

import android.os.SystemClock

import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks

/**
 * Lets the first tap after a double tap to sleep wake the screen again.
 *
 * A tap on the always-on display wakes the screen through
 * `PulsingGestureListener.onSingleTapUp`, which first asks the falsing manager
 * whether the tap looks accidental. Straight after a double tap to sleep it
 * says so: the ShadeLog shows `isDozing: true, proximityIsNotNear: true,
 * isNotFalseTap: false` for the first tap, then the next one about half a
 * second later wakes. So for a moment after the double tap ([onSlept]), that
 * one question is answered "not accidental" for that one listener: the
 * falsing answer is overridden only while `onSingleTapUp` is running. Every
 * other check stays, proximity included, so a phone put in a pocket still
 * stays dark.
 *
 * Main thread only, like the touches it follows.
 */
internal class WakeAfterDoubleTapSleep(private val hooks: Hooks) {

    private var sleptAt = NEVER
    private var inTapUp = false

    /** A double tap has just turned the screen off. */
    fun onSlept() {
        sleptAt = SystemClock.uptimeMillis()
    }

    fun install(classLoader: ClassLoader) {
        val float = Float::class.javaPrimitiveType!!
        val tapUp = Class.forName(LISTENER, false, classLoader).getDeclaredMethod("onSingleTapUp", float, float)
        val isFalseTap = Class.forName(FALSING, false, classLoader).getDeclaredMethod("isFalseTap", Int::class.javaPrimitiveType)

        hooks.before(tapUp) { _, _ ->
            inTapUp = sleptAt != NEVER && SystemClock.uptimeMillis() - sleptAt < WINDOW_MILLIS
        }
        hooks.after(tapUp) { _, _ -> inTapUp = false }
        hooks.replaceResult(isFalseTap) { _, _, result -> if (inTapUp) false else result }
    }

    private companion object {
        const val LISTENER = "com.android.systemui.shade.PulsingGestureListener"
        const val FALSING = "com.android.systemui.classifier.BrightLineFalsingManager"
        const val NEVER = -1L

        // ponytail: fixed window; stock falsing let taps through again after about 0.5-1.3 s on CP3A.261005.005.
        const val WINDOW_MILLIS = 2_000L
    }
}
