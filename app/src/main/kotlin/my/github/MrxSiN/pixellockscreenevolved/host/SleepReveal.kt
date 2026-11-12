package my.github.MrxSiN.pixellockscreenevolved.host

import android.os.SystemClock
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

import kotlin.math.max

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks

/**
 * Draws the screen off a double tap asks for as a circle closing on the tapped
 * point, the reverse of the circle a tap on the always-on display wakes with.
 *
 * SystemUI reveals and unreveals the screen through one scrim
 * (`LightRevealScrim`), driven by an effect that says where the light is at
 * each step. Which effect a screen off gets depends on how the screen last
 * woke (`LightRevealScrimRepository`): the lift, a circle from the power
 * button after a power button wake, or a circle around the old point after a
 * tap on the always-on display. This answers each step of any of them with a
 * circle around the double tap while a point is standing, the way
 * Pixel Launcher Evolved's double tap on the home screen does. SystemUI's own
 * `CircleReveal` would draw the same circle, but no constructor of it is left
 * to call, so its shape is restated in [CircleReveal].
 *
 * A point is given only when the double tap really turns the screen off
 * ([remember]), and is kept until the screen is dark or until it is too old to
 * belong to that gesture, so a later screen off from the power button or a
 * timeout keeps its own effect. Read and written on the main thread.
 */
internal class SleepReveal(private val hooks: Hooks, private val logger: Logger) {

    private var x = 0f
    private var y = 0f
    private var takenAt = NONE

    /** Draws the next screen off around ([x], [y]), in the scrim's pixels. */
    fun remember(x: Float, y: Float) {
        this.x = x
        this.y = y
        takenAt = SystemClock.uptimeMillis()
    }

    fun install(classLoader: ClassLoader) {
        val scrim = Class.forName(SCRIM, false, classLoader)
        val float = Float::class.javaPrimitiveType!!
        val bounds = scrim.getDeclaredMethod("setRevealGradientBounds", float, float, float, float)
        val alpha = scrim.getDeclaredMethod("setRevealGradientEndColorAlpha", float)
        val centre = runCatching { scrim.field("startColorAlpha") }.getOrNull()

        // After the effect has set its step: the last values written are the ones drawn.
        for (effect in EFFECTS) {
            val step = Class.forName(effect, false, classLoader).getDeclaredMethod("setRevealAmountOnScrim", float, scrim)
            hooks.after(step) { _, args ->
                centre?.let { clearCentre(args[1] as View, it) }
                if (takenAt == NONE) return@after
                runCatching { draw(args[0] as Float, args[1] as View, bounds, alpha) }
                    .onFailure {
                        // Given up rather than failing again on every frame of the reveal.
                        takenAt = NONE
                        logger.warn("The screen off could not be drawn around the double tap", it)
                    }
            }
        }
    }

    /**
     * SystemUI's own double-tap screen off (`DoubleTapReveal`, from CP4A) darkens the
     * circle's centre too (`startColorAlpha`), up to opaque once the screen is dark,
     * and none of these effects ever sets it back: a wake by the power button, a lift
     * or a tap then left the scrim black behind the whole lock screen and every
     * notification. Google ships that gesture off; this module turns it on, so it
     * clears what the gesture leaves. `DoubleTapReveal` writes the centre after its
     * circle's step, so its own screen off is untouched.
     */
    private fun clearCentre(scrim: View, centre: Field) {
        if (centre.getFloat(scrim) == 0f) return
        centre.setFloat(scrim, 0f)
        scrim.invalidate()
    }

    private fun draw(amount: Float, scrim: View, bounds: Method, alpha: Method) {
        if (SystemClock.uptimeMillis() - takenAt > FRESH_FOR_MILLIS || scrim.width <= 0 || scrim.height <= 0) {
            takenAt = NONE
            return
        }
        val radius = CircleReveal.endRadius(x, y, scrim.width, scrim.height) * amount
        alpha.invoke(scrim, CircleReveal.endColorAlpha(amount))
        bounds.invoke(scrim, x - radius, y - radius, x + radius, y + radius)
        // The screen is dark: this gesture has had its reveal.
        if (amount <= 0f) takenAt = NONE
    }

    private companion object {
        const val SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
        /** The effects a screen off can get; the linear ones are for folding and unfolding. */
        val EFFECTS = listOf(
            "com.android.systemui.statusbar.LiftReveal",
            "com.android.systemui.statusbar.PowerButtonReveal",
            "com.android.systemui.statusbar.CircleReveal",
        )
        const val NONE = -1L

        /** A screen off starts within a frame or two of the tap; anything later is not this gesture's. */
        const val FRESH_FOR_MILLIS = 2_000L
    }
}

/**
 * SystemUI's `CircleReveal` shape: the lit circle around a point is
 * `endRadius * amount`, from every corner lit at 1 down to the point at 0, and
 * the dark outside stays opaque until the screen is half revealed.
 */
internal object CircleReveal {

    private const val HALF = 0.5f

    /** The radius that lights a [width] by [height] scrim to every corner from ([x], [y]). */
    fun endRadius(x: Float, y: Float, width: Int, height: Int): Float = max(max(x, width - x), max(y, height - y))

    fun endColorAlpha(amount: Float): Float = 1f - 2f * max(amount - HALF, 0f)
}
