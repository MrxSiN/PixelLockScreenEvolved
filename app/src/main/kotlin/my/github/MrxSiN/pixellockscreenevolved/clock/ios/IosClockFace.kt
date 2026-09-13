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

import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace

/**
 * The iOS clock: the date over a large, centred time.
 *
 * Both SystemUI faces are this drawing, shown at the size the host decides;
 * [showsDate] leaves the date out where SystemUI writes its own. The date keeps
 * its classic size however large the time grows, as on iOS.
 */
internal class IosClockFace(
    private val context: Context,
    showsDate: Boolean,
) : ResizableClockFace {

    private val timeView = IosTimeView(context)

    private val dateView: TextView? = if (showsDate) {
        TextView(context).apply {
            typeface = IosClockTypography.dateTypeface
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

        val timeParams = LinearLayout.LayoutParams(WRAP, WRAP)
        dateView?.let { date ->
            val metrics = context.resources.displayMetrics
            val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
            val dateSize = shortSide * IosClockScale.SMALLEST_TEXT_TO_SHORT_SIDE * DATE_TO_TIME_RATIO
            date.setTextSize(TypedValue.COMPLEX_UNIT_PX, dateSize)
            timeParams.topMargin = (dateSize * DATE_GAP_TO_DATE).toInt()
            addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        addView(timeView, timeParams)
    }

    override val drawsDate: Boolean = showsDate

    init {
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

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DATE_TO_TIME_RATIO = 0.21f
        const val DATE_GAP_TO_DATE = 0.35f

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
