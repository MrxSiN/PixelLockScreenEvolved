package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import java.util.Calendar
import java.util.Locale

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.clock.FontChoosingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace

/**
 * The iOS clock: the date over a large, centred time.
 *
 * Both SystemUI faces are this drawing, shown at the size and in the font the
 * host decides from [fonts]; [showsDate] leaves the date out where SystemUI
 * writes its own. The date keeps its classic size however large the time
 * grows, as on iOS.
 */
internal class IosClockFace(
    private val context: Context,
    private val fonts: List<IosNumeralFont>,
    showsDate: Boolean,
) : ResizableClockFace, FontChoosingClockFace {

    override val timeView = IosTimeView(context, fonts.first())

    private val dateView: TextView? = if (showsDate) {
        TextView(context).apply {
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
        }
    } else {
        null
    }

    override val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)

        dateView?.let { date ->
            val metrics = context.resources.displayMetrics
            val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX, shortSide * DATE_TEXT_TO_SHORT_SIDE)
            addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
    }

    override val drawsDate: Boolean = showsDate

    init {
        setFont(0)
        setColor(Color.WHITE)
        refresh()
    }

    override fun refresh() {
        val now = Calendar.getInstance()
        val locale = Locale.getDefault()

        timeView.setText(
            IosTimeText.format(
                hourOfDay = now[Calendar.HOUR_OF_DAY],
                minute = now[Calendar.MINUTE],
                is24Hour = DateFormat.is24HourFormat(context),
                locale = locale,
            ),
        )
        dateView?.text = DateFormat.format(
            DateFormat.getBestDateTimePattern(locale, IosClockTypography.DATE_SKELETON),
            now,
        )
    }

    override fun setColor(color: Int) {
        timeView.setColor(color)
        dateView?.setTextColor(withAlpha(color, IosClockTypography.DATE_ALPHA))
    }

    override fun setSize(size: Float) {
        timeView.setSize(size)
    }

    override fun setFont(index: Int) {
        val font = fonts[index.coerceIn(0, fonts.lastIndex)]
        timeView.setFont(font)
        dateView?.apply {
            typeface = font.dateTypeface
            // Settings equal to the last ones are ignored though the typeface dropped them.
            fontVariationSettings = null
            fontVariationSettings = font.dateVariation
        }
        spaceDateFromTime()
    }

    /** Keeps [IosClockTypography.DATE_TO_TIME_GAP_DP] between the date's baseline and the time, whatever the date's font. */
    private fun spaceDateFromTime() {
        val date = dateView ?: return
        val gap = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, IosClockTypography.DATE_TO_TIME_GAP_DP, context.resources.displayMetrics,
        )
        val params = timeView.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        // The date line ends its font's descent below the baseline.
        params.topMargin = (gap - date.paint.fontMetrics.descent).roundToInt().coerceAtLeast(0)
        timeView.layoutParams = params
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        /** The classic iOS date size, against the screen's short side. */
        const val DATE_TEXT_TO_SHORT_SIDE = 0.055f

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
