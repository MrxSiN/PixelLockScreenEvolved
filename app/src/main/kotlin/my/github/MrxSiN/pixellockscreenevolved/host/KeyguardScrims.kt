package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.view.View

import java.lang.reflect.Field

/**
 * The scrims SystemUI lays over the wallpaper and under the keyguard: the ones
 * that dim the lock screen photo, and the light reveal scrim that blacks the
 * photo out for the always-on display and reveals it again on waking.
 * Something drawn over the photo inside the keyguard misses them, so it asks
 * here how they would have dimmed it ([dim]) and has the light reveal scrim
 * drawn over it too ([darkenAsRevealed]).
 *
 * Found by id in the notification shade window; a scrim that is missing, or
 * whose fields have moved, darkens nothing.
 */
internal class KeyguardScrims(val root: View) {

    private val behind = scrim("scrim_behind")
    private val reveal = scrim("light_reveal_scrim")
    private val inFront = scrim("scrim_in_front")
    private val atop = Paint()
    private val gradientMatrix = Matrix()

    /**
     * The colour, with alpha, that the dimming scrims lay over the photo
     * together, from the lower to the higher, over the wallpaper's own black
     * dimming of [wallpaperDim]; transparent when nothing dims it. The lock
     * screen dims its photo by about a fifth.
     */
    fun dim(wallpaperDim: Float): Int {
        var red = 0f
        var green = 0f
        var blue = 0f
        var clear = 1f
        fun over(color: Int, alpha: Float) {
            val a = alpha.coerceIn(0f, 1f)
            red = red * (1f - a) + Color.red(color) * a
            green = green * (1f - a) + Color.green(color) * a
            blue = blue * (1f - a) + Color.blue(color) * a
            clear *= 1f - a
        }
        over(Color.BLACK, wallpaperDim)
        behind?.let(::scrimColor)?.let { (color, alpha) -> over(color, alpha) }
        inFront?.let(::scrimColor)?.let { (color, alpha) -> over(color, alpha) }

        val alpha = 1f - clear
        if (alpha <= 0f) return Color.TRANSPARENT
        return Color.argb((alpha * 255f).toInt(), (red / alpha).toInt().coerceIn(0, 255), (green / alpha).toInt().coerceIn(0, 255), (blue / alpha).toInt().coerceIn(0, 255))
    }

    private fun scrim(name: String): View? {
        val id = root.resources.getIdentifier(name, "id", root.context.packageName)
        return if (id == 0) null else root.findViewById(id)
    }

    /** A `ScrimView`'s colour and how opaque it is drawn. */
    private fun scrimColor(view: View): Pair<Int, Float>? {
        if (!view.isShown) return null
        val drawable = runCatching { view.field("mDrawable").get(view) as Drawable? }.getOrNull() ?: return null
        val color = runCatching { mainColor(drawable) }.getOrNull() ?: return null
        return color to view.alpha * drawable.alpha / 255f * Color.alpha(color) / 255f
    }

    /** Whether the light reveal scrim still hides any of the photo. */
    fun isRevealing(): Boolean {
        val view = reveal?.takeIf { it.isShown } ?: return false
        val revealed = runCatching { view.field("revealAmount").getFloat(view) }.getOrNull() ?: return false
        return view.alpha > 0f && revealed < 1f
    }

    /**
     * Darkens what [target] has drawn on [canvas], in [target]'s own pixels,
     * exactly as the light reveal scrim darkens the photo at the same place on
     * screen, only where [target] has drawn. The scrim reveals the photo from a
     * point outward rather than evenly, so a cut-out of the photo darkened by
     * one amount, or faded, stood out over photo still hidden, or let the clock
     * behind it show through.
     *
     * The scrim is drawn again here as `LightRevealScrim.onDraw` draws it, from
     * its own gradient paint, atop what is drawn. That needs [target] drawn in a
     * hardware layer of its own: on the window it darkened what was drawn
     * behind [target] too, and in a layer saved on the canvas it showed nothing.
     */
    fun darkenAsRevealed(canvas: Canvas, target: View) {
        val view = reveal ?: return
        if (!canvas.isHardwareAccelerated || !isRevealing()) return
        val targetToScrim = Matrix().also(target::transformMatrixToGlobal)
        targetToScrim.postConcat(Matrix().also { Matrix().also(view::transformMatrixToGlobal).invert(it) })

        val saved = canvas.save()
        canvas.concat(targetToScrim)
        runCatching { drawReveal(canvas, view) }
        canvas.restoreToCount(saved)
    }

    /** `LightRevealScrim.onDraw`, with every draw laid atop what [canvas] already holds. */
    private fun drawReveal(canvas: Canvas, view: View) {
        val amount = view.field("revealAmount").getFloat(view)
        val width = view.field("revealGradientWidth").getFloat(view)
        val height = view.field("revealGradientHeight").getFloat(view)
        val endColor = view.field("revealGradientEndColor").getInt(view)
        if (width <= 0f || height <= 0f || amount == 0f) {
            if (amount < 1f) canvas.drawColor(withAlpha(endColor, view.alpha), PorterDuff.Mode.SRC_ATOP)
            return
        }
        val startAlpha = view.field("startColorAlpha").getFloat(view)
        if (startAlpha > 0f) canvas.drawColor(withAlpha(endColor, startAlpha * view.alpha), PorterDuff.Mode.SRC_ATOP)

        val gradient = view.field("gradientPaint").get(view) as Paint
        val center = view.field("revealGradientCenter").get(view) as PointF
        gradientMatrix.setScale(width, height, 0f, 0f)
        gradientMatrix.postTranslate(center.x, center.y)
        gradient.shader?.setLocalMatrix(gradientMatrix)
        atop.set(gradient)
        atop.xfermode = ATOP
        atop.alpha = (gradient.alpha * view.alpha).toInt()
        canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), atop)
    }

    private fun mainColor(drawable: Drawable): Int = drawable.field("mMainColor").getInt(drawable)

    private companion object {
        val ATOP = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)

        /** [color] with its alpha set to [alpha], from 0 to 1. */
        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((alpha.coerceIn(0f, 1f) * 255f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        val FIELDS = HashMap<Pair<Class<*>, String>, Field>()

        /** A field of this object's class or one it extends, looked up once. */
        fun Any.field(name: String): Field = FIELDS.getOrPut(javaClass to name) {
            generateSequence(javaClass as Class<*>?) { it.superclass }
                .firstNotNullOfOrNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
                ?.apply { isAccessible = true }
                ?: throw NoSuchFieldException(name)
        }
    }
}
