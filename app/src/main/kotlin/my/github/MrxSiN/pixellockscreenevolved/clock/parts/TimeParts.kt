package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import java.util.Locale

/**
 * The hour and the minute as a lock screen clock writes them: no AM/PM marker
 * and no leading zero in 12-hour time, a padded hour in 24-hour time, and the
 * locale's own digits. Faces that set the two apart, one above the other or
 * either side of a subject, write each from here. Kept free of Android so the
 * rules can be unit tested.
 */
object TimeParts {

    data class Parts(val hour: String, val minute: String) {
        /** "9:41", the two on one line. */
        val joined: String get() = "$hour:$minute"
    }

    fun of(hourOfDay: Int, minute: Int, is24Hour: Boolean, locale: Locale): Parts {
        require(hourOfDay in 0..23) { "hourOfDay out of range: $hourOfDay" }
        require(minute in 0..59) { "minute out of range: $minute" }
        val hour = if (is24Hour) {
            String.format(locale, "%02d", hourOfDay)
        } else {
            String.format(locale, "%d", (hourOfDay % 12).takeIf { it != 0 } ?: 12)
        }
        return Parts(hour, String.format(locale, "%02d", minute))
    }
}
