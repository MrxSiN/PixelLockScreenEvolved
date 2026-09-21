package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * The small picture Wallpaper & style lists a clock style with, drawn by the
 * style ([sketch]) in one colour.
 *
 * It follows the tint the picker gives it, and otherwise the picker's light or
 * dark theme, so it reads like the vector thumbnails beside it.
 */
class ClockThumbnail(
    context: Context,
    /** Draws the style into [box] of the canvas with [paint], whose colour is the thumbnail's. */
    private val sketch: (canvas: Canvas, box: Rect, paint: Paint) -> Unit,
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = FlexCut.typeface
        color = themeColor(context)
    }

    private var tint: ColorStateList? = null

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val color = paint.color
        val alpha = paint.alpha
        val filter = paint.colorFilter
        paint.reset()
        paint.isAntiAlias = true
        paint.typeface = FlexCut.typeface
        paint.color = color
        paint.alpha = alpha
        paint.colorFilter = filter
        sketch(canvas, bounds, paint)
    }

    override fun setTintList(tint: ColorStateList?) {
        this.tint = tint
        applyTint()
    }

    override fun onStateChange(state: IntArray): Boolean = applyTint()

    override fun isStateful(): Boolean = tint?.isStateful == true

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

    override fun getIntrinsicWidth(): Int = INTRINSIC_SIZE_PX

    override fun getIntrinsicHeight(): Int = INTRINSIC_SIZE_PX

    private fun applyTint(): Boolean {
        val color = tint?.getColorForState(state, paint.color) ?: return false
        if (color == paint.color) return false
        paint.color = color
        invalidateSelf()
        return true
    }

    companion object {
        /** The time every thumbnail shows. */
        const val SAMPLE_HOUR = "9"
        const val SAMPLE_MINUTE = "41"
        const val SAMPLE_TIME = "$SAMPLE_HOUR:$SAMPLE_MINUTE"

        private const val INTRINSIC_SIZE_PX = 256

        private fun themeColor(context: Context): Int {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
        }

        /**
         * Writes [lines] in [cut], stacked and centred in [box], as large as
         * [heightShare] of its height and [widthShare] of its width allow; each
         * line's horizontal place within the block comes from [align], -1 start,
         * 0 centre, 1 end.
         */
        fun stacked(
            canvas: Canvas,
            box: Rect,
            paint: Paint,
            cut: FlexCut,
            lines: List<String>,
            heightShare: Float = 0.7f,
            widthShare: Float = 0.8f,
            align: (index: Int) -> Int = { 0 },
            lineGap: Float = 0.08f,
        ) {
            paint.fontVariationSettings = cut.settings
            paint.textSize = PROBE
            val digits = Rect().also { paint.getTextBounds("0123456789", 0, 10, it) }
            val lineHeight = digits.height() / PROBE
            val widest = lines.maxOf { paint.measureText(it) } / PROBE
            val totalPerSize = lineHeight * (lines.size + lineGap * (lines.size - 1))
            paint.textSize = minOf(box.height() * heightShare / totalPerSize, box.width() * widthShare / widest)
            val step = lineHeight * paint.textSize * (1f + lineGap)
            val blockWidth = widest * paint.textSize
            val blockLeft = box.exactCenterX() - blockWidth / 2
            var top = box.exactCenterY() - lineHeight * paint.textSize * (lines.size + lineGap * (lines.size - 1)) / 2
            lines.forEachIndexed { index, line ->
                val width = paint.measureText(line)
                val left = blockLeft + (blockWidth - width) * (align(index) + 1) / 2f
                canvas.drawText(line, left, top - digits.top / PROBE * paint.textSize, paint)
                top += step
            }
        }

        private const val PROBE = 100f
    }
}
