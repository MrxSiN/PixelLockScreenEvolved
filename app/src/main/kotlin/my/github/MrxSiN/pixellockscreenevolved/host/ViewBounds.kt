package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View

/**
 * Where views are drawn, with every move and scale of theirs and their parents' applied.
 *
 * Every answer is written into a matrix or rectangle the caller owns, since
 * several callers ask before each frame; on the main thread.
 */
internal object ViewBounds {

    private val viewToWindow = Matrix()
    private val ancestorToWindow = Matrix()
    private val windowToAncestor = Matrix()

    /**
     * Writes [view]'s pixels in its window's into [out], including what a Compose
     * scene hosting it applies ([ComposedViewPlacement]), which its view matrices leave out.
     */
    fun toWindow(view: View, out: Matrix, beforeDraw: Boolean = false): Matrix {
        if (!ComposedViewPlacement.toWindow(view, out, beforeDraw)) {
            out.reset()
            view.transformMatrixToGlobal(out)
        }
        return out
    }

    /** Writes the window's pixels in [view]'s own into [out]. */
    fun fromWindow(view: View, out: Matrix): Matrix {
        // A matrix that cannot be inverted leaves the identity, as a new matrix would be.
        out.reset()
        toWindow(view, viewToWindow).invert(out)
        return out
    }

    /** Writes [view]'s bounds in its window into [out]; [beforeDraw] as for [ComposedViewPlacement.toWindow]. */
    fun inWindow(view: View, out: RectF, beforeDraw: Boolean = false): RectF {
        out.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        toWindow(view, viewToWindow, beforeDraw).mapRect(out)
        return out
    }

    /** Writes [view]'s bounds in [ancestor]'s own pixels into [out], with every move and scale between them applied and none above. */
    fun inAncestor(ancestor: View, view: View, out: RectF, beforeDraw: Boolean = false): RectF {
        windowToAncestor.reset()
        toWindow(ancestor, ancestorToWindow, beforeDraw).invert(windowToAncestor)
        toWindow(view, viewToWindow, beforeDraw).postConcat(windowToAncestor)
        out.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
        viewToWindow.mapRect(out)
        return out
    }
}
