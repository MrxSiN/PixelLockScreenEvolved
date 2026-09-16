package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The lock screen photo on the always-on display, as on iOS, as Wallpaper &
 * style's switches choose ([AodWallpaperStyle]), behind every lock screen clock
 * ([AodWallpaperLayer]). As dots, where the depth effect has found the photo's
 * subject ([onSubject]), only the subject is drawn, lighting fewer pixels still.
 *
 * Only small copies are kept, on the GPU: half the screen's size is plenty at
 * the always-on display's brightness. The photo's is kept whatever the style,
 * about 5MB, since SystemUI draws the photo only once, often before the
 * settings have been read, and not again until the photo changes.
 */
internal class KeyguardAodWallpaper(private val feed: LockWallpaperFeed) : LockWallpaperFeed.Listener {

    private val layers = Collections.synchronizedMap(WeakHashMap<ClockControllerAdapter, AodWallpaperLayer>())
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    /** The photo's copy and the size of the surface it is stretched to. */
    private class Photo(val picture: Bitmap, val surface: WallpaperPlacement.Size)

    private var photo: Photo? = null
    private var subject: Bitmap? = null
    private var style: AodWallpaperStyle? = null
    private var watching = false

    /** Draws the photo behind a clock SystemUI built, on the always-on display. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            watch(clock.largeFace.view.context)
            layers[clock] = AodWallpaperLayer(clock, feed, ::look)
        }
    }

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        val copy = smallGpuCopy(photo, context)
        main.post {
            // The old copy is left to the garbage collector: a frame being drawn may still hold it.
            this.photo = Photo(copy, surface)
            invalidateAll()
        }
    }

    override fun onPlacementChanged() = invalidateAll()

    /**
     * The depth effect's cut-out of the photo's subject, as big as the wallpaper
     * surface, or null where there is none or the effect is off. Called on the main thread.
     */
    fun onSubject(cutout: Bitmap?, context: Context) {
        if (cutout == null) {
            subject = null
            invalidateAll()
            return
        }
        worker.execute {
            val copy = smallGpuCopy(cutout, context)
            main.post {
                subject = copy
                invalidateAll()
            }
        }
    }

    /** What the always-on display draws the photo as now, or null while it draws black. */
    fun look(): AodWallpaperLook? {
        val current = photo ?: return null
        val chosen = style ?: return null
        return AodWallpaperLook(current.picture, current.surface, chosen, subject.takeIf { chosen == AodWallpaperStyle.DOTS })
    }

    /** [picture] at half the screen's longest side, or smaller, on the GPU. */
    private fun smallGpuCopy(picture: Bitmap, context: Context): Bitmap {
        val longest = context.resources.displayMetrics.let { max(it.widthPixels, it.heightPixels) } * SCREEN_SHARE
        val scale = min(1f, longest / max(picture.width, picture.height))
        val small = Bitmap.createScaledBitmap(picture, (picture.width * scale).roundToInt(), (picture.height * scale).roundToInt(), true)
        val onGpu = small.copy(Bitmap.Config.HARDWARE, false) ?: return small
        if (small !== picture) small.recycle()
        return onGpu
    }

    /** Follows the switches, once. */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        val resolver: ContentResolver = context.contentResolver
        val follow = {
            style = AodWallpaperStyle.chosen(resolver)
            invalidateAll()
        }
        listOf(AodWallpaperSetting, AodDotsSetting, AodBlackAndWhiteSetting).forEach { it.observe(resolver, follow) }
    }

    private fun invalidateAll() {
        synchronized(layers) { layers.values.toList() }.forEach(AodWallpaperLayer::invalidate)
    }

    private companion object {
        /** The copies' longest side, against the screen's. */
        const val SCREEN_SHARE = 0.5f
    }
}
