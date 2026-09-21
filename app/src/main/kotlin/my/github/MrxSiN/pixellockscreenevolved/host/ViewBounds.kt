package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View

/** Where views are drawn, with every move and scale of theirs and their parents' applied. */
internal object ViewBounds {

    /**
     * [view]'s pixels in its window's, including what a Compose scene hosting it
     * applies ([ComposedViewPlacement]), which its view matrices leave out.
     */
    fun toWindow(view: View): Matrix =
        ComposedViewPlacement.toWindow(view) ?: Matrix().also(view::transformMatrixToGlobal)

    /** The window's pixels in [view]'s own. */
    fun fromWindow(view: View): Matrix = Matrix().also { toWindow(view).invert(it) }

    /** [view]'s bounds in its window. */
    fun inWindow(view: View): RectF =
        RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()).also(toWindow(view)::mapRect)

    /** [view]'s bounds in [ancestor]'s own pixels, with every move and scale between them applied and none above. */
    fun inAncestor(ancestor: View, view: View): RectF {
        val toAncestor = toWindow(view).apply { postConcat(fromWindow(ancestor)) }
        return RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()).also(toAncestor::mapRect)
    }
}
