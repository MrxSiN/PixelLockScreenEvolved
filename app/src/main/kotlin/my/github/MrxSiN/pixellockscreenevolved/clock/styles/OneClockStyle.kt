package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace

/**
 * One, after One UI's lock screen: tall, light, slightly narrow figures, the
 * hour over the minute, centred under the date.
 */
class OneClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_ONE"
    override val name: String = "One"
    override val description: String = "Light stacked figures centred under the date, after One UI"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> StackedNumeralsFace(
            context, CUT, DATE, Gravity.CENTER_HORIZONTAL, SizeRange(0.3f, 0.46f),
            lineGap = LINE_GAP, dateGap = 0.1f, blockWidth = 0.9f,
        )
        FaceSize.SMALL -> OneLineSmallFace(context, CUT)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        ClockThumbnail.stacked(
            canvas, box, paint, CUT, listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE),
            heightShare = 0.8f, lineGap = LINE_GAP,
        )
    }

    private companion object {
        val CUT = FlexCut(weight = 330f, width = 88f)
        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.042f, weight = 500)
        const val LINE_GAP = 0.1f
    }
}
