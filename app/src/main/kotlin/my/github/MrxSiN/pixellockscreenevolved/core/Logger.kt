package my.github.MrxSiN.pixellockscreenevolved.core

import android.util.Log

/**
 * Sink for diagnostics.
 *
 * Hook and clock code depends on this contract rather than on a concrete
 * logging framework, so the transport can change without touching behaviour.
 */
interface Logger {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
}

/** [Logger] backed by logcat, readable with `adb logcat -s PixelLockScreenEvolved`. */
object AndroidLogger : Logger {

    const val TAG: String = "PixelLockScreenEvolved"

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String, error: Throwable?) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }
}
