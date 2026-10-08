package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.view.Display
import android.view.Surface
import android.view.View

import java.lang.ref.WeakReference

import kotlin.math.max

/**
 * How low a large clock may stand on the lock screen: the lowest its bottom
 * may reach, in window pixels, with room left under it for the smartspace card
 * that follows it ([ComposedSmartspacePlacement]) and above the fingerprint
 * sensor, spaced as the lock screen spaces its rows ([LockScreenInsets]).
 *
 * The sensor is read from the framework's own description of it
 * (`FingerprintSensorPropertiesInternal` and its `SensorLocationInternal`),
 * which SystemUI may read, in the
 * display's natural pixels at its largest resolution; it is scaled to the
 * resolution in use. Where there is no under-display sensor, or the screen is
 * turned, the shortcuts at the bottom are kept clear by [FALLBACK_SHARE] of the
 * window instead.
 */
internal object LockScreenFloor {

    /** The share of the window a clock may reach down to where no sensor sets the floor. */
    // ponytail: fixed share on phones without UDFPS; read the shortcuts' bounds if such a phone shows them overlapped.
    private const val FALLBACK_SHARE = 0.78f

    /** `Context.FINGERPRINT_SERVICE`, whose `FingerprintManager` the SDK no longer exposes. */
    private const val FINGERPRINT_SERVICE = "fingerprint"

    private var card = WeakReference<View>(null)

    /** The sensor's top in natural pixels at the largest resolution, read once; 0 while there is none. */
    private var sensorTop = -1f
    private var naturalHeight = 0

    /** Keeps room for [card] under the clock from now on. */
    fun followCard(card: View) {
        if (this.card.get() !== card) this.card = WeakReference(card)
    }

    /** The lowest [face]'s bottom may stand, in its window's pixels. On the main thread, before each frame. */
    fun of(face: View): Float {
        val root = face.rootView
        val windowHeight = root.height.toFloat()
        val sensor = sensorTopOnScreen(face)
        var floor = (if (sensor > 0f) sensor else windowHeight * FALLBACK_SHARE) - LockScreenInsets.rowGap(face.context)
        val shown = card.get()
        if (shown != null && shown.isShown && shown.rootView === root && shown.height > 0) {
            // As laid out, not as the scene scales it while waking, so the floor holds still through a wake.
            floor -= LockScreenInsets.cardGap(face.context) + shown.height
        }
        return floor
    }

    /** The sensor's top on screen, in pixels, or 0 where there is none to keep clear. */
    private fun sensorTopOnScreen(view: View): Float {
        val display = view.display ?: return 0f
        if (sensorTop < 0f) readSensor(view.context, display)
        if (sensorTop <= 0f || naturalHeight <= 0) return 0f
        if (display.rotation != Surface.ROTATION_0) return 0f
        val metrics = view.resources.displayMetrics
        return sensorTop * max(metrics.widthPixels, metrics.heightPixels) / naturalHeight
    }

    private fun readSensor(context: Context, display: Display) {
        sensorTop = 0f
        runCatching {
            val manager = context.getSystemService(FINGERPRINT_SERVICE) ?: return
            val sensors = manager.javaClass.getMethod("getSensorPropertiesInternal").invoke(manager) as List<*>
            for (sensor in sensors) {
                if (sensor == null || sensor.javaClass.getMethod("isAnyUdfpsType").invoke(sensor) != true) continue
                val location = sensor.javaClass.getMethod("getLocation").invoke(sensor) ?: continue
                val y = location.javaClass.getField("sensorLocationY").getInt(location)
                val radius = location.javaClass.getField("sensorRadius").getInt(location)
                sensorTop = (y - radius).toFloat()
                naturalHeight = display.supportedModes.maxOf { max(it.physicalWidth, it.physicalHeight) }
                return
            }
        }
    }
}
