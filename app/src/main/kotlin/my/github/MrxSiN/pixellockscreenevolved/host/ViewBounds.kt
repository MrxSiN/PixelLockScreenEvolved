package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View

/** Where views are drawn, with every move and scale of theirs and their parents' applied. */
internal object ViewBounds {

    /** [view]'s bounds in its window. */
    fun inWindow(view: View): RectF {
        val toWindow = Matrix().also(view::transformMatrixToGlobal)
        return RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()).also(toWindow::mapRect)
    }

    /** [view]'s bounds in [ancestor]'s own pixels, with every move and scale between them applied and none above. */
    fun inAncestor(ancestor: View, view: View): RectF {
        val toAncestor = Matrix().also(view::transformMatrixToGlobal)
        val windowToAncestor = Matrix().also { Matrix().also(ancestor::transformMatrixToGlobal).invert(it) }
        toAncestor.postConcat(windowToAncestor)
        return RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()).also(toAncestor::mapRect)
    }
}
