package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.ViewGroup

/**
 * Layout parameters for a view added by hand to one of a host's
 * `ConstraintLayout`s. The host ships ConstraintLayout shrunk, so its
 * parameters are made by the layout itself and their constraint fields set
 * by name.
 */
internal object ConstraintParams {

    const val PARENT = 0
    const val UNSET = -1

    /** Parameters [layout] makes for a child of [width] by [height]. */
    fun forChild(layout: ViewGroup, width: Int, height: Int): ViewGroup.MarginLayoutParams =
        ViewGroup::class.java.getDeclaredMethod("generateLayoutParams", ViewGroup.LayoutParams::class.java)
            .apply { isAccessible = true }
            .invoke(layout, ViewGroup.LayoutParams(width, height)) as ViewGroup.MarginLayoutParams

    /** Sets a constraint field, such as `topToTop`, to a view id or [PARENT] or [UNSET]. */
    fun ViewGroup.LayoutParams.constrain(name: String, value: Int) {
        javaClass.getField(name).setInt(this, value)
    }

    /** Resolves the constraint fields just set, as inflation does; skipped where R8 removed it. */
    fun ViewGroup.LayoutParams.validate() {
        runCatching { javaClass.getMethod("validate").invoke(this) }
    }
}
