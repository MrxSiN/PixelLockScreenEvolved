package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectInkTest {

    /** A 4 by 4 grid over a 400 by 400 cut-out, its lower half the subject's. */
    private val lowerHalf = SubjectInk(4, 4, BooleanArray(16) { it >= 8 }, 400, 400)

    @Test
    fun coverageCountsTheSubjectsShareOfABox() {
        assertEquals(0f, lowerHalf.coverage(0f, 0f, 400f, 200f), 0.001f)
        assertEquals(1f, lowerHalf.coverage(0f, 200f, 400f, 400f), 0.001f)
        assertEquals(0.5f, lowerHalf.coverage(0f, 0f, 400f, 400f), 0.001f)
        assertEquals(0.5f, lowerHalf.coverage(100f, 100f, 300f, 300f), 0.001f)
    }

    @Test
    fun aBoxPartlyOffTheCutOutCountsWhatIsOffAsUncovered() {
        assertEquals(0.5f, lowerHalf.coverage(0f, 200f, 400f, 600f), 0.001f)
        assertEquals(0f, lowerHalf.coverage(0f, -400f, 400f, -100f), 0.001f)
    }

    @Test
    fun anEmptyGridHasNoSubject() {
        assertEquals(true, SubjectInk(2, 2, BooleanArray(4), 10, 10).isEmpty)
    }
}
