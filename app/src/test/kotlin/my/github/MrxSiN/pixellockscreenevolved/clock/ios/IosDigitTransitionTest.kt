package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.IosDigitTransition.Place

class IosDigitTransitionTest {

    @Test
    fun onlyTheDigitsThatChangeChange() {
        assertEquals(
            listOf(Place(0, 0, false), Place(1, 1, false), Place(2, 2, true), Place(3, 3, true)),
            IosDigitTransition.between("1:09", "1:10"),
        )
    }

    @Test
    fun aNewHourDigitComesInOnTheLeft() {
        assertEquals(
            listOf(Place(null, 0, true), Place(0, 1, true), Place(1, 2, false), Place(2, 3, true), Place(3, 4, true)),
            IosDigitTransition.between("9:59", "10:00"),
        )
    }

    @Test
    fun anHourDigitThatGoesLeavesFromTheLeft() {
        assertEquals(
            listOf(Place(0, null, true), Place(1, 0, true), Place(2, 1, false), Place(3, 2, true), Place(4, 3, true)),
            IosDigitTransition.between("12:59", "1:00"),
        )
    }

    @Test
    fun theSameTextChangesNothing() {
        assertEquals(listOf(false, false, false, false), IosDigitTransition.between("7:45", "7:45").map { it.changes })
    }
}
