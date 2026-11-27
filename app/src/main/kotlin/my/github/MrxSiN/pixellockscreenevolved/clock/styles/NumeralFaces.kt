package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.view.Gravity
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralView
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/** Numeral heights, against the screen's short side, at the size slider's two ends. */
internal class SizeRange(val smallest: Float, val largest: Float)

/**
 * The hour over the minute, with the date above them, aligned as [gravity]
 * says: the large face of the stacked styles ([HyperClockStyle], [OneClockStyle]).
 * The hour keeps its leading zero, as stacked lock screen clocks write it.
 */
internal class StackedNumeralsFace(
    context: Context,
    cut: FlexCut,
    dateLook: DateLine.Look,
    private val gravity: Int,
    private val sizes: SizeRange,
    /** Space between the lines, and under the date, against the numeral height. */
    private val lineGap: Float,
    private val dateGap: Float,
    /** Widest the block may be, against the short side; a start-aligned block keeps this width so its start lines up with the lock screen's. */
    private val blockWidth: Float,
) : NumeralClockFace(context, DateLine(context, dateLook)), ResizableClockFace {

    private val hour = NumeralView(context, cut).apply { gravity = this@StackedNumeralsFace.gravity }
    private val minute = NumeralView(context, cut).apply { gravity = this@StackedNumeralsFace.gravity }
    private val time = column(gravity, hour, minute)

    override val numerals: List<NumeralView> = listOf(hour, minute)
    override val view: View = column(gravity, requireNotNull(date).view, time).apply {
        if (gravity == Gravity.START) minimumWidth = (shortSide * blockWidth).toInt()
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
        val height = shortSide * lerp(sizes.smallest, sizes.largest, size)
        numerals.forEach { it.fit(height, maxWidth = shortSide * blockWidth) }
        spaceAbove(minute, height * lineGap)
        spaceAbove(time, height * dateGap)
    }
}

/**
 * The time on one line with the date under it: the large face of the
 * one-line styles ([HorizonClockStyle], [CinemaClockStyle]). [text] says how
 * the line is written, with or without its colon.
 */
internal class LineNumeralsFace(
    context: Context,
    cut: FlexCut,
    dateLook: DateLine.Look,
    private val sizes: SizeRange,
    private val dateGap: Float,
    /** Widest the line may be, against the short side. */
    private val maxWidth: Float,
    /** How far the figures may be drawn taller than designed where the width caps them ([NumeralView.fit]). */
    private val maxStretch: Float,
    private val text: (TimeParts.Parts) -> String,
    /** Space between the figures, in ems ([NumeralView.setTracking]). */
    tracking: Float = 0f,
) : NumeralClockFace(context, DateLine(context, dateLook)), ResizableClockFace {

    private val time = NumeralView(context, cut).apply { setTracking(tracking) }

    override val numerals: List<NumeralView> = listOf(time)
    override val view: View = column(Gravity.CENTER_HORIZONTAL, time, requireNotNull(date).view)
    override val timeView: View get() = time

    init {
        setSize(0f)
        refresh()
    }

    override fun write(parts: TimeParts.Parts) = time.setText(text(parts))

    override fun setSize(size: Float) {
        val height = shortSide * lerp(sizes.smallest, sizes.largest, size)
        time.fit(height, maxWidth = shortSide * maxWidth, maxStretch = maxStretch)
        spaceAbove(requireNotNull(date).view, height * dateGap)
    }
}
