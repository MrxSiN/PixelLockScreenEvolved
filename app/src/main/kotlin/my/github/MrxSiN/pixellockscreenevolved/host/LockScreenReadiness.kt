package my.github.MrxSiN.pixellockscreenevolved.host

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tells whatever gets an unlock ready ahead of time ([Listener]) when the lock
 * screen has settled, awake and fully shown, and when it has gone, dozing or
 * taken away.
 *
 * It follows the time of every clock SystemUI builds. A little after each burst
 * of lock screen frames it looks again, so a burst is looked at once, and the
 * work it starts never lands in the middle of the lock screen's own motion.
 */
internal class LockScreenReadiness {

    /** Something kept ready while the lock screen is up. */
    interface Listener {
        /** The lock screen has settled with [face]'s time on it; called again after later frames. */
        fun onLockScreenSettled(face: ClockFaceAdapter)

        /** The lock screen is dozing or gone; whatever was kept ready should be let go. */
        fun onLockScreenLeft()
    }

    private val clocks = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()))
    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())
    private var lookPosted = false

    private val look = Runnable {
        lookPosted = false
        lookAgain()
    }

    private val lockScreenFrame = ViewTreeObserver.OnPreDrawListener {
        if (!lookPosted) {
            lookPosted = true
            main.postDelayed(look, LOOK_AFTER_MS)
        }
        true
    }

    fun addListener(listener: Listener) {
        listeners += listener
    }

    /** Follows the faces of a clock SystemUI built. */
    fun track(clock: ClockControllerAdapter) {
        clocks.add(clock)
        clock.faces.forEach { watch(it.timeView) }
    }

    /** The face whose time is now on the lock screen, in the notification shade window, if one is. */
    fun shownFace(): ClockFaceAdapter? =
        synchronized(clocks) { clocks.toList() }
            .flatMap { it.faces }
            .firstOrNull { face ->
                val time = face.timeView
                time.isAttachedToWindow && time.isShown && time.width > 0 &&
                    (time.rootView.layoutParams as? WindowManager.LayoutParams)?.type == TYPE_NOTIFICATION_SHADE
            }

    private fun watch(time: View) {
        time.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = view.viewTreeObserver.addOnPreDrawListener(lockScreenFrame)

            override fun onViewDetachedFromWindow(view: View) {
                view.viewTreeObserver.removeOnPreDrawListener(lockScreenFrame)
                listeners.forEach(Listener::onLockScreenLeft)
            }
        })
        if (time.isAttachedToWindow) time.viewTreeObserver.addOnPreDrawListener(lockScreenFrame)
    }

    private fun lookAgain() {
        val face = shownFace()
        if (face == null || face.dozeFraction > 0f) {
            listeners.forEach(Listener::onLockScreenLeft)
            return
        }
        // While the keyguard fades, for the bouncer or an unlock, nothing is started that could make it stutter.
        if (ViewPictures.visibleAlpha(face.timeView) < 1f) return
        listeners.forEach { it.onLockScreenSettled(face) }
    }

    private companion object {
        /** How long after a lock screen frame it is looked at again, so a burst of frames is looked at once. */
        const val LOOK_AFTER_MS = 250L

        /** `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`, the window the lock screen is drawn in. */
        const val TYPE_NOTIFICATION_SHADE = 2040
    }
}
