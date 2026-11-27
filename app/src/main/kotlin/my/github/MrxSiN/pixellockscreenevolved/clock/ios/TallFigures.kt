package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Path
import android.graphics.PathIterator

/**
 * Draws a figure taller the way iOS draws its tall lock screen clock: its top
 * and bottom keep their shape, the flag of a 1, the bar of a 7, the round ends
 * of a 0, and only the middle grows. A uniform stretch dragged the short flag
 * of a 1 into a long second leg once the figure ran down to the subject.
 *
 * The height from [top] to [top] + [height] is mapped so the first and last
 * [CAP] of it are moved but not stretched, and the middle takes the rest;
 * every point of the path is moved by that map, curves' control points
 * included, so curves stay curves. Where the figure is to be less than
 * [CAP] × 2 taller than it is, the stretch is uniform, as before.
 */
internal object TallFigures {

    /** The share of a figure's height, at the top and at the bottom, kept as drawn. */
    const val CAP = 0.22f

    private const val SEGMENT_FLOATS = 8

    /** Writes into [dst] [src] made [stretch] times as tall, from its top at [top], [height] tall unstretched. */
    fun stretch(src: Path, top: Float, height: Float, stretch: Float, dst: Path) {
        dst.reset()
        val iterator = src.pathIterator
        // A segment's points: its start and up to three more, as x and y.
        val points = FloatArray(SEGMENT_FLOATS)
        while (iterator.hasNext()) {
            val verb = iterator.next(points, 0)
            for (i in 1 until points.size step 2) points[i] = map(points[i], top, height, stretch)
            when (verb) {
                PathIterator.VERB_MOVE -> dst.moveTo(points[0], points[1])
                PathIterator.VERB_LINE -> dst.lineTo(points[2], points[3])
                PathIterator.VERB_QUAD -> dst.quadTo(points[2], points[3], points[4], points[5])
                PathIterator.VERB_CONIC -> dst.conicTo(points[2], points[3], points[4], points[5], points[6])
                PathIterator.VERB_CUBIC -> dst.cubicTo(points[2], points[3], points[4], points[5], points[6], points[7])
                PathIterator.VERB_CLOSE -> dst.close()
                PathIterator.VERB_DONE -> break
            }
        }
    }

    /**
     * Where a point [y], on a figure from [top] down [height], goes when the figure is made [stretch] times as
     * tall from the same top. Pure, so it can be unit tested.
     */
    fun map(y: Float, top: Float, height: Float, stretch: Float): Float {
        if (height <= 0f) return y
        val t = (y - top) / height
        val total = height * stretch
        val cap = height * CAP
        val middle = total - 2 * cap
        // Too little growth for the caps to stay whole: a plain stretch.
        if (middle < height * (1 - 2 * CAP)) return top + (y - top) * stretch
        return top + when {
            t <= CAP -> y - top
            t >= 1 - CAP -> total - (height - (y - top))
            else -> cap + (t - CAP) / (1 - 2 * CAP) * middle
        }
    }
}
