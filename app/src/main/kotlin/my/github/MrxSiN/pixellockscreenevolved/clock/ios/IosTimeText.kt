package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import java.util.Locale

/**
 * The time as the iOS lock screen writes it.
 *
 * No AM/PM marker and no leading zero in 12-hour time; a padded hour in
 * 24-hour time. Kept free of Android so the rules can be unit tested.
 */
object IosTimeText {

    fun format(hourOfDay: Int, minute: Int, is24Hour: Boolean, locale: Locale): String {
        require(hourOfDay in 0..23) { "hourOfDay out of range: $hourOfDay" }
        require(minute in 0..59) { "minute out of range: $minute" }

        return if (is24Hour) {
            String.format(locale, "%02d:%02d", hourOfDay, minute)
        } else {
            val hour = (hourOfDay % 12).takeIf { it != 0 } ?: 12
            String.format(locale, "%d:%02d", hour, minute)
        }
    }
}
