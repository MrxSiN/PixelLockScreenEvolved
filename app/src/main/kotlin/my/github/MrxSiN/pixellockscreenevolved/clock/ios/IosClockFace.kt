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

import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace

/**
 * The iOS clock drawn with plain views.
 *
 * The large face is the iOS lock screen: the date over a large time, centred,
 * at whatever size was chosen. The small face is the time alone, fitted to the
 * fixed height SystemUI gives the small clock, so it ignores the chosen size;
 * the date line there is SystemUI's own smartspace.
 */
internal class IosClockFace(
    private val context: Context,
    faceSize: FaceSize,
) : ResizableClockFace {

    private val sizer: IosTimeSizer? =
        if (faceSize == FaceSize.LARGE) IosTimeSizer(context.resources.displayMetrics) else null

    private var size = 0f

    private val timeView = TextView(context).apply {
        typeface = IosClockTypography.timeTypeface
        fontVariationSettings = IosClockTypography.timeVariation()
        letterSpacing = IosClockTypography.TIME_LETTER_SPACING
        includeFontPadding = false
        maxLines = 1
        gravity = Gravity.CENTER
    }

    private val dateView: TextView? = if (faceSize == FaceSize.LARGE) {
        TextView(context).apply {
            typeface = IosClockTypography.dateTypeface
            includeFontPadding = false
            maxLines = 1
            gravity = Gravity.CENTER
        }
    } else {
        null
    }

    override val view: View = when (faceSize) {
        FaceSize.LARGE -> largeFace(requireNotNull(dateView), requireNotNull(sizer))
        FaceSize.SMALL -> smallFace()
    }

    override val drawsDate: Boolean = dateView != null

    init {
        setColor(Color.WHITE)
        refresh()
    }

    override fun refresh() {
        val now = Calendar.getInstance()
        val locale = Locale.getDefault()

        timeView.text = IosTimeText.format(
            hourOfDay = now[Calendar.HOUR_OF_DAY],
            minute = now[Calendar.MINUTE],
            is24Hour = DateFormat.is24HourFormat(context),
            locale = locale,
        )
        dateView?.text = DateFormat.format(
            DateFormat.getBestDateTimePattern(locale, IosClockTypography.DATE_SKELETON),
            now,
        )
        sizer?.apply(timeView, size)
    }

    override fun setColor(color: Int) {
        timeView.setTextColor(color)
        dateView?.setTextColor(withAlpha(color, IosClockTypography.DATE_ALPHA))
    }

    override fun setSize(size: Float) {
        if (sizer == null || this.size == size) return
        this.size = size
        sizer.apply(timeView, size)
    }

    private fun largeFace(date: TextView, sizer: IosTimeSizer): View {
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizer.smallestTextSize * DATE_TO_TIME_RATIO)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(timeView, LinearLayout.LayoutParams(WRAP, WRAP))
        }
    }

    /**
     * The time sized to the band SystemUI reserves for the small clock, read
     * from the host's own dimension so the notifications below never overlap.
     */
    private fun smallFace(): View = timeView.apply {
        layoutParams = ViewGroup.LayoutParams(MATCH, WRAP)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, smallClockBand() * SMALL_TIME_TO_BAND_RATIO)
    }

    private fun smallClockBand(): Float {
        val resources = context.resources
        val id = resources.getIdentifier(SMALL_CLOCK_HEIGHT, "dimen", context.packageName)
        return if (id != 0) {
            resources.getDimension(id)
        } else {
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, SMALL_CLOCK_HEIGHT_FALLBACK_DP, resources.displayMetrics)
        }
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val SMALL_CLOCK_HEIGHT = "small_clock_height"
        const val SMALL_CLOCK_HEIGHT_FALLBACK_DP = 112f
        const val SMALL_TIME_TO_BAND_RATIO = 0.7f

        /** The date keeps its classic size however large the time grows, as on iOS. */
        const val DATE_TO_TIME_RATIO = 0.21f

        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
