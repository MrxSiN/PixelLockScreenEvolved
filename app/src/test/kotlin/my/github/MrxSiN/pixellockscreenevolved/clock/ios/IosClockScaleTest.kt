package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import org.junit.Assert.assertEquals
import org.junit.Test

class IosClockScaleTest {

    private val shortSide = 1000f
    private val numeral = 0.7f
    private val column = 900f
    private val stock = shortSide * IosClockScale.STOCK_NUMERAL_TO_SHORT_SIDE

    @Test
    fun everySizeSpansTheColumn() {
        listOf(0f, 0.5f, 1f).forEach { size ->
            val fit = IosClockScale.spanning(size, shortSide, numeral, inkWidthPerTextSize = 2.5f, columnWidth = column)
            assertEquals(column, fit.textSize * 2.5f, 1e-2f)
        }
    }

    @Test
    fun theHeightRunsFromTheStockClockToTheLargest() {
        val smallest = IosClockScale.spanning(0f, shortSide, numeral, 2.5f, column)
        val largest = IosClockScale.spanning(1f, shortSide, numeral, 2.5f, column)
        assertEquals(stock, smallest.textSize * numeral * smallest.stretch, 1e-2f)
        assertEquals(stock * IosClockScale.LARGEST_TO_STOCK, largest.textSize * numeral * largest.stretch, 1e-2f)
    }

    @Test
    fun aWiderTimeIsSetSmallerButStretchedToTheSameHeight() {
        val narrow = IosClockScale.spanning(0.4f, shortSide, numeral, 2f, column)
        val wide = IosClockScale.spanning(0.4f, shortSide, numeral, 3f, column)
        assert(wide.textSize < narrow.textSize)
        assertEquals(narrow.textSize * numeral * narrow.stretch, wide.textSize * numeral * wide.stretch, 1e-2f)
    }

    @Test
    fun beforeAnyTimeTheStockHeightIsSetUnstretched() {
        val fit = IosClockScale.unstretched(IosClockScale.STOCK_NUMERAL_TO_SHORT_SIDE, shortSide, numeral)
        assertEquals(1f, fit.stretch, 0f)
        assertEquals(stock, fit.textSize * numeral, 1e-2f)
    }
}
