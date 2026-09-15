package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The still lock screen photo SystemUI draws, and where the window manager puts
 * it on screen, for whatever draws with it over the lock screen ([Listener]):
 * the depth effect and the wallpaper on the always-on display.
 *
 * SystemUI draws a still lock screen photo itself, stretched to the wallpaper
 * surface. Each time it draws one it has not drawn before (after Apply, not the
 * previews Wallpaper & style shows while photos are browsed), the photo is
 * handed on as drawn. The window that surface is in is cropped, scrolled and
 * zoomed on screen, which is followed here, on the main thread ([place]). Live
 * wallpapers are drawn by their own apps and never reach this.
 */
internal class LockWallpaperFeed(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    interface Listener {
        /**
         * A lock screen photo not handed on before, the engine's own, which it may let
         * go once this returns, so what is kept must be copied now; [surface] is the
         * size it is stretched to, [key] tells it from other photos. Called on the
         * wallpaper's drawing thread.
         */
        fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) = Unit

        /** Where the photo is placed on screen, or how dimmed, has changed. Called on the main thread. */
        fun onPlacementChanged() = Unit
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var seenKey: String? = null
    private var engine = WeakReference<Any>(null)
    private var dimOf: (Any) -> Float = { 0f }

    private var crop: WallpaperPlacement.Crop? = null
    private var scroll = WallpaperPlacement.Scroll(0f, 0.5f)
    private var zoom = 1f
    private var zoomScales = WallpaperPlacement.ZoomScales(1f, 1f)

    /** The crop and scroll the placement follows, for telling one placement from another; on the main thread. */
    val placementKey: Any get() = crop to scroll

    fun addListener(listener: Listener) {
        listeners += listener
    }

    /** Where a photo stretched to [surface] is on [screen] now; on the main thread. */
    fun place(screen: WallpaperPlacement.Size, surface: WallpaperPlacement.Size): WallpaperPlacement.Placed =
        WallpaperPlacement.place(
            screen = screen,
            surface = surface,
            crop = crop ?: WallpaperPlacement.Crop(0, 0, surface.width, surface.height),
            scroll = scroll,
            zoom = zoom,
            zoomScales = zoomScales,
        )

    /** How much the wallpaper dims its own photo, as by battery saver or dark theme, from 0 to 1. */
    fun dim(): Float = engine.get()?.let(dimOf) ?: 0f

    /** Hands the current photo on again the next time SystemUI draws it, as for a listener that could not use it. */
    fun forgetPhoto() {
        seenKey = null
    }

    override fun install(classLoader: ClassLoader) {
        val api = try {
            LockWallpaperApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen wallpaper not found; nothing is drawn with the lock screen photo", error)
            return
        }

        zoomScales = api.zoomScales()
        dimOf = api::dim

        hooks.after(api.zoomChanged) { engine, args ->
            val owner = engine ?: return@after
            if (!api.engineType.isInstance(owner) || !isLockScreen(api, owner)) return@after
            val value = args[0] as Float
            main.post {
                zoom = value
                placementChanged()
            }
        }

        hooks.after(api.offsetsChanged) { engine, args ->
            if (!isLockScreen(api, requireNotNull(engine))) return@after
            val next = WallpaperPlacement.Scroll(args[0] as Float, args[1] as Float)
            main.post {
                scroll = next
                placementChanged()
            }
        }

        hooks.after(api.drawPhoto) { engine, args ->
            val owner = requireNotNull(engine)
            if (!isLockScreen(api, owner)) return@after
            val photo = args.firstOrNull() as? Bitmap ?: return@after
            val (width, height) = api.surfaceSize(owner) ?: return@after
            if (photo.isRecycled || width <= 0 || height <= 0) return@after
            // The same photo is redrawn as the screen wakes or the picker closes; only a new one is handed on.
            val key = DepthCutout.keyOf(photo)
            if (key == seenKey) return@after
            seenKey = key
            val context = (owner as WallpaperService.Engine).displayContext ?: return@after
            this.engine = WeakReference(owner)
            val screen = context.resources.displayMetrics
            val newCrop = api.crop(owner, context, screen.widthPixels, screen.heightPixels)
                ?.let { scaleCrop(it, photo.width, photo.height, width, height) }
                ?: WallpaperPlacement.Crop(0, 0, width, height)
            val engineZoom = api.zoom(owner)
            val surface = WallpaperPlacement.Size(width, height)
            listeners.forEach { it.onPhoto(photo, surface, key, context) }
            main.post {
                crop = newCrop
                zoom = engineZoom
                placementChanged()
            }
        }
    }

    private fun placementChanged() = listeners.forEach(Listener::onPlacementChanged)

    /** [crop], in a photo of [photoWidth] by [photoHeight], in the pixels of the same photo stretched to [width] by [height]. */
    private fun scaleCrop(crop: Rect, photoWidth: Int, photoHeight: Int, width: Int, height: Int): WallpaperPlacement.Crop {
        val sx = width.toFloat() / photoWidth
        val sy = height.toFloat() / photoHeight
        return WallpaperPlacement.Crop((crop.left * sx).toInt(), (crop.top * sy).toInt(), (crop.right * sx).toInt(), (crop.bottom * sy).toInt())
    }

    /**
     * Whether [engine] draws the real lock screen, not one of the previews
     * Wallpaper & style shows with the same engine while photos are browsed.
     */
    private fun isLockScreen(api: LockWallpaperApi, engine: Any): Boolean =
        !(engine as WallpaperService.Engine).isPreview && api.drawsLockScreen(engine)
}
