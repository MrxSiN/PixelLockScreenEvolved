package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class AodProgressTest {

    @Test
    fun awakeWithAClearScrimShowsNothing() {
        assertEquals(0f, aodProgress(doze = 0f, revealed = 1f, previous = 0f))
    }

    @Test
    fun theScreenOffFollowsTheScrimNotTheDozeThatJumpedToOne() {
        // Measured on CP4A: the doze is 1 from the first frame while the scrim is still clear; the dots blinked.
        assertEquals(0f, aodProgress(doze = 1f, revealed = 1f, previous = 0f))
        assertEquals(0.5f, aodProgress(doze = 1f, revealed = 0.5f, previous = 0f), 1e-6f)
        assertEquals(1f, aodProgress(doze = 1f, revealed = 0f, previous = 0.9f))
    }

    @Test
    fun aFingerprintUnlockLeavesWithTheReveal() {
        // The doze drops to 0 at once; the scrim is a quarter revealed.
        assertEquals(0.75f, aodProgress(doze = 0f, revealed = 0.25f, previous = 1f), 1e-6f)
        assertEquals(0f, aodProgress(doze = 0f, revealed = 1f, previous = 0.1f))
    }

    @Test
    fun aDarkScrimWithoutTheAlwaysOnDisplayBringsNothingIn() {
        assertEquals(0f, aodProgress(doze = 0f, revealed = 0f, previous = 0f))
    }

    @Test
    fun withoutTheScrimItFollowsTheDoze() {
        assertEquals(0.4f, aodProgress(doze = 0.4f, revealed = Float.NaN, previous = 0f), 1e-6f)
    }
}
