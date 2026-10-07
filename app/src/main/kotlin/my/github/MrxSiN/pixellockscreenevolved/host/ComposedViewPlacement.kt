package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.core.Invoke

/**
 * Where Compose really draws a plain view it hosts.
 *
 * Since Android 17 QPR1 the lock screen hosts a clock face's view inside a
 * Compose scene, through an `AndroidViewHolder`. Compose places that holder as
 * a view at its layout position only; the scales and moves its scene applies to
 * the clock around it are graphics layers, which no view's matrix carries. So
 * `View.transformMatrixToGlobal` put the holder, and every view in it, where
 * the scene would lay it out unscaled, and something drawn to line up with the
 * screen (the depth effect's subject over the wallpaper) came out scaled about a
 * tenth too small and shifted. Compose's own coordinates for the holder carry
 * those layers, and are read here by reflection, SystemUI's Compose being
 * reachable only through its class loader.
 */
internal object ComposedViewPlacement {

    private const val HOLDER = "androidx.compose.ui.viewinterop.AndroidViewHolder"

    /** How far apart the points mapped to recover the holder's transform are, in pixels. */
    private const val SPAN = 1000f

    private val lookups = HashMap<Class<*>, Lookup?>()

    /** Whether each view class seen is a holder, so a frame's walk up the parents scans no class hierarchy. */
    private val holderClasses = HashMap<Class<*>, Boolean>()

    /** The reflective path from a holder to its layout coordinates, per holder class. */
    private class Lookup(
        val layoutNode: Field,
        val nodes: Field,
        val innerCoordinator: Field,
        val localToWindow: Method,
    )

    // Scratch for the per-frame callers ([ViewBounds]), all on the main thread: one frame allocates only
    // the three Longs reflection boxes its answers in.
    private val local = floatArrayOf(0f, 0f, SPAN, 0f, 0f, SPAN)
    private val window = FloatArray(local.size)
    private val points = Array(local.size / 2) { arrayOf<Any?>(packOffset(local[2 * it], local[2 * it + 1])) }
    private val holderToWindow = Matrix()
    private val holderToGlobal = Matrix()
    private val globalToHolder = Matrix()

    /**
     * Writes [view]'s pixels in its window's into [out], with every graphics
     * layer of the Compose scene around it applied; false, leaving [out] as it
     * was, when [view] is not hosted by Compose, or its coordinates cannot be
     * read, where its view matrices are the whole story. On the main thread.
     */
    fun toWindow(view: View, out: Matrix): Boolean {
        var holder: View? = view
        while (holder != null && !isHolder(holder.javaClass)) holder = holder.parent as? View
        if (holder == null) return false
        val placed = try {
            holderToWindow(holder)
        } catch (_: Throwable) {
            false
        }
        if (!placed) return false
        // The views between [view] and the holder are still placed by their own matrices.
        holderToGlobal.reset()
        holder.transformMatrixToGlobal(holderToGlobal)
        holderToGlobal.invert(globalToHolder)
        out.reset()
        view.transformMatrixToGlobal(out)
        out.postConcat(globalToHolder)
        out.postConcat(holderToWindow)
        return true
    }

    private fun isHolder(type: Class<*>): Boolean = holderClasses[type] ?: run {
        var superclass: Class<*>? = type
        while (superclass != null && superclass.name != HOLDER) superclass = superclass.superclass
        (superclass != null).also { holderClasses[type] = it }
    }

    /**
     * Sets [holderToWindow] to the holder's pixels in the window's, from where
     * Compose puts three of its points, which is enough for any scale, move or
     * rotation a layer applies.
     */
    private fun holderToWindow(holder: View): Boolean {
        val lookup = lookupFor(holder.javaClass) ?: return false
        val node = lookup.layoutNode.get(holder) ?: return false
        val coordinates = lookup.innerCoordinator.get(lookup.nodes.get(node)) ?: return false
        for (i in points.indices) {
            val mapped = Invoke.with(lookup.localToWindow, coordinates, points[i]) as Long
            window[2 * i] = unpackX(mapped)
            window[2 * i + 1] = unpackY(mapped)
        }
        return holderToWindow.setPolyToPoly(local, 0, window, 0, 3)
    }

    /** A lookup that failed is remembered too, so a build without these fields is not searched each frame. */
    private fun lookupFor(type: Class<*>): Lookup? = synchronized(lookups) {
        if (type in lookups) return lookups[type]
        lookups[type] = runCatching {
            val holder = generateSequence(type as Class<*>?) { it.superclass }.first { it.name == HOLDER }
            val layoutNode = holder.getDeclaredField("layoutNode").accessible()
            val nodes = layoutNode.type.getDeclaredField("nodes").accessible()
            val inner = nodes.type.getDeclaredField("innerCoordinator").accessible()
            // A value-class argument gives the method a mangled name; its prefix is stable.
            val localToWindow = generateSequence(inner.type as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .first {
                    it.name.startsWith("localToWindow") &&
                        it.parameterTypes.singleOrNull() == Long::class.java &&
                        it.returnType == Long::class.java
                }
                .accessible()
            Lookup(layoutNode, nodes, inner, localToWindow)
        }.getOrNull()
        lookups[type]
    }

    private fun Field.accessible(): Field = apply { isAccessible = true }

    private fun Method.accessible(): Method = apply { isAccessible = true }

    /** Compose's `Offset`, two floats packed in a long. */
    private fun packOffset(x: Float, y: Float): Long =
        (x.toRawBits().toLong() shl Int.SIZE_BITS) or (y.toRawBits().toLong() and 0xFFFFFFFFL)

    private fun unpackX(offset: Long): Float = Float.fromBits((offset shr Int.SIZE_BITS).toInt())

    private fun unpackY(offset: Long): Float = Float.fromBits(offset.toInt())
}
