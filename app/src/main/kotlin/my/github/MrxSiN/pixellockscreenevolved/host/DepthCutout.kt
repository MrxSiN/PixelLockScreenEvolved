package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.depth.GuidedFilter
import my.github.MrxSiN.pixellockscreenevolved.depth.SubjectMaskContract

/**
 * The pictures the depth effect is made of: the size segmentation works at, a
 * key that tells one photo from another, the subject's matte, and the masks
 * already found, kept on disk so a restart of SystemUI does not segment the
 * same photo again. It is made on the depth effect's worker, since it tidies
 * that directory.
 *
 * The matte is alpha alone (`ALPHA_8`): everything that uses the subject, the
 * clock's erasing ([DepthLayer]), the adaptive position ([SubjectInk]) and the
 * always-on display's dots, reads only how opaque it is. An `ARGB_8888`
 * cut-out at the wallpaper surface's size held four times the memory in
 * SystemUI for colour nothing drew.
 */
internal class DepthCutout(cacheDirectory: File) {

    private val masks = File(cacheDirectory, MASK_DIRECTORY)

    init {
        // Masks another model or format wrote are never read again, so their directories are removed.
        cacheDirectory.listFiles { file -> file.isDirectory && file.name.startsWith(MASK_DIRECTORY_PREFIX) && file != masks }
            ?.forEach(File::deleteRecursively)
        // A store cut short, by SystemUI dying mid-write, leaves only its temporary file.
        masks.listFiles { file -> file.name.endsWith(TEMPORARY_SUFFIX) }?.forEach(File::delete)
    }

    /**
     * The subject's matte, as big as [surface], from [mask]: the model's haze
     * cleared, only its main subject kept where [mainOnly] says, laid against
     * the photo's [depth] map where one is given ([DepthComposite.combine]) and
     * snapped to [photo]'s edges again, a person's [edits] laid over it, and
     * stretched over the surface. The model leaves a faint haze of alpha well
     * around the subject, which showed as a lighter or darker box around the
     * time, so alpha up to [HAZE] is dropped and the rest stretched back to
     * full. The work is done at the mask's size, which is [photo]'s, and
     * stretched once, so no surface-sized buffer is held besides the matte.
     */
    fun matte(
        mask: Bitmap,
        surface: WallpaperPlacement.Size,
        mainOnly: Boolean,
        depth: Bitmap? = null,
        photo: Bitmap? = null,
        edits: Bitmap? = null,
    ): Bitmap {
        val alpha = composedAlpha(mask, mainOnly, depth, photo)
        edits?.let { DepthComposite.applyEdits(alpha, alphaOf(it, mask.width, mask.height)) }
        return stretch(alpha, mask.width, mask.height, surface)
    }

    /** [mask]'s alpha as [matte] lays it out before any edits, one value from 0 to 255 per pixel at the mask's size. */
    fun composedAlpha(mask: Bitmap, mainOnly: Boolean, depth: Bitmap?, photo: Bitmap?): IntArray {
        val width = mask.width
        val height = mask.height
        val alpha = alphaOf(mask, width, height)
        for (i in alpha.indices) alpha[i] = (alpha[i] - HAZE).coerceAtLeast(0) * OPAQUE / (OPAQUE - HAZE)
        if (mainOnly) SubjectComponents.mainOnly(alpha, width, height)
        if (depth != null && photo != null && photo.width == width && photo.height == height) {
            DepthComposite.combine(alpha, alphaOf(depth, width, height))
            // The depth map is coarser than the mask; its edge is snapped to the photo's as the mask's was.
            // The guide is read a row at a time, so no photo-sized array of colours is held besides it.
            val guide = FloatArray(width * height)
            val row = IntArray(width)
            for (y in 0 until height) {
                photo.getPixels(row, 0, width, 0, y, width, 1)
                for (x in 0 until width) guide[y * width + x] = luma(row[x])
            }
            val radius = maxOf(2, maxOf(width, height) / GUIDE_DIVISOR)
            val refined = GuidedFilter.apply(guide, FloatArray(alpha.size) { alpha[it] / OPAQUE.toFloat() }, width, height, radius, GUIDE_EPSILON)
            for (i in alpha.indices) alpha[i] = (refined[i] * OPAQUE + 0.5f).toInt()
        }
        return alpha
    }

    /** [alpha], [width] by [height], as an `ALPHA_8` bitmap the size of [surface]; [alpha] is used up, its values shifted in place. */
    fun stretch(alpha: IntArray, width: Int, height: Int, surface: WallpaperPlacement.Size): Bitmap {
        for (i in alpha.indices) alpha[i] = alpha[i] shl ALPHA_SHIFT
        val small = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply { setPixels(alpha, 0, width, 0, 0, width, height) }
        return Bitmap.createScaledBitmap(small, surface.width, surface.height, true).also { if (it !== small) small.recycle() }
    }

    /** What is kept for a photo, each in a file of its own. */
    enum class Kind(val suffix: String) {
        /** The subject's mask, at the size the photo is segmented at. */
        MASK(".mask"),

        /** How near each pixel is, at the size the depth model worked at. */
        DEPTH(".depth"),

        /** A person's corrections to the mask ([DepthComposite.applyEdits]), at the mask's size. */
        EDITS(".edit"),
    }

    /**
     * The mask stored for the photo [key] names, or null where there is none,
     * or the file is not one this version wrote whole for a photo of [size].
     */
    fun storedMask(key: String, size: WallpaperPlacement.Size): Bitmap? = stored(key, Kind.MASK, size)

    /**
     * What is stored of [kind] for the photo [key] names, or null where there is none, or the file is
     * not one this version wrote whole, or not of [size] where one is asked for. A file read is marked
     * as just used, so what is kept is what was most recently shown, not most recently found.
     */
    fun stored(key: String, kind: Kind, size: WallpaperPlacement.Size? = null): Bitmap? = runCatching {
        val file = File(masks, "$key${kind.suffix}")
        if (!file.isFile) return null
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return null
            val width = input.readInt()
            val height = input.readInt()
            if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return null
            if (size != null && (width != size.width || height != size.height)) return null
            val alpha = ByteArray(width * height).also(input::readFully)
            if (input.readLong() != checksum(alpha)) return null
            file.setLastModified(System.currentTimeMillis())
            Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply {
                setPixels(IntArray(alpha.size) { (alpha[it].toInt() and BYTE) shl ALPHA_SHIFT }, 0, width, 0, 0, width, height)
            }
        }
    }.getOrNull()

    /** Forgets everything stored for the photo [key] names, so its subject is found again. */
    fun forgetMask(key: String) {
        Kind.entries.forEach { File(masks, "$key${it.suffix}").delete() }
    }

    /** Forgets what is stored of [kind] for the photo [key] names. */
    fun forget(key: String, kind: Kind) {
        File(masks, "$key${kind.suffix}").delete()
    }

    /** Stores [mask] for the photo [key] names ([store]). */
    fun storeMask(key: String, mask: Bitmap) = store(key, Kind.MASK, mask)

    /**
     * Stores [picture] as [kind] for the photo [key] names, keeping only the files of the few most
     * recent photos. Its alpha is written as it is, since Android cannot encode an `ALPHA_8` bitmap
     * as an image file, with a checksum, to a temporary file renamed into place, so a reader never
     * sees half of it.
     */
    fun store(key: String, kind: Kind, picture: Bitmap) {
        masks.mkdirs()
        masks.listFiles()?.sortedByDescending(File::lastModified)?.drop(KEPT_FILES - 1)?.forEach(File::delete)
        val alpha = alphaBytes(picture)
        val target = File(masks, "$key${kind.suffix}")
        val temporary = File(masks, "$key${kind.suffix}$TEMPORARY_SUFFIX")
        DataOutputStream(temporary.outputStream().buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(picture.width)
            output.writeInt(picture.height)
            output.write(alpha)
            output.writeLong(checksum(alpha))
        }
        if (!temporary.renameTo(target)) temporary.delete()
    }

    companion object {
        private const val MASK_DIRECTORY_PREFIX = "pixel_lock_screen_evolved_depth"

        /**
         * Named for the model and the mask format: masks found before their edges were refined
         * ([my.github.MrxSiN.pixellockscreenevolved.depth.GuidedFilter]) are found again once.
         */
        private const val MASK_DIRECTORY = "${MASK_DIRECTORY_PREFIX}_${SubjectMaskContract.MODEL}_v2"
        private const val TEMPORARY_SUFFIX = ".tmp"

        /** "PLSM", the start of every mask file this format writes. */
        private const val MAGIC = 0x504c534d

        private const val KEY_SAMPLE = 64
        private const val OPAQUE = 255
        private const val BYTE = 0xff
        private const val ALPHA_SHIFT = 24

        /** Mask alpha, out of 255, at and below which a pixel is taken to be the model's haze rather than the subject. */
        private const val HAZE = 26

        /** Files kept on disk, a mask, a depth map and edits for each of the last six photos, so going back to one needs no new segmentation. */
        private const val KEPT_FILES = 18

        /** The largest picture read back, against a corrupt size. */
        private const val MAX_PIXELS = 4096L * 4096

        /** The guided filter's box, against the mask's long side, and how sharp a photo edge must be to hold to it. */
        private const val GUIDE_DIVISOR = 128
        private const val GUIDE_EPSILON = 2e-3f

        /** Long side segmentation works at; the mask is stretched back over the photo. */
        private const val SEGMENTATION_SIDE = 1024

        /** [surface] scaled so its longer side is at most [SEGMENTATION_SIDE]: the size the photo is segmented at. */
        fun segmentationSize(surface: WallpaperPlacement.Size): WallpaperPlacement.Size {
            val scale = minOf(1f, SEGMENTATION_SIDE.toFloat() / max(surface.width, surface.height))
            return WallpaperPlacement.Size(max(1, (surface.width * scale).roundToInt()), max(1, (surface.height * scale).roundToInt()))
        }

        /**
         * A key for [photo]: its size and a checksum of a sample of its pixels. The sample is
         * 64 a side: at 32, two edits of one photo, a little lighter, could share a key and so a mask.
         */
        fun keyOf(photo: Bitmap): String {
            val sample = Bitmap.createScaledBitmap(photo, KEY_SAMPLE, KEY_SAMPLE, true)
            val pixels = IntArray(KEY_SAMPLE * KEY_SAMPLE).also { sample.getPixels(it, 0, KEY_SAMPLE, 0, 0, KEY_SAMPLE, KEY_SAMPLE) }
            if (sample !== photo) sample.recycle()
            val crc = CRC32()
            pixels.forEach { crc.update(it) }
            return "${photo.width}x${photo.height}-${crc.value.toString(16)}"
        }

        /** Columns of the photo's luminance grid ([LumaGrid]), enough to tell the light under a clock from the light beside it. */
        private const val LUMA_COLUMNS = 24

        /** [photo]'s relative luminance on a coarse grid. */
        fun lumaOf(photo: Bitmap): LumaGrid {
            val columns = minOf(LUMA_COLUMNS, photo.width)
            val rows = max(1, (photo.height.toFloat() * columns / photo.width).roundToInt())
            val small = Bitmap.createScaledBitmap(photo, columns, rows, true)
            val pixels = IntArray(columns * rows).also { small.getPixels(it, 0, columns, 0, 0, columns, rows) }
            if (small !== photo) small.recycle()
            return LumaGrid(columns, rows, FloatArray(pixels.size) { LumaGrid.luminance(pixels[it]) })
        }

        /** Rec. 601 luma of an ARGB colour, from 0 to 1: the guide's brightness, as the service's filter takes it. */
        private fun luma(color: Int): Float =
            (0.299f * ((color shr 16) and 0xff) + 0.587f * ((color shr 8) and 0xff) + 0.114f * (color and 0xff)) / 255f

        /** [picture]'s alpha, stretched to [width] by [height], one value from 0 to 255 per pixel. */
        fun alphaOf(picture: Bitmap, width: Int, height: Int): IntArray {
            val sized = if (picture.width == width && picture.height == height) picture else Bitmap.createScaledBitmap(picture, width, height, true)
            val pixels = IntArray(width * height).also { sized.getPixels(it, 0, width, 0, 0, width, height) }
            if (sized !== picture) sized.recycle()
            for (i in pixels.indices) pixels[i] = pixels[i] ushr ALPHA_SHIFT
            return pixels
        }

        private fun checksum(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

        /** Reads ALPHA_8 without assuming its native row stride equals width. */
        private fun alphaBytes(bitmap: Bitmap): ByteArray {
            val rowBytes = bitmap.rowBytes
            val raw = ByteBuffer.allocate(rowBytes * bitmap.height).also(bitmap::copyPixelsToBuffer).array()
            if (rowBytes == bitmap.width) return raw
            return ByteArray(bitmap.width * bitmap.height).also { compact ->
                repeat(bitmap.height) { y ->
                    raw.copyInto(compact, y * bitmap.width, y * rowBytes, y * rowBytes + bitmap.width)
                }
            }
        }

        private fun CRC32.update(value: Int) {
            update(value ushr 24)
            update(value ushr 16)
            update(value ushr 8)
            update(value)
        }
    }
}
