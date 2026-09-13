package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    @Test
    fun smallestIsTheClassicShapeUnstretched() {
        assertEquals(
            "'opsz' 144.0, 'wght' 600.0, 'wdth' 100.0, 'XTRA' 468.0, 'XOPQ' 96.0, 'YOPQ' 79.0",
            IosClockScale.variationAt(0f),
        )
        assertEquals(1f, IosClockScale.stretchAt(0f, naturalNineAspect = 0.5f), 0f)
    }

    @Test
    fun largestIsThinAndStretchedToTheIosAspect() {
        assertEquals(
            "'opsz' 14.0, 'wght' 500.0, 'wdth' 50.0, 'XTRA' 468.0, 'XOPQ' 110.0, 'YOPQ' 40.0",
            IosClockScale.variationAt(1f),
        )
        assertEquals(2f, IosClockScale.stretchAt(1f, naturalNineAspect = 0.52f), 1e-5f)
    }

    @Test
    fun aFontAlreadyNarrowerThanIosIsNotStretched() {
        assertEquals(1f, IosClockScale.stretchAt(1f, naturalNineAspect = 0.24f), 0f)
    }

    @Test
    fun sizesOutsideTheRangeAreClamped() {
        assertEquals(IosClockScale.variationAt(0f), IosClockScale.variationAt(-1f))
        assertEquals(IosClockScale.variationAt(1f), IosClockScale.variationAt(3f))
        assertEquals(10f, IosClockScale.numeralHeightAt(2f, smallest = 2f, largest = 10f), 0f)
    }

    @Test
    fun numeralHeightGrowsLinearly() {
        assertEquals(6f, IosClockScale.numeralHeightAt(0.5f, smallest = 2f, largest = 10f), 1e-6f)
    }
}
