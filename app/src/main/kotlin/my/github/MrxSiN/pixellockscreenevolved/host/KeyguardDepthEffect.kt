package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
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
 * ([DepthProgressNotice]). The subject's matte is then shown in front of every
 * large face ([DepthLayer]), except in a preview of another wallpaper
 * ([KeyguardPreviewWallpapers]). All of it waits while the effect is off
 * ([DepthEffectSetting]): the latest photo is kept, and found only once the
 * effect is turned on. Whatever else draws the subject is told of it while the
 * effect is on ([addSubjectListener]).
 *
 * A new photo clears the last one's subject at once, so the old subject is
 * never erased from the time over a photo it does not belong to while the new
 * one is found. Which subjects stand in front ([DepthSubjects]) is applied to
 * the mask kept, without segmenting again.
 *
 * Where it stands with the current photo is written for Wallpaper & style's
 * Depth effect page ([DepthStatus]). From there a person can ask for the
 * subject again ([DepthReprocessRequest]) when the cut-out came out wrong or
 * none was found: the mask kept for the photo is forgotten, and the photo is
 * looked at again, more carefully ([SubjectMaskClient.request]).
 *
 * With the depth map ([DepthLayers.DEPTH]) the photo's depth decides what is
 * in front ([DepthComposite]); a person's own corrections, made in the
 * subject's editor ([DepthMaskEditor]), are laid over it. A clock of Google's
 * own ([trackStock]) gets the subject in front of it too, and a preview of a
 * photo not yet set shows that photo's subject, found here from the photo
 * Wallpaper & style hands over ([DepthPreviewBroadcast]).
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

    private val shown = Collections.synchronizedMap(WeakHashMap<Any, DepthLayer>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private var enabled = false
    private var watching = false
    private var waiting: Photo? = null

    /** The photo last looked at, at the size it is segmented at, kept so it can be looked at again when asked. */
    @Volatile private var latest: Photo? = null

    /** The photo whose subject is wanted; written on the worker, read on the main thread. */
    @Volatile private var photoKey: String? = null

    /** The photo whose subject is shown; on the main thread. */
    private var shownKey: String? = null
    private var cutout: Bitmap? = null
    private var luma: LumaGrid? = null
    private var client: SubjectMaskClient? = null
    private var previewClient: SubjectMaskClient? = null
    private var preview: Bitmap? = null
    private var previewKey: String? = null
    private var notice: DepthProgressNotice? = null
    private var store: DepthCutout? = null
    private var context: Context? = null
    private val subjectListeners = CopyOnWriteArrayList<(Bitmap?, Context) -> Unit>()

    /**
     * Tells [listener], on the main thread, of the subject's matte whenever it or the effect's
     * setting changes: the matte while the effect is on, null while it is off or there is none.
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
     * Draws the subject in front of a clock SystemUI built, once there is one: in front of both its
     * faces, the small one shown beside notifications as well as the large one.
     */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            for (face in listOf(clock.largeFace, clock.smallFace)) {
                add(face, DepthLayer(face, feed, overOtherWallpaper = { previews.showsOtherWallpaper(clock.controller) }))
            }
        }
    }

    /**
     * Draws the subject in front of a clock of Google's own SystemUI built, held as [controller]:
     * each of its faces' views, [faces], is all of that face ([StockClockTarget]).
     */
    fun trackStock(controller: Any, faces: List<View>) {
        main.post {
            for (view in faces) {
                if (shown.containsKey(view)) continue
                add(view, DepthLayer(StockClockTarget(view), feed, overOtherWallpaper = { previews.showsOtherWallpaper(controller) }))
            }
        }
    }

    private fun add(face: Any, layer: DepthLayer) {
        // Its settings, requests and previews are followed from the first clock, not only from the first
        // photo: after a hot reload no photo is handed on until the wallpaper changes.
        watch(layer.context)
        shown[face] = layer
        layer.setEnabled(enabled)
        layer.show(cutout, luma)
        layer.showPreview(preview)
    }

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        // Taken straight at the size it is segmented at, stretched as the surface stretches it: the
        // surface-sized copy SystemUI used to hold until the subject was found was four times the memory.
        // Scaling hands the engine's own photo straight back where it is already that size, and the engine
        // may let it go the moment this returns, so a copy is always taken.
        val size = DepthCutout.segmentationSize(surface)
        val scaled = Bitmap.createScaledBitmap(photo, size.width, size.height, true)
        val small = if (scaled !== photo) scaled else photo.copy(Bitmap.Config.ARGB_8888, false) ?: return
        main.post {
            this.context = context
            watch(context)
            if (key != shownKey) show(null, null, null)
            val taken = Photo(context, small, key, surface)
            if (enabled) {
                process(taken)
            } else {
                waiting?.photo?.recycle()
                waiting = taken
            }
        }
    }

    override fun onPlacementChanged() {
        each().forEach(DepthLayer::invalidate)
    }

    private fun each(): List<DepthLayer> = synchronized(shown) { shown.values.toList() }

    /** A photo at the size it is segmented at, [key] telling it from others, and the surface its subject is shown at. */
    private class Photo(val context: Context, val photo: Bitmap, val key: String, val surface: WallpaperPlacement.Size)

    /**
     * Follows the effect's settings, once: turning the effect on finds the cut-out for the photo that
     * waited, a reprocess request looks again, and choosing which subjects stand in front cuts again.
     */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        val resolver = context.contentResolver
        DepthReprocessRequest.listen(resolver) { reprocess(careful = true) }
        var laidOut = DepthSubjects.get(resolver) to DepthLayers.get(resolver)
        val layOutAgain = {
            val now = DepthSubjects.get(resolver) to DepthLayers.get(resolver)
            if (now != laidOut) {
                laidOut = now
                reprocess(careful = false)
            }
        }
        DepthSubjects.observe(resolver, layOutAgain)
        DepthLayers.observe(resolver, layOutAgain)
        DepthEditRequest.listen(resolver) { openEditor() }
        listenForPreviews(context)
        DepthOcclusion.observe(resolver) { each().forEach(DepthLayer::remeasure) }
        DepthEffectSetting.watch(resolver) { on ->
            enabled = on
            each().forEach { it.setEnabled(on) }
            tellSubject()
            if (on) waiting?.let {
                waiting = null
                process(it)
            }
        }
    }

    private fun process(photo: Photo, careful: Boolean = false) {
        worker.execute { runCatching { findCutout(photo, careful) }.onFailure { logger.warn("Depth effect failed", it) } }
    }

    /**
     * Cuts the subject of the photo last looked at out again: [careful]ly, forgetting the mask kept for
     * it, when a person asks for it to be found again, or from the mask kept, as when the subjects
     * chosen change. With no photo looked at yet, the next one SystemUI draws is handed on again.
     * Called on the main thread.
     */
    private fun reprocess(careful: Boolean) {
        if (!enabled) return
        val last = latest ?: return feed.forgetPhoto()
        if (careful) logger.info("Depth effect asked to find the subject again")
        worker.execute {
            runCatching {
                if (careful) cutouts(last.context).forgetMask(last.key)
                photoKey = null
                findCutout(last, careful)
            }.onFailure { logger.warn("Depth effect failed", it) }
        }
    }

    private fun cutouts(context: Context): DepthCutout = store ?: DepthCutout(context.filesDir).also { store = it }

    /**
     * On the worker: finds the matte for [taken] and shows it, unless its photo is the one already
     * wanted. A [careful] look skips the mask kept for it.
     */
    private fun findCutout(taken: Photo, careful: Boolean) {
        val context = taken.context
        val photo = taken.photo
        val key = taken.key
        // A newer photo took this one's place, and let it go, before an earlier ask for it came round.
        if (photo.isRecycled) return
        if (key == photoKey) {
            if (taken !== latest) photo.recycle()
            return
        }
        photoKey = key
        val previous = latest
        latest = taken
        // Only ever read on this worker, and the client sent its own copy, so the last photo can go now.
        if (previous != null && previous !== taken) previous.photo.recycle()
        val cutouts = cutouts(context)
        val grid = DepthCutout.lumaOf(photo)

        // The depth map is found only while the clock is laid out by it; a mask kept without one is looked
        // at again once it is wanted.
        val wantsDepth = DepthLayers.get(context.contentResolver) == DepthLayers.DEPTH
        val stored = cutouts.storedMask(key, WallpaperPlacement.Size(photo.width, photo.height)).takeUnless { careful }
        val storedDepth = if (stored != null && wantsDepth) cutouts.stored(key, DepthCutout.Kind.DEPTH) else null
        if (stored != null && (!wantsDepth || storedDepth != null)) {
            publish(key, matteOf(cutouts, taken, stored, storedDepth), grid)
            report(context, DepthStatus.READY)
            return
        }

        // The client parcels its photo while the worker may already be on the next one, so it gets its own.
        val sent = photo.copy(Bitmap.Config.ARGB_8888, false)
        main.post {
            val appContext = context.applicationContext ?: context
            val masks = client ?: SubjectMaskClient(appContext, modulePackage, logger).also { client = it }
            val progress = notice ?: DepthProgressNotice(appContext, modulePackage, logger).also { notice = it }
            progress.finding()
            report(context, DepthStatus.FINDING)
            masks.request(
                sent,
                careful,
                wantsDepth,
                onMask = { mask, depth ->
                    worker.execute {
                        runCatching {
                            cutouts.storeMask(key, mask)
                            depth?.let { cutouts.store(key, DepthCutout.Kind.DEPTH, it) }
                            publish(key, matteOf(cutouts, taken, mask, depth), grid)
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

    /** The matte for [taken] from its [mask] and [depth] map, laid out as the settings and the edits kept for it say. */
    private fun matteOf(cutouts: DepthCutout, taken: Photo, mask: Bitmap, depth: Bitmap?): Bitmap {
        val resolver = taken.context.contentResolver
        val size = WallpaperPlacement.Size(taken.photo.width, taken.photo.height)
        return cutouts.matte(
            mask, taken.surface,
            mainOnly = DepthSubjects.get(resolver) == DepthSubjects.MAIN,
            depth = depth.takeIf { DepthLayers.get(resolver) == DepthLayers.DEPTH },
            photo = taken.photo,
            edits = cutouts.stored(taken.key, DepthCutout.Kind.EDITS, size),
        )
    }

    /**
     * Opens the subject's editor ([DepthMaskEditor]) over whatever is on screen, for the photo last
     * looked at, once its subject is found. Called on the main thread.
     */
    private fun openEditor() {
        val last = latest ?: return logger.info("No lock screen photo to edit the subject of yet")
        // Asked from Wallpaper & style, which is used unlocked; a window opened over the keyguard would wait hidden.
        if (last.context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) {
            return logger.info("The subject editor opens only while the phone is unlocked")
        }
        val frame = lockScreenFrame(last)
        worker.execute {
            runCatching {
                if (last.photo.isRecycled) return@execute
                val cutouts = cutouts(last.context)
                val resolver = last.context.contentResolver
                val size = WallpaperPlacement.Size(last.photo.width, last.photo.height)
                val mask = cutouts.storedMask(last.key, size) ?: return@execute logger.info("The subject is not found yet; nothing to edit")
                val found = cutouts.composedAlpha(
                    mask,
                    DepthSubjects.get(resolver) == DepthSubjects.MAIN,
                    cutouts.stored(last.key, DepthCutout.Kind.DEPTH).takeIf { DepthLayers.get(resolver) == DepthLayers.DEPTH },
                    last.photo,
                )
                val edits = cutouts.stored(last.key, DepthCutout.Kind.EDITS, size)?.let { DepthCutout.alphaOf(it, size.width, size.height) }
                    ?: IntArray(size.width * size.height) { DepthComposite.UNEDITED }
                val photo = last.photo.copy(Bitmap.Config.ARGB_8888, false)
                main.post {
                    DepthMaskEditor(last.context, photo, found, edits, frame, logger) { saved -> saveEdits(last, saved) }.show()
                }
            }.onFailure { logger.warn("The subject editor could not open", it) }
        }
    }

    /**
     * The part of [taken]'s photo the lock screen shows, in the photo's pixels, as the wallpaper
     * rests there ([LockWallpaperFeed.place]), or null where it covers none of it. On the main thread.
     */
    private fun lockScreenFrame(taken: Photo): android.graphics.RectF? {
        val screen = taken.context.resources.displayMetrics
        val placed = feed.place(WallpaperPlacement.Size(screen.widthPixels, screen.heightPixels), taken.surface, zoomed = false)
        // A screen point back through the zoom and the surface's place, then from the surface to the photo.
        fun photoX(x: Float) = ((x - placed.pivotX) / placed.scale + placed.pivotX - placed.left) * taken.photo.width / placed.width
        fun photoY(y: Float) = ((y - placed.pivotY) / placed.scale + placed.pivotY - placed.top) * taken.photo.height / placed.height
        val frame = android.graphics.RectF(photoX(0f), photoY(0f), photoX(screen.widthPixels.toFloat()), photoY(screen.heightPixels.toFloat()))
        return if (frame.intersect(0f, 0f, taken.photo.width.toFloat(), taken.photo.height.toFloat())) frame else null
    }

    /** Keeps [edits] for [photo]'s subject, or forgets them where nothing is edited, and lays the subject out again. */
    private fun saveEdits(photo: Photo, edits: IntArray) {
        worker.execute {
            runCatching {
                val cutouts = cutouts(photo.context)
                if (edits.all { it == DepthComposite.UNEDITED }) {
                    cutouts.forget(photo.key, DepthCutout.Kind.EDITS)
                } else {
                    cutouts.store(photo.key, DepthCutout.Kind.EDITS, cutouts.stretch(edits, photo.photo.width, photo.photo.height, WallpaperPlacement.Size(photo.photo.width, photo.photo.height)))
                }
                logger.info("Depth effect subject edited")
            }.onFailure { logger.warn("The subject's edits could not be kept", it) }
            main.post { reprocess(careful = false) }
        }
    }

    /**
     * Hears the photos Wallpaper & style previews before they are set ([DepthPreviewBroadcast]):
     * only from a sender holding its permission, and only from the one Android names as Wallpaper &
     * style. Called on the main thread, once.
     */
    private fun listenForPreviews(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(received: Context, intent: Intent) {
                if (sentFromPackage != DepthPreviewBroadcast.SENDER) return logger.warn("Ignored a wallpaper preview from $sentFromPackage")
                val bytes = intent.getByteArrayExtra(DepthPreviewBroadcast.EXTRA_PHOTO) ?: return
                worker.execute {
                    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                        ?.let { photo -> main.post { findPreview(context, photo) } }
                }
            }
        }
        runCatching {
            context.registerReceiver(receiver, IntentFilter(DepthPreviewBroadcast.ACTION), DepthPreviewBroadcast.PERMISSION, main, Context.RECEIVER_EXPORTED)
        }.onFailure { logger.warn("Wallpaper previews cannot be heard; they show no depth effect", it) }
    }

    /** Finds the subject of [photo], previewed before it is set, for the preview's clock to stand behind. On the main thread. */
    private fun findPreview(context: Context, photo: Bitmap) {
        if (!enabled) return
        val key = DepthCutout.keyOf(photo)
        if (key == previewKey) return
        previewKey = key
        val appContext = context.applicationContext ?: context
        val masks = previewClient ?: SubjectMaskClient(appContext, modulePackage, logger).also { previewClient = it }
        logger.info("Finding the subject of a previewed wallpaper")
        val resolver = context.contentResolver
        masks.request(
            photo,
            careful = false,
            depth = DepthLayers.get(resolver) == DepthLayers.DEPTH,
            onMask = { mask, depth ->
                worker.execute {
                    runCatching {
                        val matte = cutouts(context).matte(
                            mask, WallpaperPlacement.Size(mask.width, mask.height),
                            mainOnly = DepthSubjects.get(resolver) == DepthSubjects.MAIN,
                            depth = depth.takeIf { DepthLayers.get(resolver) == DepthLayers.DEPTH },
                            photo = photo,
                        )
                        main.post {
                            if (previewKey != key) return@post
                            preview = matte
                            each().forEach { it.showPreview(matte) }
                            logger.info("Depth effect ready for the wallpaper preview")
                        }
                    }.onFailure { logger.warn("The preview's subject could not be shown", it) }
                }
            },
            onFailed = {
                if (previewKey == key) previewKey = null
                logger.info("No subject found for the wallpaper preview")
            },
        )
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

    private fun publish(key: String, matte: Bitmap, grid: LumaGrid) {
        main.post {
            // A newer photo or look was asked for meanwhile: this matte is already stale.
            if (key != photoKey) return@post
            show(key, matte, grid)
            logger.info("Depth effect ready for the lock screen photo")
        }
    }

    /** Shows [matte] for the photo [key] names, or none; on the main thread. */
    private fun show(key: String?, matte: Bitmap?, grid: LumaGrid?) {
        shownKey = key
        cutout = matte
        luma = grid
        each().forEach { it.show(matte, grid) }
        tellSubject()
    }
}
