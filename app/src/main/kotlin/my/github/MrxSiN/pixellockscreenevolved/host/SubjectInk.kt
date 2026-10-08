package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.RectF

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a cut-out subject's ink is, read once from a small copy of the
 * cut-out, for whatever lays the clock out around it: the box the ink fills
 * ([bounds]), and how much of any box the ink covers ([coverage]).
 *
 * A screen-sized cut-out has some millions of pixels, so it is sampled at most
 * [SIDE] pixels a side, and only pixels solid enough to read as the subject
 * count ([INK]): the model leaves a soft edge well around it. Coverage comes
 * from a summed-area table, so any box costs four reads however large it is.
 */
internal class SubjectInk(
    /** The sampled grid's size. */
    private val columns: Int,
    private val rows: Int,
    /** Whether each cell of the grid, row by row, is the subject's. */
    ink: BooleanArray,
    /** The cut-out's own size, in whose pixels every box here is given. */
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) {

    private val sums = IntArray((columns + 1) * (rows + 1))

    private var left = columns
    private var top = rows
    private var right = -1
    private var bottom = -1

    init {
        for (y in 0 until rows) {
            var rowSum = 0
            for (x in 0 until columns) {
                if (ink[y * columns + x]) {
                    rowSum++
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
                sums[(y + 1) * (columns + 1) + x + 1] = sums[y * (columns + 1) + x + 1] + rowSum
            }
        }
    }

    /** Whether any cell holds the subject. */
    val isEmpty: Boolean get() = right < 0

    /** Writes the box the ink fills, in the cut-out's pixels, into [out], each cell standing for the whole of itself; false when there is none. */
    fun bounds(out: RectF): Boolean {
        if (isEmpty) return false
        val across = sourceWidth.toFloat() / columns
        val down = sourceHeight.toFloat() / rows
        out.set(left * across, top * down, (right + 1) * across, (bottom + 1) * down)
        return true
    }

    /**
     * The share, from 0 to 1, of the box from ([boxLeft], [boxTop]) to
     * ([boxRight], [boxBottom]), in the cut-out's pixels, that the subject
     * covers. Parts of the box off the cut-out count as uncovered.
     */
    fun coverage(boxLeft: Float, boxTop: Float, boxRight: Float, boxBottom: Float): Float {
        val x0 = cell(boxLeft, sourceWidth, columns)
        val x1 = cell(boxRight, sourceWidth, columns)
        val y0 = cell(boxTop, sourceHeight, rows)
        val y1 = cell(boxBottom, sourceHeight, rows)
        val cells = (cellExact(boxRight, sourceWidth, columns) - cellExact(boxLeft, sourceWidth, columns)) *
            (cellExact(boxBottom, sourceHeight, rows) - cellExact(boxTop, sourceHeight, rows))
        if (cells <= 0f) return 0f
        val stride = columns + 1
        val inked = sums[y1 * stride + x1] - sums[y0 * stride + x1] - sums[y1 * stride + x0] + sums[y0 * stride + x0]
        return (inked / cells).coerceIn(0f, 1f)
    }

    private fun cellExact(pixel: Float, size: Int, cells: Int): Float = pixel * cells / size

    private fun cell(pixel: Float, size: Int, cells: Int): Int = cellExact(pixel, size, cells).roundToInt().coerceIn(0, cells)

    companion object {
        /** Long side the cut-out is sampled at. */
        private const val SIDE = 128

        /** Alpha, out of 255, at and above which a pixel counts as the subject rather than its soft edge. */
        private const val INK = 40

        private const val ALPHA_SHIFT = 24

        /** Reads [cutout]'s ink. */
        fun of(cutout: Bitmap): SubjectInk? {
            if (cutout.width <= 0 || cutout.height <= 0) return null
            val scale = min(1f, SIDE.toFloat() / max(cutout.width, cutout.height))
            val width = max(1, (cutout.width * scale).roundToInt())
            val height = max(1, (cutout.height * scale).roundToInt())
            val small = Bitmap.createScaledBitmap(cutout, width, height, true)
            val pixels = IntArray(width * height).also { small.getPixels(it, 0, width, 0, 0, width, height) }
            if (small !== cutout) small.recycle()
            val ink = BooleanArray(pixels.size) { (pixels[it] ushr ALPHA_SHIFT) >= INK }
            return SubjectInk(width, height, ink, cutout.width, cutout.height)
        }
    }
}
