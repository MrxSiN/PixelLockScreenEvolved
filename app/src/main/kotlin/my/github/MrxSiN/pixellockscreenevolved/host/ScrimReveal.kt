package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.view.View

import java.lang.reflect.Field
import java.util.WeakHashMap

/**
 * SystemUI's light-reveal scrim, read as it last drew: the black it lays over
 * the photo on the always-on display, and the gradient it sweeps away on a wake
 * (the lift, the circle from a tap, the ripple from the fingerprint sensor) and
 * back on going to sleep. Where it is dark, the photo is not seen, so whatever
 * is drawn from the photo (the always-on wallpaper, the depth effect's subject)
 * follows it rather than the doze fraction, which on `CP4A.261105.040.A1`
 * jumps to 1 the moment the screen goes off while the scrim takes about 400 ms
 * to darken. Read on the main thread.
 */
internal class ScrimReveal(type: Class<*>) {

    private val amount: Field = type.field("revealAmount")
    private val width: Field = type.field("revealGradientWidth")
    private val height: Field = type.field("revealGradientHeight")
    private val gradient: Field = type.field("gradientPaint")

    /** The flat dark SystemUI lays over the whole scrim first, a circle's centre; null where this build has none. */
    private val startAlpha: Field? = runCatching { type.field("startColorAlpha") }.getOrNull()

    /** The scrim each window drew last, by its root view: the lock screen's, and any preview's apart. */
    private val scrims = WeakHashMap<View, View>()

    /** Notes [scrim] as its window's, as it draws: written only when it changes, as it draws every frame of a reveal. */
    fun drew(scrim: View) {
        val root = scrim.rootView
        if (scrims[root] !== scrim) scrims[root] = scrim
    }

    /** The scrim in [view]'s window, or null where none has drawn. */
    fun scrimFor(view: View): View? = scrims[view.rootView]

    /** How far [scrim] has revealed what is behind it, from 0 dark to 1 clear. */
    fun amount(scrim: View): Float = amount.getFloat(scrim)

    /**
     * [amount], or NaN where it cannot be read: a primitive, as it is read before every frame of the
     * lock screen, where a `Float?` or a `Result` would allocate.
     */
    fun amountOrNaN(scrim: View): Float = try {
        amount.getFloat(scrim)
    } catch (_: ReflectiveOperationException) {
        Float.NaN
    } catch (_: IllegalArgumentException) {
        Float.NaN
    }

    /**
     * [scrim]'s gradient, in its own pixels, whose alpha is how dark the scrim is at each point; null where
     * it is dark all over, as its own draw takes it: nothing revealed yet, or no gradient placed.
     */
    fun darkness(scrim: View): Shader? {
        if (amount(scrim) <= 0f || width.getFloat(scrim) <= 0f || height.getFloat(scrim) <= 0f) return null
        return (gradient.get(scrim) as? Paint)?.shader
    }

    /**
     * Draws how dark [scrim] is onto [canvas], in the scrim's own pixels, through [paint], whose xfermode
     * decides what that does (`DST_OUT` clears what lies under the dark), as the scrim's own draw lays its
     * black: the flat start colour first, then the gradient through the gradient paint's colour filter. The
     * filter matters: it multiplies by the end colour, whose alpha (`revealGradientEndColorAlpha`) fades the
     * whole black in and out, and the shader's alpha alone read the scrim as half dark over the clock for
     * most of a wake (until 480 ms, where the photo there was fully up by 216 ms) and from the first frame
     * of going to sleep (MEASURED frame by frame on screen recordings). Returns false, drawing nothing,
     * where the scrim is dark all over.
     */
    fun drawDarkness(scrim: View, canvas: Canvas, paint: Paint): Boolean {
        val shader = darkness(scrim) ?: return false
        val scrimPaint = gradient.get(scrim) as Paint
        val right = scrim.width.toFloat()
        val bottom = scrim.height.toFloat()
        val start = startAlpha?.getFloat(scrim) ?: 0f
        if (start > 0f) {
            paint.alpha = (start.coerceIn(0f, 1f) * OPAQUE).toInt()
            canvas.drawRect(0f, 0f, right, bottom, paint)
            paint.alpha = OPAQUE
        }
        paint.shader = shader
        paint.colorFilter = scrimPaint.colorFilter
        canvas.drawRect(0f, 0f, right, bottom, paint)
        paint.shader = null
        paint.colorFilter = null
        return true
    }

    private companion object {
        const val OPAQUE = 255
    }
}
