package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

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

    /** The reflective path from a holder to its layout coordinates, per holder class. */
    private class Lookup(
        val layoutNode: Field,
        val nodes: Field,
        val innerCoordinator: Field,
        val localToWindow: Method,
    )

    /**
     * [view]'s pixels in its window's, with every graphics layer of the Compose
     * scene around it applied; null when [view] is not hosted by Compose, or its
     * coordinates cannot be read, where its view matrices are the whole story.
     */
    fun toWindow(view: View): Matrix? {
        val holder = generateSequence(view) { it.parent as? View }.firstOrNull(::isHolder) ?: return null
        val holderToWindow = runCatching { holderToWindow(holder) }.getOrNull() ?: return null
        // The views between [view] and the holder are still placed by their own matrices.
        val viewToHolder = Matrix().also(view::transformMatrixToGlobal)
        viewToHolder.postConcat(Matrix().also { Matrix().also(holder::transformMatrixToGlobal).invert(it) })
        return viewToHolder.apply { postConcat(holderToWindow) }
    }

    private fun isHolder(view: View): Boolean =
        generateSequence(view.javaClass as Class<*>?) { it.superclass }.any { it.name == HOLDER }

    /**
     * The holder's pixels in the window's, from where Compose puts three of its
     * points, which is enough for any scale, move or rotation a layer applies.
     */
    private fun holderToWindow(holder: View): Matrix? {
        val lookup = lookupFor(holder.javaClass) ?: return null
        val node = lookup.layoutNode.get(holder) ?: return null
        val coordinates = lookup.innerCoordinator.get(lookup.nodes.get(node)) ?: return null
        val local = floatArrayOf(0f, 0f, SPAN, 0f, 0f, SPAN)
        val window = FloatArray(local.size)
        for (i in local.indices step 2) {
            val mapped = lookup.localToWindow.invoke(coordinates, packOffset(local[i], local[i + 1])) as Long
            window[i] = unpackX(mapped)
            window[i + 1] = unpackY(mapped)
        }
        return Matrix().takeIf { it.setPolyToPoly(local, 0, window, 0, 3) }
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
