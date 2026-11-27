package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ExpressiveClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace

/**
 * What every face drawn in [NumeralView]s shares: reading the time, colouring
 * every part, moving the numerals through Roboto Flex's axes into and out of
 * the always-on display, as the lock screen appears and as the charger is
 * plugged in ([NumeralView]), and writing the date where the face has one ([date]).
 *
 * A face says only how it is laid out: its views, in [layout], and how the
 * time is split across them ([write]). One whose size can be chosen also
 * implements [my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace].
 * Subclasses call [refresh] once their views exist.
 */
abstract class NumeralClockFace(
    protected val context: Context,
    /** The face's own date line, or null for a face that leaves the date to SystemUI. */
    protected val date: DateLine?,
) : ClockFace, DozingClockFace, ExpressiveClockFace, WeatherClockFace {

    protected val shortSide: Float = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()

    /** The screen's longer side, for a face laid out down the whole lock screen. */
    protected val screenHeight: Float = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }.toFloat()

    /** Every line of numerals the face draws. */
    protected abstract val numerals: List<NumeralView>

    /** Shows [parts] in the face's lines. */
    protected abstract fun write(parts: TimeParts.Parts)

    override val drawsDate: Boolean get() = date != null

    final override fun refresh() {
        val now = Calendar.getInstance()
        write(TimeParts.of(now[Calendar.HOUR_OF_DAY], now[Calendar.MINUTE], DateFormat.is24HourFormat(context), Locale.getDefault()))
        date?.refresh()
    }

    override fun setColor(color: Int) {
        numerals.forEach { it.setColor(color) }
        date?.setColor(color)
    }

    override fun setDoze(fraction: Float) {
        numerals.forEach { it.setDoze(fraction) }
    }

    override fun enter() = numerals.forEach(NumeralView::playEnter)

    override fun charge() = numerals.forEach(NumeralView::playCharge)

    override fun setWeather(weather: ClockWeather?) {
        date?.setWeather(weather)
    }

    /** A vertical stack of [children], each laid out as [gravity] places it, wrapping them. */
    protected fun column(gravity: Int, vararg children: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        this.gravity = gravity
        layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
        children.forEach { addView(it, LinearLayout.LayoutParams(WRAP, WRAP).apply { this.gravity = gravity }) }
    }

    /** [view]'s space above it, in pixels. */
    protected fun spaceAbove(view: View, pixels: Float) {
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            it.topMargin = pixels.toInt()
            view.layoutParams = it
        }
    }

    protected companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

        fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)
    }
}

/**
 * The small face every numeral style shares: the time on one line, sized to
 * sit beside notifications as a Pixel small clock does, in the style's own cut.
 * It leaves the date to SystemUI, which always writes its row under a small clock.
 *
 * The time is centred in whatever height the face is given: the host gives a
 * small face the band the lock screen lays its own small clock out in, so that
 * what the lock screen puts below it keeps the spacing it was laid out with.
 */
class OneLineSmallFace(
    context: Context,
    cut: FlexCut,
    /**
     * The tallest the numerals may stand, against the screen's short side. The line is as wide as the text
     * column ([MAX_WIDTH]) lets it be up to this height, so beside notifications the time spans the column, as
     * iOS's and Hyper's do, rather than standing small in the middle of empty space; a narrow cut reaches
     * this height first.
     */
    numeralToShortSide: Float = FILL_HEIGHT,
    /** Space between the figures, in ems ([NumeralView.setTracking]), for a heavy, narrow cut. */
    tracking: Float = 0f,
) : NumeralClockFace(context, date = null) {

    private val time = NumeralView(context, cut).apply { setTracking(tracking); fit(shortSide * numeralToShortSide, maxWidth = shortSide * MAX_WIDTH) }

    override val numerals: List<NumeralView> = listOf(time)

    override val view: View = column(android.view.Gravity.CENTER, time)

    override val timeView: View get() = time

    init {
        refresh()
    }

    override fun write(parts: TimeParts.Parts) = time.setText(parts.joined)

    companion object {
        /** The height a Pixel small clock's figures stand at, against the short side. */
        const val PIXEL_NUMERAL_TO_SHORT_SIDE = 0.13f

        /** The tallest a small face's numerals stand, against the short side. */
        const val FILL_HEIGHT = 0.24f

        /** As wide as a small face runs, against the short side: the lock screen's text column. */
        const val MAX_WIDTH = 0.86f
    }
}
