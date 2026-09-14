package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pictures the depth effect is made of: the photo as small as segmentation
 * needs, a key that tells one photo from another, the subject cut out of the
 * photo, and the masks already found, kept on disk so a restart of SystemUI
 * does not segment the same photo again.
 */
internal class DepthCutout(cacheDirectory: File) {

    private val masks = File(cacheDirectory, MASK_DIRECTORY)

    /** [photo] scaled so its longer side is at most [SEGMENTATION_SIDE]. */
    fun forSegmentation(photo: Bitmap): Bitmap {
        val scale = SEGMENTATION_SIDE.toFloat() / max(photo.width, photo.height)
        if (scale >= 1f) return photo.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(photo, (photo.width * scale).roundToInt(), (photo.height * scale).roundToInt(), true)
    }

    /**
     * The subject of [photo]: its pixels, as opaque as [mask] stretched over
     * the photo is. The alpha is set pixel by pixel; drawn with a transfer
     * mode, an alpha-only bitmap acts as coverage and would leave the pixels
     * outside the subject untouched.
     */
    fun cutOut(photo: Bitmap, mask: Bitmap): Bitmap {
        val width = photo.width
        val height = photo.height
        val stretched = Bitmap.createScaledBitmap(mask, width, height, true)
        val alpha = ByteBuffer.allocate(width * height).also(stretched::copyPixelsToBuffer).array()
        if (stretched !== mask) stretched.recycle()

        val pixels = IntArray(width * height).also { photo.getPixels(it, 0, width, 0, 0, width, height) }
        for (i in pixels.indices) {
            pixels[i] = ((alpha[i].toInt() and 0xff) shl 24) or (pixels[i] and 0xffffff)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** The mask stored for the photo [key] names, or null. */
    fun storedMask(key: String): Bitmap? = runCatching {
        DataInputStream(File(masks, "$key$MASK_SUFFIX").inputStream().buffered()).use { input ->
            val width = input.readInt()
            val height = input.readInt()
            val alpha = ByteArray(width * height).also(input::readFully)
            Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply { copyPixelsFromBuffer(ByteBuffer.wrap(alpha)) }
        }
    }.getOrNull()

    /**
     * Stores [mask] for the photo [key] names, keeping only the few most recent
     * masks. Its alpha is written as it is, since Android cannot encode an
     * `ALPHA_8` bitmap as an image file.
     */
    fun storeMask(key: String, mask: Bitmap) {
        masks.mkdirs()
        masks.listFiles()?.sortedByDescending(File::lastModified)?.drop(KEPT_MASKS - 1)?.forEach(File::delete)
        val alpha = ByteBuffer.allocate(mask.width * mask.height).also(mask::copyPixelsToBuffer).array()
        DataOutputStream(File(masks, "$key$MASK_SUFFIX").outputStream().buffered()).use { output ->
            output.writeInt(mask.width)
            output.writeInt(mask.height)
            output.write(alpha)
        }
    }

    companion object {
        private const val MASK_DIRECTORY = "pixel_lock_screen_evolved_depth"
        private const val MASK_SUFFIX = ".mask"
        private const val KEY_SAMPLE = 32

        /** Masks kept on disk, so going back to a recent photo needs no new segmentation. */
        private const val KEPT_MASKS = 4

        /** Long side segmentation works at; the mask is stretched back over the photo. */
        private const val SEGMENTATION_SIDE = 1024

        /** A key for [photo]: its size and a checksum of a coarse sample of its pixels. */
        fun keyOf(photo: Bitmap): String {
            val sample = Bitmap.createScaledBitmap(photo, KEY_SAMPLE, KEY_SAMPLE, true)
            val pixels = IntArray(KEY_SAMPLE * KEY_SAMPLE).also { sample.getPixels(it, 0, KEY_SAMPLE, 0, 0, KEY_SAMPLE, KEY_SAMPLE) }
            if (sample !== photo) sample.recycle()
            val crc = CRC32()
            pixels.forEach { crc.update(it) }
            return "${photo.width}x${photo.height}-${crc.value.toString(16)}"
        }

        private fun CRC32.update(value: Int) {
            update(value ushr 24)
            update(value ushr 16)
            update(value ushr 8)
            update(value)
        }
    }
}
