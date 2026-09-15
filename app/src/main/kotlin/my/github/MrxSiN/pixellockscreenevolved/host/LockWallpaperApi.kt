package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.view.SurfaceHolder

import java.lang.reflect.Method

/**
 * SystemUI's still-image wallpaper, which draws the lock screen photo in
 * SystemUI's own process. The photo fills a surface as big as the photo; the
 * window manager scales it so the photo's crop for the screen fills the
 * screen, and slides it within that crop by the wallpaper's scroll.
 * HOOK_NOTES.md records the shapes.
 */
internal class LockWallpaperApi(classLoader: ClassLoader) {

    val engineType: Class<*> = Class.forName("com.android.systemui.wallpapers.ImageWallpaper\$CanvasEngine", false, classLoader)

    /** Draws a loaded photo, stretched over the whole wallpaper surface; its one argument is the photo. */
    val drawPhoto: Method = engineType.getDeclaredMethod("drawFrameOnCanvas", android.graphics.Bitmap::class.java)

    /**
     * Tells the engine how far the wallpaper is scrolled; its first two
     * arguments are that scroll, from 0 to 1 across and down the wallpaper.
     */
    val offsetsChanged: Method = engineType.getDeclaredMethod(
        "onOffsetsChanged",
        Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    )

    /** Tells an engine the wallpaper's zoom, from 0 zoomed in to 1 zoomed out; its one argument is the zoom. */
    val zoomChanged: Method = Class.forName("android.service.wallpaper.WallpaperService\$Engine")
        .getDeclaredMethod("onZoomChanged", Float::class.javaPrimitiveType)

    private val zoomOf = Class.forName("android.service.wallpaper.WallpaperService\$Engine").getDeclaredMethod("getZoom")
        .apply { isAccessible = true }

    private val dimAmount = Class.forName("android.service.wallpaper.WallpaperService\$Engine").getDeclaredField("mWallpaperDimAmount")
        .apply { isAccessible = true }

    /** How much an engine's wallpaper is dimmed, as by battery saver or dark theme, from 0 to 1. */
    fun dim(engine: Any): Float = runCatching { dimAmount.getFloat(engine) }.getOrDefault(0f)

    /** The zoom an engine last heard. */
    fun zoom(engine: Any): Float = runCatching { zoomOf.invoke(engine) as Float }.getOrDefault(1f)

    /** The least and most the window manager scales a wallpaper as it zooms, from the platform's configuration. */
    fun zoomScales(): WallpaperPlacement.ZoomScales {
        val system = android.content.res.Resources.getSystem()
        fun scale(name: String, fallback: Float): Float =
            system.getIdentifier(name, "dimen", "android").takeIf { it != 0 }?.let(system::getFloat) ?: fallback
        return WallpaperPlacement.ZoomScales(scale("config_wallpaperMinScale", 1f), scale("config_wallpaperMaxScale", 1f))
    }

    private val surfaceHolder = engineType.getDeclaredField("mSurfaceHolder").apply { isAccessible = true }
    private val wallpaperFlags = Class.forName("android.service.wallpaper.WallpaperService\$Engine")
        .getMethod("getWallpaperFlags")

    /** Whether an engine draws the lock screen, alone or shared with the home screen. */
    fun drawsLockScreen(engine: Any): Boolean = (wallpaperFlags.invoke(engine) as Int) and FLAG_LOCK != 0

    /** The width and height of the surface an engine draws its photo over. */
    fun surfaceSize(engine: Any): Pair<Int, Int>? {
        val frame = (surfaceHolder.get(engine) as SurfaceHolder?)?.surfaceFrame ?: return null
        return frame.width() to frame.height()
    }

    /**
     * The part of the photo an engine draws that is shown on a screen of
     * [width] by [height], in the photo's pixels, as the window manager crops
     * it, with the room the wallpaper scrolls across; null where the platform
     * has no such crops, when the whole photo is shown. A lock screen that
     * shares the home screen's photo keeps its crops with the home screen's.
     */
    fun crop(engine: Any, context: Context, width: Int, height: Int): Rect? = runCatching {
        val which = if ((wallpaperFlags.invoke(engine) as Int) and FLAG_SYSTEM != 0) FLAG_SYSTEM else FLAG_LOCK
        val crops = WallpaperManager::class.java
            .getMethod("getBitmapCrops", List::class.java, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(WallpaperManager.getInstance(context), listOf(Point(width, height)), which, false) as List<*>?
        crops?.firstOrNull() as Rect?
    }.getOrNull()

    private companion object {
        /** `WallpaperManager.FLAG_SYSTEM`. */
        const val FLAG_SYSTEM = 1

        /** `WallpaperManager.FLAG_LOCK`. */
        const val FLAG_LOCK = 2
    }
}
