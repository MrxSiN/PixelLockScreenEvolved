package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import java.util.Locale

import org.junit.Assert.assertEquals
import org.junit.Test

class IosTimeTextTest {

    private val us = Locale.US

    @Test
    fun twelveHourDropsLeadingZeroAndMarker() {
        assertEquals("9:41", IosTimeText.format(9, 41, is24Hour = false, locale = us))
        assertEquals("9:41", IosTimeText.format(21, 41, is24Hour = false, locale = us))
    }

    @Test
    fun twelveHourShowsMidnightAndNoonAsTwelve() {
        assertEquals("12:00", IosTimeText.format(0, 0, is24Hour = false, locale = us))
        assertEquals("12:05", IosTimeText.format(12, 5, is24Hour = false, locale = us))
    }

    @Test
    fun twentyFourHourPadsTheHour() {
        assertEquals("09:41", IosTimeText.format(9, 41, is24Hour = true, locale = us))
        assertEquals("00:00", IosTimeText.format(0, 0, is24Hour = true, locale = us))
        assertEquals("23:59", IosTimeText.format(23, 59, is24Hour = true, locale = us))
    }

    @Test
    fun digitsFollowTheLocale() {
        assertEquals("٩:٤١", IosTimeText.format(9, 41, is24Hour = false, locale = Locale.forLanguageTag("ar-EG")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHourOutOfRange() {
        IosTimeText.format(24, 0, is24Hour = true, locale = us)
    }
}
