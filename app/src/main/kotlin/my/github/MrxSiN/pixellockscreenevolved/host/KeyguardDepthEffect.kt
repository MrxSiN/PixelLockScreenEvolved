package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The depth effect: the subject of the lock screen photo in front of the large
 * clock, as on iOS.
 *
 * Each new lock screen photo ([LockWallpaperFeed]) not seen before goes to the
 * module's app for its subject mask ([SubjectMaskClient]); a mask already found
 * is read back from disk ([DepthCutout]). While a new photo's mask is being
 * found, and when it is ready or given up, a notification says so
 * ([DepthProgressNotice]). The subject cut out of the photo is then shown in
 * front of every large face ([DepthLayer]), except in a preview of another
 * wallpaper ([KeyguardPreviewWallpapers]). All of it waits while the effect is
 * off ([DepthEffectSetting]): the latest photo is kept, and found only once the
 * effect is turned on. Whatever else draws the subject is told of it while the
 * effect is on ([addSubjectListener]).
 */
internal class KeyguardDepthEffect(
    private val feed: LockWallpaperFeed,
    private val modulePackage: String,
    private val previews: KeyguardPreviewWallpapers,
    private val aodWallpaper: KeyguardAodWallpaper,
    private val logger: Logger,
) : LockWallpaperFeed.Listener, TimeOcclusion {

    private val layers = Collections.synchronizedMap(WeakHashMap<ClockControllerAdapter, DepthLayer>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var enabled = false
    private var watching = false
    private var waiting: Waiting? = null
    private var photoKey: String? = null
    private var cutout: Bitmap? = null
    private var client: SubjectMaskClient? = null
    private var notice: DepthProgressNotice? = null
    private var store: DepthCutout? = null
    private var context: Context? = null
    private val subjectListeners = CopyOnWriteArrayList<(Bitmap?, Context) -> Unit>()

    /**
     * Tells [listener], on the main thread, of the subject's cut-out whenever it or the effect's
     * setting changes: the cut-out while the effect is on, null while it is off or there is none.
     */
    fun addSubjectListener(listener: (Bitmap?, Context) -> Unit) {
        subjectListeners += listener
    }

    private fun tellSubject() {
        val at = context ?: return
        val shown = cutout.takeIf { enabled }
        subjectListeners.forEach { it(shown, at) }
    }

    /** Draws the subject in front of a clock SystemUI built, once there is one. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            val layer = DepthLayer(
                face = clock.largeFace,
                feed = feed,
                overOtherWallpaper = { previews.showsOtherWallpaper(clock.controller) },
                aodShowsWallpaper = aodWallpaper::isShown,
            )
            layers[clock] = layer
            layer.setEnabled(enabled)
            layer.show(cutout)
        }
    }

    override fun eraseSubject(time: View, picture: Bitmap): Boolean = layerOver(time)?.eraseSubjectFrom(picture) ?: false

    override fun stateOf(time: View): Any? = layerOver(time)?.occlusionState()

    /** The layer drawn in front of [time], if one is. */
    private fun layerOver(time: View): DepthLayer? =
        synchronized(layers) { layers.entries.toList() }
            .firstOrNull { (clock, _) -> clock.largeFace.timeView === time }
            ?.value

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        // Found at the surface's size, the size the layer is placed at.
        val shown = Bitmap.createScaledBitmap(photo, surface.width, surface.height, true)
        main.post {
            this.context = context
            watch(context)
            if (enabled) {
                process(context, shown, key)
            } else {
                waiting?.photo?.recycle()
                waiting = Waiting(context, shown, key)
            }
        }
    }

    override fun onPlacementChanged() {
        synchronized(layers) { layers.values.toList() }.forEach(DepthLayer::invalidate)
    }

    /** A photo taken while the effect is off, kept until it is turned on. */
    private class Waiting(val context: Context, val photo: Bitmap, val key: String)

    /** Follows [DepthEffectSetting], once; turning the effect on finds the cut-out for the photo that waited. */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        DepthEffectSetting.watch(context.contentResolver) { on ->
            enabled = on
            synchronized(layers) { layers.values.toList() }.forEach { it.setEnabled(on) }
            tellSubject()
            if (on) waiting?.let {
                waiting = null
                process(it.context, it.photo, it.key)
            }
        }
    }

    private fun process(context: Context, photo: Bitmap, key: String) {
        worker.execute { runCatching { findCutout(context, photo, key) }.onFailure { logger.warn("Depth effect failed", it) } }
    }

    /** On the worker: finds the cut-out for [photo], which [key] names, and shows it, unless it is the photo already shown. */
    private fun findCutout(context: Context, photo: Bitmap, key: String) {
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
            feed.forgetPhoto()
            worker.execute { if (photoKey == key) photoKey = null }
        }
    }

    private fun publish(key: String, picture: Bitmap) {
        main.post {
            if (key != photoKey) return@post
            cutout = picture
            synchronized(layers) { layers.values.toList() }.forEach { it.show(picture) }
            tellSubject()
            logger.info("Depth effect ready for the lock screen photo")
        }
    }
}
