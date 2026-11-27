package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A person's edits to the subject ([DepthMaskEditor]): one [DepthComposite] mark per pixel of
 * the photo, painted in strokes of round dabs, each stroke one step to undo or redo. Kept free
 * of Android so it can be desk-tested; [onChanged] names each box of pixels whose subject
 * changed, as left, top, right and bottom (exclusive), for the editor to redraw.
 */
internal class SubjectEdits(
    val width: Int,
    val height: Int,
    /** The subject as the effect found it, one value from 0 to 255 per pixel. */
    private val found: IntArray,
    initial: IntArray,
    private val onChanged: (Int, Int, Int, Int) -> Unit,
) {

    private val marks = initial.copyOf()
    private val undone = ArrayDeque<Stroke>()
    private val done = ArrayDeque<Stroke>()
    private var stroke: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f

    /** One stroke: the pixels it changed, what each held before, and the [mark] it left. A pixel is recorded once: the next dab finds it at [mark] already. */
    private class Stroke(val mark: Int) {
        var pixels = IntArray(256)
        var before = ByteArray(256)
        var size = 0

        fun record(i: Int, old: Int) {
            if (size == pixels.size) {
                pixels = pixels.copyOf(size * 2)
                before = before.copyOf(size * 2)
            }
            pixels[size] = i
            before[size] = old.toByte()
            size++
        }
    }

    val canUndo: Boolean get() = done.isNotEmpty()
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** Whether anything differs from how the editor opened. */
    val isDirty: Boolean get() = done.isNotEmpty()

    /** The subject at pixel [i], edits included, from 0 to 255. */
    fun alphaAt(i: Int): Int = when (marks[i]) {
        DepthComposite.ADDED -> 255
        DepthComposite.REMOVED -> 0
        else -> found[i]
    }

    /** The marks as they stand, to keep. */
    fun snapshot(): IntArray = marks.copyOf()

    /** Starts a stroke leaving [mark] ([DepthComposite.ADDED], [DepthComposite.REMOVED] or [DepthComposite.UNEDITED]) with a dab at ([x], [y]). */
    fun begin(mark: Int, x: Float, y: Float, radius: Float) {
        stroke = Stroke(mark)
        lastX = x
        lastY = y
        dab(x, y, radius)
    }

    /** Carries the stroke on to ([x], [y]), dabbing close enough along the way that no gap opens however fast the finger moved. */
    fun moveTo(x: Float, y: Float, radius: Float) {
        if (stroke == null) return
        val distance = hypot(x - lastX, y - lastY)
        val step = max(0.5f, radius * STEP)
        val steps = ceil(distance / step).toInt()
        for (s in 1..steps) {
            val t = s.toFloat() / steps
            dab(lastX + (x - lastX) * t, lastY + (y - lastY) * t, radius)
        }
        lastX = x
        lastY = y
    }

    /** Keeps the stroke as one step to undo; a stroke that changed nothing is not kept. */
    fun end() {
        val kept = stroke ?: return
        stroke = null
        if (kept.size == 0) return
        push(kept)
    }

    /** Takes the stroke under way back, as when a second finger turns it into a pinch. */
    fun cancel() {
        val dropped = stroke ?: return
        stroke = null
        restore(dropped)
    }

    /** Returns every pixel to the subject as found, as one step to undo. */
    fun reset() {
        end()
        val all = Stroke(DepthComposite.UNEDITED)
        for (i in marks.indices) if (marks[i] != DepthComposite.UNEDITED) {
            all.record(i, marks[i])
            marks[i] = DepthComposite.UNEDITED
        }
        if (all.size == 0) return
        push(all)
        changed(all)
    }

    fun undo() {
        end()
        val last = done.removeLastOrNull() ?: return
        restore(last)
        undone.addLast(last)
    }

    fun redo() {
        val next = undone.removeLastOrNull() ?: return
        for (k in 0 until next.size) marks[next.pixels[k]] = next.mark
        done.addLast(next)
        changed(next)
    }

    private fun push(kept: Stroke) {
        done.addLast(kept)
        if (done.size > HISTORY) done.removeFirst()
        undone.clear()
    }

    private fun restore(stroke: Stroke) {
        for (k in 0 until stroke.size) marks[stroke.pixels[k]] = stroke.before[k].toInt() and 0xFF
        changed(stroke)
    }

    private fun changed(stroke: Stroke) {
        if (stroke.size == 0) return
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        for (k in 0 until stroke.size) {
            val i = stroke.pixels[k]
            val x = i % width
            val y = i / width
            left = min(left, x)
            top = min(top, y)
            right = max(right, x + 1)
            bottom = max(bottom, y + 1)
        }
        onChanged(left, top, right, bottom)
    }

    private fun dab(x: Float, y: Float, radius: Float) {
        val current = stroke ?: return
        val left = max(0, (x - radius).toInt())
        val top = max(0, (y - radius).toInt())
        val right = min(width, (x + radius).toInt() + 1)
        val bottom = min(height, (y + radius).toInt() + 1)
        if (right <= left || bottom <= top) return
        val r2 = radius * radius
        var any = false
        for (py in top until bottom) for (px in left until right) {
            val dx = px + 0.5f - x
            val dy = py + 0.5f - y
            if (dx * dx + dy * dy > r2) continue
            val i = py * width + px
            if (marks[i] == current.mark) continue
            current.record(i, marks[i])
            marks[i] = current.mark
            any = true
        }
        if (any) onChanged(left, top, right, bottom)
    }

    private companion object {
        /** The most strokes kept to undo. */
        const val HISTORY = 50

        /** How far apart dabs are along a stroke, against the brush's radius. */
        const val STEP = 0.3f
    }
}
