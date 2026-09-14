package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveSpringTest {

    @Test
    fun startsAtRestAtZero() {
        assertEquals(0f, ExpressiveSpring.FAST_SPATIAL.valueAt(0f), 1e-6f)
        assertEquals(0f, ExpressiveSpring.FAST_EFFECTS.valueAt(0f), 1e-6f)
    }

    @Test
    fun spatialSpringOvershootsThenSettles() {
        val spring = ExpressiveSpring.FAST_SPATIAL
        val peak = (1..500).maxOf { spring.valueAt(it / 1000f) }
        assertTrue("a bouncy spring passes its end", peak > 1f)
        assertEquals(1f, spring.valueAt(spring.settleMillis / 1000f), 0.002f)
    }

    @Test
    fun effectsSpringNeverOvershoots() {
        val spring = ExpressiveSpring.FAST_EFFECTS
        assertTrue((1..500).all { spring.valueAt(it / 1000f) <= 1f })
        assertEquals(1f, spring.valueAt(spring.settleMillis / 1000f), 0.002f)
    }

    @Test
    fun aKickStartsAndEndsAtRestAndSwingsOutByOne() {
        val spring = ExpressiveSpring.FAST_SPATIAL
        assertEquals(0f, spring.kickAt(0f), 1e-6f)
        val farthest = (1..500).maxOf { spring.kickAt(it / 1000f) }
        assertEquals(1f, farthest, 0.01f)
        assertEquals(0f, spring.kickAt(spring.settleMillis / 1000f), 0.01f)
    }
}
