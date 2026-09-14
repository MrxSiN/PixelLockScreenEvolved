package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

/** Pictures of views, and how shown a view looks, for the unlock overlays. */
internal object ViewPictures {

    /** [view] drawn as it looks now, without its own or its parents' fade; null while it has no size. */
    fun of(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    /**
     * How opaque SystemUI has made [view], with its parents' alpha applied: 0 when
     * it is not shown at all. Its transition alpha, which this module uses to hold
     * views hidden, is left out.
     */
    fun visibleAlpha(view: View): Float {
        if (!view.isShown) return 0f
        return view.alpha * parentAlpha(view)
    }

    /** The alpha [view]'s parents apply to it. */
    fun parentAlpha(view: View): Float {
        var alpha = 1f
        var parent = view.parent
        while (parent is View) {
            alpha *= parent.alpha
            parent = parent.parent
        }
        return alpha
    }

    fun View.screenLocation(): IntArray = IntArray(2).also(::getLocationOnScreen)

    /** An image of [picture], [width] by [height], placed by its top left corner and scaled from there. */
    fun image(anchor: View, picture: Bitmap, width: Int = picture.width, height: Int = picture.height) =
        ImageView(anchor.context).apply {
            setImageBitmap(picture)
            pivotX = 0f
            pivotY = 0f
            layoutParams = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START)
        }
}
