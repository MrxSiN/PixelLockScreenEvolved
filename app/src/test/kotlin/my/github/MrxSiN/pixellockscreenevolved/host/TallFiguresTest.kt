package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.TallFigures

class TallFiguresTest {

    @Test
    fun theCapsKeepTheirShapeAndTheMiddleGrows() {
        // A figure 100 tall made three times as tall, from 0.
        assertEquals(0f, TallFigures.map(0f, 0f, 100f, 3f), 0.001f)
        // The top cap moves not at all.
        assertEquals(22f, TallFigures.map(22f, 0f, 100f, 3f), 0.001f)
        // The bottom cap keeps its height, at the new foot.
        assertEquals(300f, TallFigures.map(100f, 0f, 100f, 3f), 0.001f)
        assertEquals(278f, TallFigures.map(78f, 0f, 100f, 3f), 0.001f)
        // The middle takes the rest, evenly.
        assertEquals(150f, TallFigures.map(50f, 0f, 100f, 3f), 0.001f)
    }

    @Test
    fun aSmallGrowthIsAPlainStretch() {
        assertEquals(55f, TallFigures.map(50f, 0f, 100f, 1.1f), 0.001f)
        assertEquals(50f, TallFigures.map(50f, 0f, 100f, 1f), 0.001f)
    }
}
