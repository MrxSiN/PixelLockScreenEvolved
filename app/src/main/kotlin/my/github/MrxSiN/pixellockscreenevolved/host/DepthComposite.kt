package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.depth.GuidedFilter

/**
 * What stands in front of the clock, from the subject's mask, the photo's
 * depth map and a person's own corrections. Kept free of Android so it can be
 * unit tested. Every array is one value from 0 to 255 per pixel, row by row,
 * all the same size.
 *
 * With the depth map ([DepthLayers.DEPTH]) the depth decides two things the
 * mask got wrong, as marks of the same kind as a person's edits ([depthEdits]):
 * what is at least as near as the subject and touches it, a railing or a hand
 * held out before it, stands in front even where the mask missed it, and what
 * the mask took in, was unsure of and is far behind the subject, the sky between
 * an arm and a body, goes back behind the clock. Each is decided outright, kept
 * only where it is wider than [depthEdits]' band, and its edge then snapped to
 * the photo's ([snapEdits]).
 *
 * The depth map is found at 518 px and its edges are soft, so along the
 * subject's outline it reads the subject as partly the wall behind it, and the
 * wall as partly the subject. Taken as it came, it cut a band out of the
 * subject's hair and shoulders (4 624 px of a 1024 x 668 photo's subject on the
 * test phone, the time showing through them), and took away a block of hair the
 * mask was sure of that it could not tell from the wall: the mask is found at
 * 1024 px and is the better judge of the outline wherever it is sure.
 */
internal object DepthComposite {

    /** Mask alpha from which a pixel is the subject's, for finding its depth. */
    private const val SOLID = 128

    /** Fewer subject pixels than this share of the photo, and the depth map has no subject to set the plane by. */
    private const val LEAST_SUBJECT = 0.005f

    /**
     * How much farther than the subject's median depth, in steps out of 255, a pixel the mask missed may be
     * and still be added. The median, not a low percentile, since what the mask took in by mistake (sky
     * between an arm and a body) is far and would drag a low percentile back to it.
     */
    private const val ADD_BEHIND = 10

    /** How much farther than the subject's median depth a pixel of the mask may be before it is the background showing through. */
    private const val REMOVE_BEHIND = 65

    /** Mask alpha from which the mask is sure of a pixel, and the depth map does not take it away. */
    private const val SURE = 250

    /** A box mean from which every pixel of a window is marked, against float rounding. */
    private const val WHOLE = 0.9999f

    /** How [edits] mark a pixel: left as found, made the subject's, or made the background's. */
    const val UNEDITED = 128
    const val ADDED = 255
    const val REMOVED = 0

    /**
     * What [depth] (255 nearest) changes of the mask [alpha], both [width] by [height], as marks laid over
     * it with [applyEdits]: [ADDED] where a pixel the mask missed is about as near as the subject and
     * reached from it through such pixels, [REMOVED] where a pixel of the mask is unsure and far behind it,
     * [UNEDITED] elsewhere; null where the subject is too small to set a depth by. A change narrower than
     * [band] pixels either side is the depth map's soft edge, not a thing in the photo, and is left out.
     */
    fun depthEdits(alpha: IntArray, depth: IntArray, width: Int, height: Int, band: Int): IntArray? {
        require(alpha.size == depth.size && alpha.size == width * height) { "mask and depth must be width × height" }
        val histogram = IntArray(256)
        var solid = 0
        for (i in alpha.indices) if (alpha[i] >= SOLID) {
            histogram[depth[i]]++
            solid++
        }
        if (solid < alpha.size * LEAST_SUBJECT) return null
        var seen = 0
        var median = 0
        while (median < 255) {
            seen += histogram[median]
            if (seen * 2 >= solid) break
            median++
        }
        val addFrom = median - ADD_BEHIND
        val removeBelow = median - REMOVE_BEHIND
        val marks = IntArray(alpha.size) { UNEDITED }
        // From every pixel of the subject kept, out through its 4-neighbours as near as it: a near
        // patch that does not touch the subject (the ground in a corner) stays behind the clock.
        val reached = BooleanArray(alpha.size)
        val stack = IntArray(alpha.size)
        var top = 0
        for (i in alpha.indices) {
            if (alpha[i] in 1 until SURE && depth[i] < removeBelow) marks[i] = REMOVED
            else if (alpha[i] >= SOLID) {
                reached[i] = true
                stack[top++] = i
            }
        }
        fun reach(n: Int) {
            if (reached[n] || depth[n] < addFrom) return
            reached[n] = true
            stack[top++] = n
            if (alpha[n] < SOLID) marks[n] = ADDED
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % width
            if (x > 0) reach(i - 1)
            if (x < width - 1) reach(i + 1)
            if (i >= width) reach(i - width)
            if (i + width < alpha.size) reach(i + width)
        }
        keepWide(marks, REMOVED, width, height, band)
        keepWide(marks, ADDED, width, height, band)
        return marks
    }

    /**
     * Clears every [mark] in [marks] that no square [radius] pixels either side fits wholly inside (a
     * morphological opening), in place: bands and slivers narrower than the square go, and what is wider
     * keeps its shape.
     */
    private fun keepWide(marks: IntArray, mark: Int, width: Int, height: Int, radius: Int) {
        if (radius <= 0 || mark !in marks) return
        val inside = FloatArray(marks.size) { if (marks[it] == mark) 1f else 0f }
        val core = GuidedFilter.boxMean(inside, width, height, radius)
        for (i in core.indices) core[i] = if (core[i] >= WHOLE) 1f else 0f
        val opened = GuidedFilter.boxMean(core, width, height, radius)
        for (i in marks.indices) if (marks[i] == mark && opened[i] <= 0f) marks[i] = UNEDITED
    }

    /** [alpha] with a person's [edits] laid over it, in place. */
    fun applyEdits(alpha: IntArray, edits: IntArray): IntArray {
        require(alpha.size == edits.size) { "mask and edits must be one size" }
        for (i in alpha.indices) when (edits[i]) {
            ADDED -> alpha[i] = 255
            REMOVED -> alpha[i] = 0
        }
        return alpha
    }

    /**
     * Snaps the edges of a person's [edits], already laid over [alpha] ([applyEdits]), to the photo's
     * own edges, in place: a brush is a circle, and a stroke that runs past a shoulder leaves the
     * circle's edge in the sky. Within [radius] pixels of where a stroke ends, [alpha] eases toward
     * itself run through the guided filter against [guide] (the photo's luma, 0 to 1); deep inside a
     * stroke, and away from any, it is left as it was, so what was painted solid stays solid and the
     * rest of the subject is not softened again. Returns [alpha].
     */
    fun snapEdits(alpha: IntArray, edits: IntArray, guide: FloatArray, width: Int, height: Int, radius: Int, epsilon: Float): IntArray {
        require(alpha.size == edits.size && alpha.size == guide.size && alpha.size == width * height) { "mask, edits and guide must be one size" }
        val edited = FloatArray(edits.size) { if (edits[it] == UNEDITED) 0f else 1f }
        if (edited.none { it > 0f }) return alpha
        val near = GuidedFilter.boxMean(edited, width, height, radius)
        val refined = GuidedFilter.apply(guide, FloatArray(alpha.size) { alpha[it] / 255f }, width, height, radius, epsilon)
        for (i in alpha.indices) {
            // 1 where a window is half edited, at a stroke's edge; 0 wholly inside one or clear of any.
            val weight = 1f - abs(2f * near[i] - 1f)
            if (weight <= 0f) continue
            val snapped = refined[i].coerceIn(0f, 1f) * 255f
            alpha[i] = (alpha[i] + (snapped - alpha[i]) * weight + 0.5f).toInt().coerceIn(0, 255)
        }
        return alpha
    }
}
