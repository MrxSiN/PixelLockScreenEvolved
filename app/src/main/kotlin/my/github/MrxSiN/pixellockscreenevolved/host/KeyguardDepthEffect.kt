package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.View

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The depth effect: the subject of the lock screen photo in front of the large
 * clock, as on iOS.
 *
 * SystemUI draws a still lock screen photo itself, so each time it draws one it
 * has not drawn before (after Apply, not the previews Wallpaper & style shows
 * while photos are browsed), the
 * photo is taken as drawn: stretched to the wallpaper surface. The window that
 * surface is in is scaled and scrolled on screen, and the scroll is followed. A photo not
 * seen before goes to the module's app for its subject mask
 * ([SubjectMaskClient]); a mask already found is read back from disk
 * ([DepthCutout]). While a new photo's mask is being found, and when it is
 * ready or given up, a notification says so ([DepthProgressNotice]). The subject
 * cut out of the photo is then shown in front of every large face ([DepthLayer]). All of it waits while the effect is off
 * ([DepthEffectSetting]): the latest photo is kept, and found only once the
 * effect is turned on.
 * Live wallpapers are drawn by their own apps and never reach this, so they
 * have no depth effect.
 */
internal class KeyguardDepthEffect(
    private val hooks: Hooks,
    private val modulePackage: String,
    private val logger: Logger,
) : HostPatch, TimeOcclusion {

    private val layers = Collections.synchronizedMap(WeakHashMap<ClockControllerAdapter, DepthLayer>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var crop: WallpaperPlacement.Crop? = null
    private var scrollX = 0f
    private var scrollY = 0.5f
    private var zoom = 1f
    private var zoomScales = WallpaperPlacement.ZoomScales(1f, 1f)
    private var enabled = false
    private var watching = false
    private var lockEngine = java.lang.ref.WeakReference<Any>(null)
    private var dimOf: (Any) -> Float = { 0f }
    private var waiting: Waiting? = null
    @Volatile
    private var seenKey: String? = null
    private var photoKey: String? = null
    private var cutout: Bitmap? = null
    private var client: SubjectMaskClient? = null
    private var notice: DepthProgressNotice? = null
    private var store: DepthCutout? = null

    /** Draws the subject in front of a clock SystemUI built, once there is one. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            val layer = DepthLayer(clock.largeFace, zoomScales) { lockEngine.get()?.let(dimOf) ?: 0f }
            layers[clock] = layer
            layer.setEnabled(enabled)
            layer.setScroll(scrollX, scrollY)
            layer.setZoom(zoom)
            crop?.let(layer::setCrop)
            layer.show(cutout)
        }
    }

    override fun eraseSubject(time: View, picture: Bitmap): Boolean =
        synchronized(layers) { layers.entries.toList() }
            .firstOrNull { (clock, _) -> clock.largeFace.timeView === time }
            ?.value?.eraseSubjectFrom(picture) ?: false

    override fun install(classLoader: ClassLoader) {
        val api = try {
            LockWallpaperApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Lock screen wallpaper not found; the depth effect is unavailable", error)
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
                synchronized(layers) { layers.values.toList() }.forEach { it.setZoom(value) }
            }
        }

        hooks.after(api.offsetsChanged) { engine, args ->
            if (!isLockScreen(api, requireNotNull(engine))) return@after
            val x = args[0] as Float
            val y = args[1] as Float
            main.post {
                scrollX = x
                scrollY = y
                synchronized(layers) { layers.values.toList() }.forEach { it.setScroll(x, y) }
            }
        }

        hooks.after(api.drawPhoto) { engine, args ->
            val owner = requireNotNull(engine)
            if (!isLockScreen(api, owner)) return@after
            val photo = args.firstOrNull() as? Bitmap ?: return@after
            val (width, height) = api.surfaceSize(owner) ?: return@after
            if (photo.isRecycled || width <= 0 || height <= 0) return@after
            // The same photo is redrawn as the screen wakes or the picker closes; only a new one is taken.
            val key = DepthCutout.keyOf(photo)
            if (key == seenKey) return@after
            seenKey = key
            // The engine may let the photo go right after drawing, so it is copied here, as drawn.
            val shown = Bitmap.createScaledBitmap(photo, width, height, true)
            val context = (owner as WallpaperService.Engine).displayContext ?: return@after
            lockEngine = java.lang.ref.WeakReference(owner)
            val screen = context.resources.displayMetrics
            val crop = api.crop(owner, context, screen.widthPixels, screen.heightPixels)
                ?.let { scaleCrop(it, photo.width, photo.height, width, height) }
                ?: WallpaperPlacement.Crop(0, 0, width, height)
            val engineZoom = api.zoom(owner)
            main.post {
                this.crop = crop
                zoom = engineZoom
                synchronized(layers) { layers.values.toList() }.forEach { it.setZoom(engineZoom) }
                synchronized(layers) { layers.values.toList() }.forEach { it.setCrop(crop) }
            }
            main.post {
                watch(context)
                if (enabled) {
                    process(context, shown, key)
                } else {
                    waiting?.photo?.recycle()
                    waiting = Waiting(context, shown, key)
                }
            }
        }
    }

    /** [crop], in a photo of [photoWidth] by [photoHeight], in the pixels of the same photo stretched to [width] by [height]. */
    private fun scaleCrop(crop: Rect, photoWidth: Int, photoHeight: Int, width: Int, height: Int): WallpaperPlacement.Crop {
        val sx = width.toFloat() / photoWidth
        val sy = height.toFloat() / photoHeight
        return WallpaperPlacement.Crop((crop.left * sx).toInt(), (crop.top * sy).toInt(), (crop.right * sx).toInt(), (crop.bottom * sy).toInt())
    }

    /** A photo taken while the effect is off, kept until it is turned on. */
    private class Waiting(val context: Context, val photo: Bitmap, val key: String)

    /**
     * Whether [engine] draws the real lock screen, not one of the previews
     * Wallpaper & style shows with the same engine while photos are browsed.
     */
    private fun isLockScreen(api: LockWallpaperApi, engine: Any): Boolean =
        !(engine as WallpaperService.Engine).isPreview && api.drawsLockScreen(engine)

    /** Follows [DepthEffectSetting], once; turning the effect on finds the cut-out for the photo that waited. */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        DepthEffectSetting.watch(context.contentResolver) { on ->
            enabled = on
            synchronized(layers) { layers.values.toList() }.forEach { it.setEnabled(on) }
            if (on) waiting?.let {
                waiting = null
                process(it.context, it.photo, it.key)
            }
        }
    }

    private fun process(context: Context, photo: Bitmap, key: String) {
        worker.execute { runCatching { onPhoto(context, photo, key) }.onFailure { logger.warn("Depth effect failed", it) } }
    }

    /** On the worker: finds the cut-out for [photo], which [key] names, and shows it, unless it is the photo already shown. */
    private fun onPhoto(context: Context, photo: Bitmap, key: String) {
        val cutouts = store ?: DepthCutout(context.filesDir).also { store = it }
        if (key == photoKey) {
            photo.recycle()
            return
        }
        photoKey = key

        val stored = cutouts.storedMask(key)
        if (stored != null) {
            publish(key, cutouts.cutOut(photo, stored))
            return
        }

        val small = cutouts.forSegmentation(photo)
        main.post {
            val appContext = context.applicationContext ?: context
            val masks = client ?: SubjectMaskClient(appContext, modulePackage, logger).also { client = it }
            val progress = notice ?: DepthProgressNotice(appContext, modulePackage, logger).also { notice = it }
            progress.finding()
            masks.request(
                small,
                onMask = { mask ->
                    worker.execute {
                        runCatching {
                            cutouts.storeMask(key, mask)
                            publish(key, cutouts.cutOut(photo, mask))
                            progress.ready()
                        }.onFailure {
                            logger.warn("Depth effect failed", it)
                            giveUp(key, progress)
                        }
                    }
                },
                onFailed = { giveUp(key, progress) },
            )
        }
    }

    /**
     * Says the photo [key] names has no depth effect, and forgets it was seen, so
     * the next time the lock screen draws it its subject is looked for again.
     */
    private fun giveUp(key: String, progress: DepthProgressNotice) {
        main.post {
            if (key != photoKey) return@post
            progress.failed()
            seenKey = null
            worker.execute { if (photoKey == key) photoKey = null }
        }
    }

    private fun publish(key: String, picture: Bitmap) {
        main.post {
            if (key != photoKey) return@post
            cutout = picture
            synchronized(layers) { layers.values.toList() }.forEach { it.show(picture) }
            logger.info("Depth effect ready for the lock screen photo")
        }
    }
}
