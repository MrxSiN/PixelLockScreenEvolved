package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Method

/**
 * The few `androidx.constraintlayout.widget.ConstraintSet` calls clock
 * placement needs, made on SystemUI's own copy of the class.
 *
 * The module does not package ConstraintLayout, and could not hand SystemUI an
 * instance of its own copy anyway, so the set SystemUI passes in is edited
 * through reflection.
 */
internal class ConstraintSetEditor(private val set: Any) {

    private val type = set.javaClass

    fun constrainWidth(viewId: Int, width: Int) {
        method("constrainWidth", INT, INT).invoke(set, viewId, width)
    }

    fun constrainHeight(viewId: Int, height: Int) {
        method("constrainHeight", INT, INT).invoke(set, viewId, height)
    }

    fun clear(viewId: Int, side: Int) {
        method("clear", INT, INT).invoke(set, viewId, side)
    }

    fun connect(viewId: Int, side: Int, toViewId: Int, toSide: Int, margin: Int = 0) {
        method("connect", INT, INT, INT, INT, INT).invoke(set, viewId, side, toViewId, toSide, margin)
    }

    /** Pins both horizontal sides to the parent, which centres the view. */
    fun centerHorizontally(viewId: Int) {
        connect(viewId, START, PARENT_ID, START)
        connect(viewId, END, PARENT_ID, END)
    }

    private fun method(name: String, vararg parameters: Class<*>): Method =
        type.getMethod(name, *parameters)

    companion object {
        const val PARENT_ID = 0
        const val TOP = 3
        const val BOTTOM = 4
        const val START = 6
        const val END = 7
        const val MATCH_CONSTRAINT = 0
        const val WRAP_CONTENT = -2

        private val INT = Int::class.java
    }
}
