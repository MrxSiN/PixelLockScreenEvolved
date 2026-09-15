package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Which clocks SystemUI draws in a lock screen preview of a wallpaper other
 * than the lock screen's own, as Wallpaper & style shows one while a new photo
 * is looked at before it is set.
 *
 * The picker draws that photo itself, behind SystemUI's preview of the clock,
 * and names it to SystemUI only by its colours ([KeyguardPreviewApi]); a
 * preview whose colours are not the lock screen wallpaper's shows another
 * wallpaper. A clock never previewed is on the lock screen itself.
 */
internal class KeyguardPreviewWallpapers(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val otherWallpaper = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    /** Whether the clock SystemUI holds as [controller] is shown over a wallpaper other than the lock screen's. */
    fun showsOtherWallpaper(controller: Any): Boolean = otherWallpaper[controller] ?: false

    override fun install(classLoader: ClassLoader) {
        val api = try {
            KeyguardPreviewApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen preview renderer not found; a preview of a new wallpaper may show the current one's depth effect", error)
            return
        }

        hooks.before(api.clockAppearanceUpdated) { _, args ->
            val renderer = args[0] ?: return@before
            val controller = args[1] ?: return@before
            // The lock screen tab previews the wallpaper already set and names no colours.
            val shown = api.wallpaperColors(renderer)
            otherWallpaper[controller] = shown != null && shown != lockWallpaperColors(api.context(renderer))
        }
    }

    /** The lock screen wallpaper's colours: its own, or the home screen's when it shares that wallpaper. */
    private fun lockWallpaperColors(context: Context): WallpaperColors? = WallpaperManager.getInstance(context).run {
        getWallpaperColors(WallpaperManager.FLAG_LOCK) ?: getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
    }
}
