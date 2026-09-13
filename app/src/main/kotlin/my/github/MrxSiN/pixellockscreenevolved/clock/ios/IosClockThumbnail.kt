package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The picture Wallpaper & style lists the iOS clock with: a fixed 9:41, the
 * time every iOS product shot shows, set in the face's own type.
 *
 * It follows the tint the picker gives it, and otherwise the picker's light or
 * dark theme, so it reads like the vector thumbnails beside it.
 */
internal class IosClockThumbnail(context: Context) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = RobotoFlexNumerals.typeface
        fontVariationSettings = RobotoFlexNumerals.variationFor(size = 0f, stretch = 1f)
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
        textAlign = Paint.Align.CENTER
        color = themeColor(context)
    }

    private var tint: ColorStateList? = null

    override fun draw(canvas: Canvas) {
        val box = bounds
        if (box.isEmpty) return

        paint.textSize = box.height() * TEXT_HEIGHT_FRACTION
        val width = paint.measureText(SAMPLE_TIME)
        val maxWidth = box.width() * MAX_WIDTH_FRACTION
        if (width > maxWidth) paint.textSize *= maxWidth / width

        val baseline = box.exactCenterY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(SAMPLE_TIME, box.exactCenterX(), baseline, paint)
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

    private companion object {
        const val SAMPLE_TIME = "9:41"
        const val TEXT_HEIGHT_FRACTION = 0.42f
        const val MAX_WIDTH_FRACTION = 0.8f
        const val INTRINSIC_SIZE_PX = 256

        fun themeColor(context: Context): Int {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) Color.WHITE else Color.BLACK
        }
    }
}
