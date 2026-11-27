package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.BroadcastOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.View

import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The depth effect on a photo Wallpaper & style previews before it is set.
 *
 * The picker draws that photo itself, in an image view of its own its preview
 * binders all hand it to (`SubsamplingScaleImageView.setImage`), under
 * SystemUI's preview of the clock, and SystemUI only
 * ever hears of its colours ([KeyguardPreviewWallpapers]). So the photo is taken
 * here as the picker shows it, once it has been drawn, and handed to SystemUI
 * ([DepthPreviewBroadcast]), which finds its subject and erases it from the
 * preview's clock as it does on the lock screen. Only the screen that
 * previews a photo before it is set is looked at: elsewhere the picker shows
 * the wallpaper already set, whose subject SystemUI has. Nothing is done while
 * the depth effect is off, and a photo already sent is not sent again.
 */
internal class PickerDepthPreview(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val main = Handler(Looper.getMainLooper())

    /** Made only once a preview is first handed over, so a picker never previewing a photo starts no thread. */
    private val background by lazy { java.util.concurrent.Executors.newSingleThreadExecutor() }
    private var sentKey: String? = null

    override fun install(classLoader: ClassLoader) {
        val setImage = runCatching { setImage(classLoader) }
            .onFailure { logger.warn("Wallpaper preview binder not found; previews show no depth effect", it) }
            .getOrNull() ?: return
        hooks.after(setImage) { view, _ ->
            val image = view as? View ?: return@after
            if (!DepthEffectSetting.isOn(image.context.contentResolver)) return@after
            if (!inNewPhotoPreview(image)) return@after
            capture(image, attempt = 0)
        }
    }

    /**
     * Takes the photo [image] shows once it has loaded: until then it draws nothing, so it is drawn
     * small and looked at, a few times, a little apart.
     */
    private fun capture(image: View, attempt: Int) {
        main.postDelayed({
            val photo = runCatching { drawn(image) }.onFailure { logger.warn("The previewed photo could not be read", it) }.getOrNull()
            when {
                photo != null -> send(image, photo)
                attempt + 1 < ATTEMPTS -> capture(image, attempt + 1)
            }
        }, WAIT_MS)
    }

    /** [image] drawn at the size the subject is found at, or null while it shows nothing yet. */
    private fun drawn(image: View): Bitmap? {
        if (image.width <= 0 || image.height <= 0) return null
        val scale = minOf(1f, LONG_SIDE.toFloat() / max(image.width, image.height))
        val photo = Bitmap.createBitmap((image.width * scale).roundToInt(), (image.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        Canvas(photo).apply { scale(scale, scale) }.also(image::draw)
        if (isBlank(photo)) {
            photo.recycle()
            return null
        }
        return photo
    }

    /**
     * Keys, packs and hands [photo] to SystemUI off the main thread: the JPEG alone takes tens of
     * milliseconds at this size. [sentKey] is only touched on that thread.
     */
    private fun send(image: View, photo: Bitmap) {
        val context = image.context.applicationContext ?: image.context
        background.execute {
            val key = DepthCutout.keyOf(photo)
            if (key == sentKey) return@execute photo.recycle()
            sentKey = key
            val bytes = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
            photo.recycle()
            val intent = Intent(DepthPreviewBroadcast.ACTION)
                .setPackage(DepthPreviewBroadcast.RECEIVER)
                .putExtra(DepthPreviewBroadcast.EXTRA_PHOTO, bytes)
            // Shares who sent it, so SystemUI can hear Wallpaper & style alone.
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            runCatching { context.sendBroadcast(intent, null, options) }
                .onSuccess { logger.info("Handed the previewed wallpaper to SystemUI for its subject") }
                .onFailure {
                    sentKey = null
                    logger.warn("The previewed wallpaper could not be handed to SystemUI", it)
                }
        }
    }

    private companion object {
        const val IMAGE_VIEW = "com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView"
        const val IMAGE_SOURCE = "com.davemorrissey.labs.subscaleview.ImageSource"
        const val NEW_PHOTO_PREVIEW = "com.android.wallpaper.picker.preview.ui.WallpaperPreviewActivity"

        /** The long side the photo is taken at: what the subject is found at anyway. */
        const val LONG_SIDE = 1024

        /** Small enough for a broadcast at that size, sharp enough for the model. */
        const val JPEG_QUALITY = 88
        const val ATTEMPTS = 8
        const val WAIT_MS = 400L

        /** The image view's call that hands it a photo to show. */
        fun setImage(classLoader: ClassLoader): Method =
            classLoader.hostClass(IMAGE_VIEW).getDeclaredMethod("setImage", classLoader.hostClass(IMAGE_SOURCE))

        /**
         * Whether [view] is in the screen that previews a photo before it is set. The previews are
         * drawn in a surface of their own, whose views hold no activity, so the picker's own task is
         * asked which of its screens is on top.
         */
        fun inNewPhotoPreview(view: View): Boolean {
            var context: android.content.Context? = view.context
            while (context is android.content.ContextWrapper) {
                if (context is android.app.Activity) return context.javaClass.name == NEW_PHOTO_PREVIEW
                context = context.baseContext
            }
            val tasks = view.context.getSystemService(android.app.ActivityManager::class.java)?.appTasks.orEmpty()
            return tasks.any { runCatching { it.taskInfo?.topActivity?.className == NEW_PHOTO_PREVIEW }.getOrDefault(false) }
        }

        /** Whether [photo] is one flat colour, as the view draws before its image has loaded. */
        fun isBlank(photo: Bitmap): Boolean {
            val first = photo.getPixel(0, 0)
            val stepX = max(1, photo.width / SAMPLES)
            val stepY = max(1, photo.height / SAMPLES)
            for (y in 0 until photo.height step stepY) for (x in 0 until photo.width step stepX) if (photo.getPixel(x, y) != first) return false
            return true
        }

        const val SAMPLES = 16
    }
}
