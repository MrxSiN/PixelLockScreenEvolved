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
 * Horizon, after ColorOS and OxygenOS: the time in one wide, hairline line,
 * with the date and the weather under it.
 */
class HorizonClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_HORIZON"
    override val name: String = "Horizon"
    override val description: String = "A wide hairline time on one line, after ColorOS and OxygenOS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> LineNumeralsFace(
            context, CUT, DATE, SizeRange(0.17f, 0.26f), dateGap = 0.22f, maxWidth = 0.92f, maxStretch = 1f,
            text = { it.joined },
        )
        FaceSize.SMALL -> OneLineSmallFace(context, CUT.copy(weight = 300f))
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        ClockThumbnail.stacked(canvas, box, paint, CUT.copy(weight = 300f), listOf(ClockThumbnail.SAMPLE_TIME), heightShare = 0.4f, widthShare = 0.9f)
    }

    private companion object {
        val CUT = FlexCut(weight = 180f, width = 130f)
        val DATE = DateLine.Look(skeleton = "MMMMdEEEE", sizeToShortSide = 0.04f, weight = 400, alpha = 0.8f)
    }
}
