package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.TypedValue

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * How a face's shapes are inked: filled while awake, and traced in a thin
 * outline on the always-on display, so wide strokes do not keep lighting the
 * same pixels for hours ([outline] fades between the two).
 *
 * Variable fonts build glyphs from overlapping contours, which a plain stroke
 * would trace inside them, so the outline is stroked twice as wide and the
 * shapes' insides cut out of it, leaving only the line around their outer edge.
 */
class DozeInk(context: Context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2 * TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, OUTLINE_DP, context.resources.displayMetrics)
    }

    private val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    var color: Int = Color.WHITE

    /** From 0 filled to 1 outline only. */
    var outline: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    /** Room a view leaves around its shapes for half the outline and a pixel of anti-aliasing. */
    val inset: Int get() = ceil(stroke.strokeWidth / 2f).toInt() + 1

    /** Draws [shape] filled or in outline as the doze asks, as opaque as [alpha] makes it. */
    fun draw(canvas: Canvas, shape: Path, alpha: Float = 1f) {
        if (alpha <= 0f) return
        if (outline < 1f) {
            fill.color = withAlpha(color, (1f - outline) * alpha)
            canvas.drawPath(shape, fill)
        }
        if (outline > 0f) {
            canvas.saveLayerAlpha(null, (outline * alpha * OPAQUE).roundToInt())
            stroke.color = color
            canvas.drawPath(shape, stroke)
            canvas.drawPath(shape, inside)
            canvas.restore()
        }
    }

    companion object {
        /**
         * Width of the line a face is traced in on the always-on display: thin,
         * so few pixels stay lit, yet solid enough to read at its low brightness.
         */
        const val OUTLINE_DP: Float = 1.5f

        private const val OPAQUE = 255

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
