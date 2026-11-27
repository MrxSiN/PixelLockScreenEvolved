package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.drawable.Drawable

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace

/**
 * Cinema: the hour and the minute side by side as one towering, condensed
 * title, stretched taller than the font draws them, with a spaced-out credit
 * line for the date. Made to stand behind the photo's subject: the figures are
 * tall enough that a head can rise through them and every digit still read.
 */
class CinemaClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_CINEMA"
    override val name: String = "Cinema"
    override val description: String = "Towering condensed figures, a title card for the depth effect"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> LineNumeralsFace(
            context, CUT, DATE, SizeRange(0.5f, 0.78f), dateGap = 0.06f, maxWidth = 0.94f, maxStretch = 1.7f,
            text = { it.hour.padStart(2, '0') + it.minute }, tracking = 0.06f,
        )
        FaceSize.SMALL -> OneLineSmallFace(context, CUT, tracking = 0.06f)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        val sample = ClockThumbnail.SAMPLE_HOUR.padStart(2, '0') + ClockThumbnail.SAMPLE_MINUTE
        ClockThumbnail.stacked(canvas, box, paint, CUT, listOf(sample), heightShare = 0.8f, widthShare = 0.7f)
    }

    private companion object {
        val CUT = FlexCut(weight = 880f, width = 25f)
        val DATE = DateLine.Look(skeleton = "EEEEMMMMd", sizeToShortSide = 0.032f, weight = 500, allCaps = true, letterSpacing = 0.4f, alpha = 0.85f)
    }
}
