package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * A small icon of the Position tab's own, drawn here: a drawable of this
 * module's own resources cannot be loaded in Wallpaper & style, which may not
 * see this module's package. It takes the tint it is given, as a vector
 * drawable would.
 *
 * - [Glyph.TAB]: Material Symbols "height" (a double arrow between two bars),
 *   the tab's icon.
 * - [Glyph.DRAG]: a fingertip with an arrow above and below, beside the hint
 *   that the clock is dragged up and down the preview ([PickerDragHint]).
 */
internal class PositionIconDrawable(private val sizePx: Int, glyph: Glyph = Glyph.TAB) : Drawable() {

    enum class Glyph { TAB, DRAG }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val path = Path().apply {
        when (glyph) {
            Glyph.TAB -> {
                // M13,6.99h3L12,3L8,6.99h3v10.02H8L12,21l4,-3.99h-3z, then the two bars.
                moveTo(13f, 6.99f)
                lineTo(16f, 6.99f)
                lineTo(12f, 3f)
                lineTo(8f, 6.99f)
                lineTo(11f, 6.99f)
                lineTo(11f, 17.01f)
                lineTo(8f, 17.01f)
                lineTo(12f, 21f)
                lineTo(16f, 17.01f)
                lineTo(13f, 17.01f)
                close()
                addRect(4f, 0.5f, 20f, 2f, Path.Direction.CW)
                addRect(4f, 22f, 20f, 23.5f, Path.Direction.CW)
            }
            Glyph.DRAG -> {
                fillType = Path.FillType.EVEN_ODD
                // The fingertip, and a ring around it.
                addCircle(12f, 12f, 3f, Path.Direction.CW)
                addCircle(12f, 12f, 6f, Path.Direction.CW)
                addCircle(12f, 12f, 4.5f, Path.Direction.CW)
                // Up and down.
                moveTo(12f, 0.5f)
                lineTo(16f, 4.5f)
                lineTo(8f, 4.5f)
                close()
                moveTo(12f, 23.5f)
                lineTo(8f, 19.5f)
                lineTo(16f, 19.5f)
                close()
            }
        }
    }
    private var tint: ColorStateList? = null

    override fun getIntrinsicWidth(): Int = sizePx

    override fun getIntrinsicHeight(): Int = sizePx

    override fun draw(canvas: Canvas) {
        val unit = bounds.width() / VIEWPORT
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(unit, unit)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        applyTint()
    }

    override fun isStateful(): Boolean = tint?.isStateful == true

    override fun onStateChange(state: IntArray): Boolean = applyTint()

    private fun applyTint(): Boolean {
        val color = tint?.getColorForState(state, Color.WHITE) ?: Color.WHITE
        if (paint.color == color) return false
        paint.color = color
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val VIEWPORT = 24f
    }
}
