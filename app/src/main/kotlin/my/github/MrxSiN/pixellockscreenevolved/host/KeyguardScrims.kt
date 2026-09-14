package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View

import java.lang.reflect.Field

/**
 * The scrims SystemUI lays over the wallpaper and under the keyguard: the ones
 * that dim the lock screen photo, and the light reveal scrim that blacks the
 * photo out for the always-on display and reveals it again on waking.
 * Something drawn over the photo inside the keyguard misses them, so it asks
 * here how they would have dimmed it ([dim]) and how much of it they hide
 * ([hidden]).
 *
 * Found by id in the notification shade window; a scrim that is missing, or
 * whose fields have moved, darkens nothing.
 */
internal class KeyguardScrims(val root: View) {

    private val behind = scrim("scrim_behind")
    private val reveal = scrim("light_reveal_scrim")
    private val inFront = scrim("scrim_in_front")

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

    /**
     * How much of the photo the light reveal scrim hides, from 0 to 1. It is
     * black, and whatever is in front of the photo should go with the photo
     * rather than turn black over the clock.
     */
    fun hidden(): Float = reveal?.let(::revealDarkness)?.coerceIn(0f, 1f) ?: 0f

    /** How much of the photo a `LightRevealScrim` still hides, as a black of that opacity. */
    private fun revealDarkness(view: View): Float? {
        if (!view.isShown) return null
        val revealed = runCatching { view.field("revealAmount").getFloat(view) }.getOrNull() ?: return null
        return view.alpha * (1f - revealed)
    }

    private fun mainColor(drawable: Drawable): Int = drawable.field("mMainColor").getInt(drawable)

    private companion object {
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
