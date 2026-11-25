package my.github.MrxSiN.pixellockscreenevolved.host

/**
 * What stands in front of the clock, from the subject's mask, the photo's
 * depth map and a person's own corrections. Kept free of Android so it can be
 * unit tested. Every array is one value from 0 to 255 per pixel, row by row,
 * all the same size.
 *
 * With the depth map ([DepthLayers.DEPTH]) the clock is set at a plane in the
 * photo rather than behind one cut-out: the far side of the subject. Whatever
 * is at least as near as that plane stands in front of the clock even where
 * the mask missed it, a railing or a branch before the subject, and whatever
 * the mask took in that is well behind the plane, the sky between an arm and a
 * body, goes back behind it.
 */
internal object DepthComposite {

    /** Mask alpha from which a pixel is the subject's, for finding its depth. */
    private const val SOLID = 128

    /** Fewer subject pixels than this share of the photo, and the depth map has no subject to set the plane by. */
    private const val LEAST_SUBJECT = 0.005f

    /**
     * The plane is this far behind the subject's median depth, in steps out of 255: its far side.
     * The median, not a low percentile, since what the mask took in by mistake (sky between an arm
     * and a body) is far and would drag a low percentile back to it.
     */
    private const val FAR_SIDE = 25

    /** The plane's soft edge, either side of it, in depth steps out of 255. */
    private const val SOFTNESS = 10

    /** How far behind the plane a pixel of the mask is still kept: further, and it is the background showing through the subject. */
    private const val BEHIND = 40

    /** How [edits] mark a pixel: left as found, made the subject's, or made the background's. */
    const val UNEDITED = 128
    const val ADDED = 255
    const val REMOVED = 0

    /**
     * The mask [alpha] laid against [depth] (255 nearest), in place, or left as it was where the
     * subject is too small to set a plane by; [alpha] is returned either way.
     */
    fun combine(alpha: IntArray, depth: IntArray): IntArray {
        require(alpha.size == depth.size) { "mask and depth must be one size" }
        val histogram = IntArray(256)
        var solid = 0
        for (i in alpha.indices) if (alpha[i] >= SOLID) {
            histogram[depth[i]]++
            solid++
        }
        if (solid < alpha.size * LEAST_SUBJECT) return alpha
        // The subject's depths from far (0) to near (255): its median, less the far side.
        var seen = 0
        var median = 0
        while (median < 255) {
            seen += histogram[median]
            if (seen * 2 >= solid) break
            median++
        }
        val plane = median - FAR_SIDE
        for (i in alpha.indices) {
            val near = ramp(depth[i], plane - SOFTNESS, plane + SOFTNESS)
            val kept = ramp(depth[i], plane - BEHIND - SOFTNESS, plane - BEHIND + SOFTNESS)
            alpha[i] = maxOf(alpha[i] * kept / 255, near)
        }
        return alpha
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

    /** 0 at and below [from], 255 at and above [to], eased between. */
    private fun ramp(value: Int, from: Int, to: Int): Int {
        if (value <= from) return 0
        if (value >= to) return 255
        val t = (value - from).toFloat() / (to - from)
        return (t * t * (3f - 2f * t) * 255f + 0.5f).toInt()
    }
}
