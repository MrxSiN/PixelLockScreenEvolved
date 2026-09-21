package my.github.MrxSiN.pixellockscreenevolved.clock.parts

import java.util.Locale

import org.junit.Assert.assertEquals
import org.junit.Test

class TimePartsTest {

    private val us = Locale.US

    @Test
    fun twelveHourDropsTheHoursLeadingZeroButPadsTheMinute() {
        assertEquals(TimeParts.Parts("9", "05"), TimeParts.of(21, 5, is24Hour = false, locale = us))
    }

    @Test
    fun twelveHourShowsMidnightAsTwelve() {
        assertEquals("12:00", TimeParts.of(0, 0, is24Hour = false, locale = us).joined)
    }

    @Test
    fun twentyFourHourPadsTheHour() {
        assertEquals(TimeParts.Parts("09", "41"), TimeParts.of(9, 41, is24Hour = true, locale = us))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMinuteOutOfRange() {
        TimeParts.of(9, 60, is24Hour = true, locale = us)
    }
}
