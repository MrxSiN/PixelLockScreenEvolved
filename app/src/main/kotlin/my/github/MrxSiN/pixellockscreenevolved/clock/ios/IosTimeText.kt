package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import java.util.Locale

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * The time as the iOS lock screen writes it.
 *
 * No AM/PM marker and no leading zero in 12-hour time; a padded hour in
 * 24-hour time. Kept free of Android so the rules can be unit tested.
 */
object IosTimeText {

    fun format(hourOfDay: Int, minute: Int, is24Hour: Boolean, locale: Locale): String =
        TimeParts.of(hourOfDay, minute, is24Hour, locale).joined
}
