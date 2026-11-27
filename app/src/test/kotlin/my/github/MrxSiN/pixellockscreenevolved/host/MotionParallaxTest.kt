package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.cos
import kotlin.math.sin

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionParallaxTest {

    /** The last row of a rotation matrix: where "up" points in the phone's frame. */
    private fun up(x: Float, y: Float, z: Float) = FloatArray(9).also { it[6] = x; it[7] = y; it[8] = z }

    @Test
    fun leanIsReadTheSameUprightAndFlat() {
        val lean = 0.2f
        // Held upright, screen to the face, turned like a wheel with the right side down: up tips toward -x.
        assertEquals(lean, sideTilt(up(-sin(lean), cos(lean), 0f)), 1e-5f)
        // Flat on its back, right side down.
        assertEquals(lean, sideTilt(up(-sin(lean), 0f, cos(lean))), 1e-5f)
    }

    @Test
    fun uprightLeanStaysSmallPastVertical() {
        // Leaning a little past upright, where roll from getOrientation wrapped through ±π.
        assertEquals(-0.05f, sideTilt(up(sin(0.05f), 0.99f, -0.1f)), 1e-3f)
    }
}
