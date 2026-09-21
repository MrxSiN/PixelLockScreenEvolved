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
 * Stacked: a large hour over the minute, set flush to the start as Material
 * sets its headlines, with the date written under them.
 */
class StackedClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_STACKED"
    override val name: String = "Stacked"
    override val description: String = "A large hour over the minute, with the date below"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        FaceSize.SMALL -> OneLineSmallFace(context, CUT)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        ClockThumbnail.stacked(
            canvas, box, paint, CUT, listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE),
            align = { -1 }, lineGap = LINE_GAP,
        )
    }

    private class Face(context: Context) : NumeralClockFace(context, DateLine(context, DATE)), ResizableClockFace {

        private val hour = NumeralView(context, CUT).apply { gravity = Gravity.START }
        private val minute = NumeralView(context, CUT).apply { gravity = Gravity.START }
        private val time = column(Gravity.START, hour, minute)

        override val numerals: List<NumeralView> = listOf(hour, minute)
        override val view: View = column(Gravity.START, time, requireNotNull(date).view).apply {
            minimumWidth = (shortSide * BLOCK_WIDTH).toInt()
        }
        override val timeView: View get() = time

        init {
            setSize(0f)
            refresh()
        }

        override fun write(parts: TimeParts.Parts) {
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setText(parts.minute)
        }

        override fun setSize(size: Float) {
            val height = shortSide * lerp(SMALLEST, LARGEST, size)
            listOf(hour, minute).forEach { it.fit(height, maxWidth = shortSide * BLOCK_WIDTH) }
            spaceAbove(minute, height * LINE_GAP)
            spaceAbove(requireNotNull(date).view, height * DATE_GAP)
        }
    }

    private companion object {
        val CUT = FlexCut(weight = 560f, width = 100f)
        val DATE = DateLine.Look(skeleton = "EEEMMMd", weight = 500, gravity = Gravity.START, alpha = 0.9f)

        /** Numeral height of each line, smallest and largest, against the screen's short side. */
        const val SMALLEST = 0.28f
        const val LARGEST = 0.42f

        /** How wide the block is kept, against the short side, so its start lines up with the lock screen's. */
        const val BLOCK_WIDTH = 0.86f

        /** Space between the lines, and above the date, against the numeral height. */
        const val LINE_GAP = 0.1f
        const val DATE_GAP = 0.16f
    }
}
