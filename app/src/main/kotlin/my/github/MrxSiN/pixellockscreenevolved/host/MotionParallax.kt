package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View

import java.lang.ref.WeakReference

import kotlin.math.asin

import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * The clock's shift against the photo as the phone tilts, so the time seems to
 * float between the photo's subject and what is behind it.
 *
 * The phone's tilt from side to side is read from the game rotation vector, the
 * sensor fusion that needs no magnetometer, measured from where the phone has
 * been held lately: holding it at a slant settles back to the middle over a
 * couple of seconds, and only the movement shows. The sensor listens only while
 * a clock asks for the shift ([offsetFor]) before each frame, and stops a moment
 * after the last ask, so a dark screen or a closed lock screen costs nothing.
 * Off with the setting ([DepthMotionSetting]) or with animations off.
 */
internal object MotionParallax : SensorEventListener {

    /** The widest the clock moves either way, in dp. */
    private const val SHIFT_DP = 12f

    /** The tilt, in radians, that moves the clock all the way. */
    private const val FULL_TILT = 0.35f

    /** How fast the middle follows how the phone is held, per second. */
    private const val RECENTRE_PER_SECOND = 0.5f

    /** How long after the last ask the sensor stops. */
    private const val IDLE_MS = 1_500L

    private val main = Handler(Looper.getMainLooper())
    private var sensors: SensorManager? = null
    private var listening = false
    private var enabled = false
    private var watching = false
    private var lastAsk = 0L
    private var lastEvent = 0L
    private var centre = Float.NaN
    private var tilt = 0f
    /** The clocks following the tilt, walked by index: it runs on every frame and every sensor event. */
    private val views = ArrayList<WeakReference<View>>()

    private val rotation = FloatArray(9)

    private val idle = object : Runnable {
        override fun run() {
            if (SystemClock.uptimeMillis() - lastAsk >= IDLE_MS) stop() else main.postDelayed(this, IDLE_MS)
        }
    }

    /** How far, in pixels, [view] is shifted now; asked before each of its frames, on the main thread. */
    fun offsetFor(view: View): Float {
        watch(view.context)
        if (!enabled || SystemMotion.isReduced) {
            stop()
            return 0f
        }
        lastAsk = SystemClock.uptimeMillis()
        if (!follows(view)) {
            views.removeAll { it.get() == null }
            views += WeakReference(view)
        }
        start(view.context)
        if (centre.isNaN()) return 0f
        val share = ((tilt - centre) / FULL_TILT).coerceIn(-1f, 1f)
        return -share * SHIFT_DP * view.resources.displayMetrics.density
    }

    private fun watch(context: Context) {
        if (watching) return
        watching = true
        DepthMotionSetting.watch(context.contentResolver) { on ->
            enabled = on
            invalidateAll()
        }
    }

    private fun start(context: Context) {
        if (listening) return
        val manager = sensors ?: context.getSystemService(SensorManager::class.java).also { sensors = it } ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: return
        listening = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI, main)
        centre = Float.NaN
        lastEvent = 0L
        main.postDelayed(idle, IDLE_MS)
    }

    private fun stop() {
        if (!listening) return
        listening = false
        sensors?.unregisterListener(this)
        main.removeCallbacks(idle)
        centre = Float.NaN
        invalidateAll()
    }

    private fun follows(view: View): Boolean {
        for (i in views.indices) if (views[i].get() === view) return true
        return false
    }

    private fun invalidateAll() {
        for (i in views.indices) views[i].get()?.invalidate()
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        tilt = sideTilt(rotation)
        val now = SystemClock.uptimeMillis()
        val seconds = if (lastEvent == 0L) 0f else ((now - lastEvent) / 1000f).coerceAtMost(0.2f)
        lastEvent = now
        centre = if (centre.isNaN()) tilt else centre + (tilt - centre) * (RECENTRE_PER_SECOND * seconds).coerceAtMost(1f)
        invalidateAll()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * How far the phone leans from side to side, in radians, right side down positive: the
 * angle between the screen's x axis and the horizontal, read from where "up" points in the
 * phone's frame (the rotation matrix's last row). `SensorManager.getOrientation`'s roll
 * was used before, but it locks up as the phone stands upright, as it is held to read the
 * lock screen: small leans swung it to its ends, and it wrapped through ±π.
 */
internal fun sideTilt(rotation: FloatArray): Float = asin((-rotation[6]).coerceIn(-1f, 1f))
