package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Whether the photo's subject may stand in front of the time, or the time must
 * come forward to stay readable. Kept free of Android so it can be unit tested.
 *
 * A single share of ink hidden let a subject wipe out one digit whole, an hour
 * gone and the minute untouched, as long as the rest stayed; and it held a thin
 * clock to the same share as a heavy one, whose strokes a subject cuts through
 * far sooner. So the time is read in parts ([read]): its digits, found by the
 * gaps in its ink, grouped into the hour and the minute, and how heavy its
 * strokes are. The answer ([inFront]) weighs every part, the contrast of the
 * clock against the wallpaper around it, the person's accessibility settings
 * and how they chose the effect to behave ([DepthOcclusion]), and keeps its
 * last answer until the time is clearly on the other side of the line, so a
 * subject at the edge of it never flickers in front of the time and behind it.
 */
internal object TimeOcclusion {

    /** How much of the time stays visible: overall, digit by digit, and by group, the hour first. */
    class Reading(
        val hidden: Float,
        val digits: FloatArray,
        val groups: FloatArray,
        /** The share of the digits' boxes their ink fills: about 0.2 for a thin cut, 0.5 for a heavy one. */
        val strokeDensity: Float,
    )

    /** What the answer depends on beyond the time itself. */
    data class Conditions(
        val mode: DepthOcclusion,
        /** The WCAG contrast ratio of the clock against the wallpaper around it, from 1 to 21; null where unknown. */
        val contrast: Float?,
        /** Whether the person reads with high-contrast text or a large font. */
        val accessible: Boolean,
    )

    /** Alpha, out of 255, above which a pixel of the time counts as ink. */
    private const val INK = 32

    /** A part with less than this share of the median digit's ink is a separator, such as the colon. */
    private const val SEPARATOR = 0.3f

    /** A part wider than this many median digits is digits that touch, split evenly. */
    private const val JOINED = 1.7f

    /** The widest gap, against the time's longer side, still read as within one mark rather than between two. */
    private const val BRIDGE = 0.012f

    /** A line with less than this share of the ink is a stray mark, not a line of the time. */
    private const val STRAY_LINE = 0.05f

    /**
     * The time as drawn ([drawn]) and as left once the subject is erased from it
     * ([left]), alpha-only, [width] by [height] row by row; null where nothing is drawn.
     */
    fun read(drawn: ByteArray, left: ByteArray, width: Int, height: Int): Reading? {
        val total = sum(drawn, 0, width, 0, height, width)
        if (total == 0L) return null
        val bridge = max(1, (max(width, height) * BRIDGE).toInt())
        val lines = runs(height, bridge) { y -> (0 until width).any { x -> ink(drawn[y * width + x]) } }
            .filter { (top, bottom) -> sum(drawn, 0, width, top, bottom, width) >= total * STRAY_LINE }
        class Part(val line: Int, val x0: Int, val x1: Int, val y0: Int, val y1: Int, val drawn: Long, val left: Long)
        val parts = ArrayList<Part>()
        val separators = HashSet<Int>()
        lines.forEachIndexed { index, (top, bottom) ->
            val columns = runs(width, bridge) { x -> (top until bottom).any { y -> ink(drawn[y * width + x]) } }
            val inks = columns.map { (x0, x1) -> sum(drawn, x0, x1, top, bottom, width) }
            val medianInk = inks.sorted()[inks.size / 2]
            val digitWidths = columns.filterIndexed { i, _ -> inks[i] >= medianInk * SEPARATOR }.map { it.second - it.first }.sorted()
            val medianWidth = digitWidths.getOrElse(digitWidths.size / 2) { 1 }.coerceAtLeast(1)
            columns.forEachIndexed { i, (x0, x1) ->
                if (inks[i] < medianInk * SEPARATOR) {
                    separators += parts.size
                    return@forEachIndexed
                }
                val pieces = if (x1 - x0 > medianWidth * JOINED) max(1, ((x1 - x0).toFloat() / medianWidth).roundToInt()) else 1
                for (p in 0 until pieces) {
                    val a = x0 + (x1 - x0) * p / pieces
                    val b = x0 + (x1 - x0) * (p + 1) / pieces
                    parts += Part(index, a, b, top, bottom, sum(drawn, a, b, top, bottom, width), sum(left, a, b, top, bottom, width))
                }
            }
        }
        if (parts.isEmpty()) return null
        // Stacked lines are the hour over the minute; on one line, the hour is what comes before the colon.
        val split = when {
            lines.size > 1 -> parts.indexOfFirst { it.line > 0 }
            separators.isNotEmpty() -> separators.min()
            else -> parts.size / 2
        }.coerceIn(0, parts.size)
        val groups = listOf(parts.subList(0, split), parts.subList(split, parts.size)).filter { it.isNotEmpty() }
        val boxes = parts.sumOf { (it.x1 - it.x0).toLong() * (it.y1 - it.y0) }
        return Reading(
            hidden = 1f - sum(left, 0, width, 0, height, width).toFloat() / total,
            digits = FloatArray(parts.size) { visible(parts[it].left, parts[it].drawn) },
            groups = FloatArray(groups.size) { g -> visible(groups[g].sumOf { it.left }, groups[g].sumOf { it.drawn }) },
            strokeDensity = if (boxes == 0L) 0f else total / OPAQUE / boxes,
        )
    }

    /** The bar the time must clear for the subject to stand in front of it. */
    class Bar(val mostHidden: Float, val leastDigit: Float, val leastGroup: Float)

    fun barFor(conditions: Conditions, strokeDensity: Float): Bar {
        val base = if (conditions.mode == DepthOcclusion.READABLE || conditions.accessible) READABLE_BAR else BALANCED_BAR
        // Thin strokes and low contrast each ask for more of the time to show, up to a tenth more each.
        val thin = ((THIN - strokeDensity) / THIN).coerceIn(0f, 1f) * TIGHTEN
        val faint = conditions.contrast?.let { ((LEGIBLE_CONTRAST - it) / (LEGIBLE_CONTRAST - 1f)).coerceIn(0f, 1f) * TIGHTEN } ?: 0f
        val tighter = thin + faint
        return Bar(base.mostHidden - tighter, base.leastDigit + tighter, base.leastGroup + tighter)
    }

    /**
     * Whether the subject stands in front of the time [reading] describes, given
     * whether it stood in front when last asked ([wasInFront]): the bar is a
     * little lower for keeping the subject in front than for bringing it there.
     */
    fun inFront(reading: Reading?, conditions: Conditions, wasInFront: Boolean): Boolean {
        if (reading == null || conditions.mode == DepthOcclusion.DEPTH) return true
        val bar = barFor(conditions, reading.strokeDensity)
        val give = if (wasInFront) HYSTERESIS else -HYSTERESIS
        return reading.hidden <= bar.mostHidden + give &&
            reading.digits.all { it >= bar.leastDigit - give } &&
            reading.groups.all { it >= bar.leastGroup - give }
    }

    /** The WCAG contrast ratio between two relative luminances, from 1 to 21. */
    fun contrast(a: Float, b: Float): Float = (max(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

    private val BALANCED_BAR = Bar(mostHidden = 0.4f, leastDigit = 0.55f, leastGroup = 0.6f)
    private val READABLE_BAR = Bar(mostHidden = 0.25f, leastDigit = 0.7f, leastGroup = 0.75f)

    /** A stroke density below which a cut counts as thin. */
    private const val THIN = 0.3f

    /** The contrast ratio WCAG asks of large text; below it, more of the time must show. */
    private const val LEGIBLE_CONTRAST = 3f
    private const val TIGHTEN = 0.1f
    private const val HYSTERESIS = 0.05f
    private const val OPAQUE = 255f

    private fun visible(left: Long, drawn: Long): Float = if (drawn == 0L) 1f else (left.toFloat() / drawn).coerceIn(0f, 1f)

    private fun ink(alpha: Byte): Boolean = (alpha.toInt() and 0xff) > INK

    private fun sum(alpha: ByteArray, x0: Int, x1: Int, y0: Int, y1: Int, width: Int): Long {
        var sum = 0L
        for (y in y0 until y1) for (x in x0 until x1) sum += alpha[y * width + x].toInt() and 0xff
        return sum
    }

    /**
     * The runs of indices below [size] for which [inked] holds, as half-open ranges, a gap of
     * [bridge] or fewer joining the runs either side of it: the gaps between a dot matrix's dots.
     */
    private fun runs(size: Int, bridge: Int, inked: (Int) -> Boolean): List<Pair<Int, Int>> {
        val found = ArrayList<Pair<Int, Int>>()
        var start = -1
        for (i in 0..size) {
            val on = i < size && inked(i)
            if (on && start < 0) start = i
            if (!on && start >= 0) {
                val last = found.lastOrNull()
                if (last != null && start - last.second <= bridge) found[found.size - 1] = last.first to i else found += start to i
                start = -1
            }
        }
        return found
    }
}

/**
 * The relative luminance of a photo on a coarse grid, for the contrast of the
 * clock against the wallpaper around it. Kept free of Android.
 */
internal class LumaGrid(private val columns: Int, private val rows: Int, private val values: FloatArray) {

    /** The mean luminance over the box from ([left], [top]) to ([right], [bottom]), each a share of the photo, or null off it. */
    fun mean(left: Float, top: Float, right: Float, bottom: Float): Float? {
        val x0 = (left * columns).toInt().coerceIn(0, columns)
        val x1 = kotlin.math.ceil(right * columns).toInt().coerceIn(0, columns)
        val y0 = (top * rows).toInt().coerceIn(0, rows)
        val y1 = kotlin.math.ceil(bottom * rows).toInt().coerceIn(0, rows)
        if (x1 <= x0 || y1 <= y0) return null
        var sum = 0f
        for (y in y0 until y1) for (x in x0 until x1) sum += values[y * columns + x]
        return sum / ((x1 - x0) * (y1 - y0))
    }

    companion object {
        /** The relative luminance of an sRGB colour, from 0 to 1, as WCAG defines it. */
        fun luminance(color: Int): Float {
            fun channel(shift: Int): Float {
                val c = ((color shr shift) and 0xff) / 255f
                return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
            }
            return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
        }
    }
}
