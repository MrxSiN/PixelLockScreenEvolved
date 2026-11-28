package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The subject editor's icons, stroked in code on a 24-unit grid: the editor runs in SystemUI,
 * which cannot load this module's drawables.
 */
internal class EditorIcon(private val glyph: Glyph, color: Int) : Drawable() {

    enum class Glyph { CLOSE, UNDO, REDO, ADD, ERASE, RESTORE, FIT, CHECK, COMPARE, RESET }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    private val path = Path().apply {
        when (glyph) {
            Glyph.CLOSE -> {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
            Glyph.UNDO, Glyph.REDO -> {
                moveTo(8f, 5f); lineTo(4f, 9f); lineTo(8f, 13f)
                moveTo(4f, 9f); lineTo(14.5f, 9f)
                arcTo(RectF(9f, 9f, 20f, 20f), -90f, 180f)
                lineTo(10f, 20f)
            }
            Glyph.ADD -> {
                addCircle(12f, 12f, 8f, Path.Direction.CW)
                moveTo(12f, 8.5f); lineTo(12f, 15.5f)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            }
            Glyph.ERASE -> {
                addCircle(12f, 12f, 8f, Path.Direction.CW)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
            }
            Glyph.RESTORE -> {
                arcTo(RectF(4.5f, 4.5f, 19.5f, 19.5f), -60f, -290f, true)
                moveTo(16.5f, 3.5f); lineTo(16f, 6.8f); lineTo(19.3f, 7.3f)
            }
            Glyph.CHECK -> {
                moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f)
            }
            Glyph.COMPARE -> {
                // A frame split down the middle, its left half filled in by strokes: before and after.
                addRoundRect(RectF(4f, 5f, 20f, 19f), 2.5f, 2.5f, Path.Direction.CW)
                moveTo(12f, 3f); lineTo(12f, 21f)
                moveTo(7f, 9f); lineTo(9f, 9f)
                moveTo(7f, 12f); lineTo(9f, 12f)
                moveTo(7f, 15f); lineTo(9f, 15f)
            }
            Glyph.RESET -> {
                arcTo(RectF(5f, 5f, 19f, 19f), 200f, 290f, true)
                moveTo(4.5f, 6f); lineTo(5.6f, 10f); lineTo(9.5f, 8.8f)
            }
            Glyph.FIT -> {
                moveTo(4f, 9f); lineTo(4f, 4f); lineTo(9f, 4f)
                moveTo(15f, 4f); lineTo(20f, 4f); lineTo(20f, 9f)
                moveTo(20f, 15f); lineTo(20f, 20f); lineTo(15f, 20f)
                moveTo(9f, 20f); lineTo(4f, 20f); lineTo(4f, 15f)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        val box = bounds
        val saved = canvas.save()
        canvas.translate(box.left.toFloat(), box.top.toFloat())
        canvas.scale(box.width() / GRID, box.height() / GRID)
        if (glyph == Glyph.REDO) canvas.scale(-1f, 1f, GRID / 2f, GRID / 2f)
        canvas.drawPath(path, paint)
        canvas.restoreToCount(saved)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun setTintList(tint: android.content.res.ColorStateList?) {
        tint?.let { paint.color = it.getColorForState(state, it.defaultColor) }
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val GRID = 24f
    }
}
