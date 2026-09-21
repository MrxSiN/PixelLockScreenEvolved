package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * The chevron Android 17's settings put on a row that opens a page, before
 * the line that parts it from the row's switch: a thin, round-capped stroke
 * pointing to the end, measured off Settings → Display & touch.
 */
internal class ChevronDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = STROKE_DP * density
    }

    var color: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val box = bounds
        val halfWidth = GLYPH_WIDTH_DP * density / 2
        val halfHeight = GLYPH_HEIGHT_DP * density / 2
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        path.reset()
        // Mirrored where text runs right to left, so it always points to the end.
        val toEnd = if (layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) -1f else 1f
        path.moveTo(cx - halfWidth * toEnd, cy - halfHeight)
        path.lineTo(cx + halfWidth * toEnd, cy)
        path.lineTo(cx - halfWidth * toEnd, cy + halfHeight)
        canvas.drawPath(path, paint)
    }

    override fun getIntrinsicWidth(): Int = (SIZE_DP * density).toInt()

    override fun getIntrinsicHeight(): Int = (SIZE_DP * density).toInt()

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** The icon's box, as Material icons are sized. */
        const val SIZE_DP = 24f

        private const val GLYPH_WIDTH_DP = 6.5f
        private const val GLYPH_HEIGHT_DP = 13f
        private const val STROKE_DP = 2f

        /** Its dp from [TypedValue]'s point of view, for callers laying it out. */
        fun sizePx(context: Context): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, SIZE_DP, context.resources.displayMetrics).toInt()
    }
}
