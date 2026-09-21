package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import android.content.Context
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.TextView

import java.util.Calendar
import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather

/**
 * The date, and the weather where SystemUI has it, as one line a face places
 * wherever its design wants it: above the time, below it, beside it. Each face
 * gives it its own look ([Look]), so a design owns its date rather than every
 * one taking the same row.
 */
class DateLine(context: Context, private val look: Look) {

    /** How a face writes its date line. */
    data class Look(
        /** Date skeleton, localized by the platform: "EEEEMMMMd" is "Saturday, September 13". */
        val skeleton: String = "EEEEMMMMd",
        /** Text height against the screen's short side. */
        val sizeToShortSide: Float = 0.045f,
        val weight: Int = 600,
        val allCaps: Boolean = false,
        val letterSpacing: Float = 0f,
        /** How opaque it is drawn against the face's colour. */
        val alpha: Float = 0.85f,
        val gravity: Int = android.view.Gravity.CENTER_HORIZONTAL,
        val showsWeather: Boolean = true,
    )

    private var weather: ClockWeather? = null

    val view: TextView = TextView(context).apply {
        includeFontPadding = false
        maxLines = 1
        gravity = look.gravity
        isAllCaps = look.allCaps
        letterSpacing = look.letterSpacing
        typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), look.weight, false)
        val metrics = context.resources.displayMetrics
        setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(metrics.widthPixels, metrics.heightPixels) * look.sizeToShortSide)
    }

    /** Writes the date as of now in the current locale. */
    fun refresh() {
        val now = Calendar.getInstance()
        val locale = Locale.getDefault()
        view.text = buildString {
            append(DateFormat.format(DateFormat.getBestDateTimePattern(locale, look.skeleton), now))
            weather?.takeIf { look.showsWeather }?.let {
                append(WEATHER_GAP)
                append(icon(it.condition))
                append(' ')
                append(it.temperature)
                append(if (it.celsius) "°C" else "°F")
            }
        }
    }

    fun setWeather(weather: ClockWeather?) {
        this.weather = weather
        refresh()
    }

    fun setColor(color: Int) {
        view.setTextColor(DozeInk.withAlpha(color, look.alpha))
    }

    private fun icon(condition: String): String = when {
        condition.contains("rain", true) || condition.contains("storm", true) -> "🌧️"
        condition.contains("snow", true) -> "❄️"
        condition.contains("cloud", true) || condition.contains("overcast", true) -> "☁️"
        condition.contains("clear", true) || condition.contains("sun", true) -> "☀️"
        else -> "☁️"
    }

    private companion object {
        const val WEATHER_GAP = "  "
    }
}
