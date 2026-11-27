package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.Drawable

import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail

/** The iOS lock screen clock: the date over a large, centred time, set in the Pixel clock's own numerals ([FlexClockNumerals]). */
class IosClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_IOS"
    override val name: String = "iOS"
    override val description: String = "iOS style clock with the date above the time"
    override val isResizable: Boolean = true
    override val isWidthAdjustable: Boolean = true

    /** Four, from the iOS default clock to the tallest iOS 26 clock ([IosClockScale]). */
    override val sizeSteps: Int = 4

    /**
     * Both faces are the iOS clock, date above the time: iOS keeps its clock
     * as it is when notifications arrive. SystemUI's own date row is hidden
     * beside it ([ClockFace.drawsDate]).
     */
    override fun createFace(context: Context, size: FaceSize): ClockFace = IosClockFace(context)

    /** A fixed 9:41, the time every iOS product shot shows, set in the face's own type. */
    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        paint.typeface = FlexClockNumerals.typeface
        paint.fontFeatureSettings = FlexClockNumerals.FEATURES
        paint.fontVariationSettings = FlexClockNumerals.variationFor(AdjustableWidthClockFace.DEFAULT_WIDTH)
        paint.letterSpacing = IosClockTypography.TIME_LETTER_SPACING
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = box.height() * THUMBNAIL_TEXT_HEIGHT
        val width = paint.measureText(ClockThumbnail.SAMPLE_TIME)
        val maxWidth = box.width() * THUMBNAIL_MAX_WIDTH
        if (width > maxWidth) paint.textSize *= maxWidth / width
        val baseline = box.exactCenterY() - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(ClockThumbnail.SAMPLE_TIME, box.exactCenterX(), baseline, paint)
    }

    private companion object {
        const val THUMBNAIL_TEXT_HEIGHT = 0.42f
        const val THUMBNAIL_MAX_WIDTH = 0.8f
    }
}
