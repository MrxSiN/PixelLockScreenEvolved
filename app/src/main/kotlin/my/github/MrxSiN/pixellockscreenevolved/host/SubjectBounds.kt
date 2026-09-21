package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.RectF

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The box a cut-out subject's ink fills, for a clock laid out around it.
 *
 * A box needs far fewer pixels than the cut-out has, so it is read from a small
 * copy: a screen-sized cut-out would otherwise be some millions of pixels to
 * walk each time a photo changed. The model leaves a soft edge well around the
 * subject, so only pixels solid enough to read as the subject count ([INK]).
 */
internal object SubjectBounds {

    /** Long side the cut-out is sampled at. */
    private const val SIDE = 128

    /** Alpha, out of 255, at and above which a pixel counts as the subject rather than its soft edge. */
    private const val INK = 40

    private const val ALPHA_SHIFT = 24

    /** [cutout]'s subject in the cut-out's own pixels, or null where it has no ink at all. */
    fun of(cutout: Bitmap): RectF? {
        if (cutout.width <= 0 || cutout.height <= 0) return null
        val scale = min(1f, SIDE.toFloat() / max(cutout.width, cutout.height))
        val width = max(1, (cutout.width * scale).roundToInt())
        val height = max(1, (cutout.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(cutout, width, height, true)
        val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
        if (small !== cutout) small.recycle()

        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr ALPHA_SHIFT) < INK) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < 0) return null

        // Back to the cut-out's own pixels, each sampled pixel standing for the whole of its cell.
        val across = cutout.width.toFloat() / width
        val down = cutout.height.toFloat() / height
        return RectF(left * across, top * down, (right + 1) * across, (bottom + 1) * down)
    }
}
