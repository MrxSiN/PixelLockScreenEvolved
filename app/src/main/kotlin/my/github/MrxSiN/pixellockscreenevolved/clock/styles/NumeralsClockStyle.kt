package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralView
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Numerals: giant figures that play Roboto Flex's axes against each other. The
 * hour is as heavy and wide as the font goes; the minute starts the hour light
 * and narrow and grows heavier and wider, and leans upright, as the hour runs
 * out ([minuteCut]), so the face changes through every hour.
 */
class NumeralsClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_NUMERALS"
    override val name: String = "Numerals"
    override val description: String = "Giant figures whose weight and width follow the minute"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        FaceSize.SMALL -> OneLineSmallFace(context, HOUR_CUT)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        ClockThumbnail.stacked(
            canvas, box, paint, HOUR_CUT, listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE),
            heightShare = 0.8f, widthShare = 0.86f, lineGap = LINE_GAP,
        )
    }

    private class Face(context: Context) : NumeralClockFace(context, DateLine(context, DATE)), ResizableClockFace {

        private val hour = NumeralView(context, HOUR_CUT)
        private val minute = NumeralView(context, minuteCut(0))
        private val time = column(Gravity.CENTER_HORIZONTAL, hour, minute)

        override val numerals: List<NumeralView> = listOf(hour, minute)
        override val view: View = column(Gravity.CENTER_HORIZONTAL, time, requireNotNull(date).view)
        override val timeView: View get() = time

        init {
            setSize(0f)
            refresh()
        }

        override fun write(parts: TimeParts.Parts) {
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setCut(minuteCut(parts.minute.filter(Char::isDigit).toIntOrNull() ?: 0))
            minute.setText(parts.minute)
        }

        override fun setSize(size: Float) {
            val height = shortSide * lerp(SMALLEST, LARGEST, size)
            listOf(hour, minute).forEach { it.fit(height, maxWidth = shortSide * MAX_WIDTH) }
            spaceAbove(minute, height * LINE_GAP)
            spaceAbove(requireNotNull(date).view, height * DATE_GAP)
        }
    }

    private companion object {
        val HOUR_CUT = FlexCut(weight = 1000f, width = 151f)
        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.04f, weight = 700)

        /** The minute's cut at [minute] of the hour: light, narrow and slanted at its start, heavy, wide and upright at its end. */
        fun minuteCut(minute: Int): FlexCut {
            val t = (minute.coerceIn(0, 59) / 59f)
            return FlexCut(weight = 150f + 850f * t, width = 25f + 126f * t, slant = -10f * (1f - t))
        }

        const val SMALLEST = 0.34f
        const val LARGEST = 0.52f
        const val MAX_WIDTH = 0.94f
        const val LINE_GAP = 0.04f
        const val DATE_GAP = 0.14f
    }
}
