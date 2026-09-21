package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

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
 * effect is on ([addSubjectListener]), and a face that lays itself out around
 * the subject is told where it stands ([SubjectFocus]).
 *
 * Where it stands with the current photo is written for Wallpaper & style's
 * Depth effect page ([DepthStatus]). From there a person can ask for the
 * subject again ([DepthReprocessRequest]) when the cut-out came out wrong or
 * none was found: the mask kept for the photo is forgotten, and the photo is
 * looked at again, more carefully ([SubjectMaskClient.request]).
 *
 * The subject stays through an unlock. It is the photo showing through the
 * time rather than a copy of it ([DepthLayer]), so whatever the app coming up
 * behind the keyguard does to the wallpaper, a launcher blurring it, shows
 * through it too.
 */
internal class KeyguardDepthEffect(
    private val feed: LockWallpaperFeed,
    private val modulePackage: String,
    private val previews: KeyguardPreviewWallpapers,
    private val logger: Logger,
) : LockWallpaperFeed.Listener {

    private val shown = Collections.synchronizedMap(WeakHashMap<ClockControllerAdapter, Shown>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var enabled = false
    private var watching = false
    private var waiting: Waiting? = null

    /** The photo last looked at, kept so it can be looked at again when asked. */
    @Volatile private var latest: Waiting? = null
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
        val subject = cutout.takeIf { enabled }
        subjectListeners.forEach { it(subject, at) }
    }

    /**
     * What the subject does to one clock SystemUI built: it stands in front of
     * the clock's large face, and that face lays itself out around it where its
     * design says to.
     */
    private class Shown(val layer: DepthLayer, val focus: SubjectFocus) {

        fun setEnabled(on: Boolean) {
            layer.setEnabled(on)
            focus.setEnabled(on)
        }

        fun show(cutout: Bitmap?) {
            layer.show(cutout)
            focus.show(cutout)
        }

        fun invalidate() {
            layer.invalidate()
            focus.invalidate()
        }
    }

    /** Draws the subject in front of a clock SystemUI built, once there is one. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            val each = Shown(
                layer = DepthLayer(
                    face = clock.largeFace,
                    feed = feed,
                    overOtherWallpaper = { previews.showsOtherWallpaper(clock.controller) },
                ),
                focus = SubjectFocus(clock.largeFace, feed),
            )
            shown[clock] = each
            each.setEnabled(enabled)
            each.show(cutout)
        }
    }

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        // Found at the surface's size, the size the layer is placed at. Scaling hands the engine's own
        // photo straight back where it is already that size, and the engine may let it go the moment
        // this returns, so a copy is always taken: segmenting one takes seconds, and the photo was
        // recycled out from under the cut-out long before its mask came back.
        val scaled = Bitmap.createScaledBitmap(photo, surface.width, surface.height, true)
        val stretched = if (scaled !== photo) scaled else photo.copy(Bitmap.Config.ARGB_8888, false) ?: return
        main.post {
            this.context = context
            watch(context)
            if (enabled) {
                process(context, stretched, key)
            } else {
                waiting?.photo?.recycle()
                waiting = Waiting(context, stretched, key)
            }
        }
    }

    override fun onPlacementChanged() {
        each().forEach(Shown::invalidate)
    }

    private fun each(): List<Shown> = synchronized(shown) { shown.values.toList() }

    /** A photo taken while the effect is off, kept until it is turned on. */
    private class Waiting(val context: Context, val photo: Bitmap, val key: String)

    /**
     * Follows [DepthEffectSetting] and [DepthReprocessRequest], once; turning the effect on finds the cut-out
     * for the photo that waited.
     */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        DepthReprocessRequest.listen(context.contentResolver) { reprocess(context) }
        DepthEffectSetting.watch(context.contentResolver) { on ->
            enabled = on
            each().forEach { it.setEnabled(on) }
            tellSubject()
            if (on) waiting?.let {
                waiting = null
                process(it.context, it.photo, it.key)
            }
        }
    }

    private fun process(context: Context, photo: Bitmap, key: String, careful: Boolean = false) {
        worker.execute { runCatching { findCutout(context, photo, key, careful) }.onFailure { logger.warn("Depth effect failed", it) } }
    }

    /**
     * Forgets the mask kept for the photo last looked at and looks at it again, carefully. With no photo
     * looked at yet, the next one SystemUI draws is handed on again. Called on the main thread.
     */
    private fun reprocess(context: Context) {
        if (!enabled) return
        val last = latest ?: return feed.forgetPhoto()
        logger.info("Depth effect asked to find the subject again")
        worker.execute {
            cutouts(context).forgetMask(last.key)
            photoKey = null
        }
        process(last.context, last.photo, last.key, careful = true)
    }

    private fun cutouts(context: Context): DepthCutout = store ?: DepthCutout(context.filesDir).also { store = it }

    /**
     * On the worker: finds the cut-out for [photo], which [key] names, and shows it, unless it is the photo
     * already shown. A [careful] look skips the mask kept for it.
     */
    private fun findCutout(context: Context, photo: Bitmap, key: String, careful: Boolean) {
        val cutouts = cutouts(context)
        if (key == photoKey) {
            if (photo !== latest?.photo) photo.recycle()
            return
        }
        photoKey = key
        latest = Waiting(context, photo, key)

        val stored = cutouts.storedMask(key).takeUnless { careful }
        if (stored != null) {
            publish(key, cutouts.cutOut(photo, stored))
            report(context, DepthStatus.READY)
            return
        }

        val small = cutouts.forSegmentation(photo)
        main.post {
            val appContext = context.applicationContext ?: context
            val masks = client ?: SubjectMaskClient(appContext, modulePackage, logger).also { client = it }
            val progress = notice ?: DepthProgressNotice(appContext, modulePackage, logger).also { notice = it }
            progress.finding()
            report(context, DepthStatus.FINDING)
            masks.request(
                small,
                careful,
                onMask = { mask ->
                    worker.execute {
                        runCatching {
                            cutouts.storeMask(key, mask)
                            publish(key, cutouts.cutOut(photo, mask))
                            progress.ready()
                            report(context, DepthStatus.READY)
                        }.onFailure {
                            logger.warn("Depth effect failed", it)
                            giveUp(key, progress, context)
                        }
                    }
                },
                onFailed = { giveUp(key, progress, context) },
            )
        }
    }

    /**
     * Says the photo [key] names has no depth effect, and forgets it was seen, so
     * the next time the lock screen draws it its subject is looked for again.
     */
    private fun giveUp(key: String, progress: DepthProgressNotice, context: Context) {
        main.post {
            if (key != photoKey) return@post
            progress.failed()
            report(context, DepthStatus.NO_SUBJECT)
            feed.forgetPhoto()
            worker.execute { if (photoKey == key) photoKey = null }
        }
    }

    /** Writes where the effect stands for Wallpaper & style's Depth effect page. */
    private fun report(context: Context, status: DepthStatus) {
        runCatching { DepthStatus.put(context.contentResolver, status) }.onFailure { logger.warn("Depth effect status could not be stored", it) }
    }

    private fun publish(key: String, picture: Bitmap) {
        main.post {
            if (key != photoKey) return@post
            cutout = picture
            each().forEach { it.show(picture) }
            tellSubject()
            logger.info("Depth effect ready for the lock screen photo")
        }
    }
}
