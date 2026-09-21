package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickerClockSectionOrderTest {

    private val styles = listOf("ios", "minimal", "stacked")

    @Test
    fun groupsModuleClocksAtIosAndKeepsBothOrders() {
        val items = listOf("ios", "pixel-1", "minimal", "pixel-2", "stacked", "pixel-3")

        assertEquals(
            listOf("ios", "minimal", "stacked", "pixel-1", "pixel-2", "pixel-3"),
            PickerClockSectionOrder.group(items, styles) { it },
        )
    }

    @Test
    fun keepsHostClocksBeforeIosInPlace() {
        val items = listOf("pixel-1", "ios", "pixel-2", "stacked", "minimal")

        assertEquals(
            listOf("pixel-1", "ios", "minimal", "stacked", "pixel-2"),
            PickerClockSectionOrder.group(items, styles) { it },
        )
    }

    @Test
    fun putsDividerAfterContiguousModuleSection() {
        val keys = listOf("ios", "minimal", "stacked", "pixel")

        assertEquals(2, PickerClockSectionOrder.dividerIndex(keys, styles.toSet()))
        assertNull(PickerClockSectionOrder.dividerIndex(keys.dropLast(1), styles.toSet()))
        assertNull(PickerClockSectionOrder.dividerIndex(listOf("ios", "pixel", "minimal"), styles.toSet()))
    }
}
