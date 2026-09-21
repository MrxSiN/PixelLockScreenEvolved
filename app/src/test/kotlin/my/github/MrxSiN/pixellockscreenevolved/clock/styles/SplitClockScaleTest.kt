package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import org.junit.Assert.assertEquals
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectFrame

class SplitClockScaleTest {

    private val shortSide = 1344f
    private val screenHeight = 2992f
    private val aboveNumerals = 180f

    private fun subject(top: Float, bottom: Float = screenHeight) =
        SubjectFrame(left = 0f, top = top, right = shortSide, bottom = bottom)

    private fun roomFor(top: Float, bottom: Float = screenHeight) =
        SplitClockScale.roomFor(subject(top, bottom), aboveNumerals, screenHeight)

    private val centred = SplitClockScale.roomFor(null, aboveNumerals, screenHeight)

    @Test
    fun theRoomEndsALittlePastTheSubjectsTop() {
        val top = 1500f
        val bottom = 2992f
        // The room is measured from the numerals' own top, which the subject's top is this far below.
        val toSubject = top - aboveNumerals
        val room = roomFor(top, bottom)
        assert(room > toSubject) { "$room does not reach past the subject's top" }
        assert(room < toSubject + (bottom - top) / 2) { "$room reaches too far past the subject's top" }
    }

    @Test
    fun aSubjectStandingLowerLeavesMoreRoom() {
        val rooms = listOf(900f, 1300f, 1700f).map { roomFor(it) }
        rooms.zipWithNext().forEach { (higher, lower) -> assert(lower > higher) { "$rooms" } }
    }

    /** A photo whose subject fills the screen from its top is a background, not something to stand above. */
    @Test
    fun aSubjectLeavingAlmostNothingIsTreatedAsNone() {
        assertEquals(centred, roomFor(top = 200f), 1e-3f)
        assertEquals(centred, roomFor(top = -400f), 1e-3f)
    }

    @Test
    fun withNoSubjectTheNumeralsReachTowardTheMiddleOfTheScreen() {
        assert(centred > screenHeight * 0.3f) { "$centred" }
        assert(centred < screenHeight * 0.5f) { "$centred" }
    }

    @Test
    fun aLargerSizeClaimsMoreOfTheRoom() {
        val room = 1200f
        val heights = (0..5).map { SplitClockScale.numeralHeight(it / 5f, room, shortSide) }
        heights.zipWithNext().forEach { (smaller, larger) -> assert(larger > smaller) { "$heights" } }
        assertEquals(room, heights.last(), 1e-3f)
    }

    @Test
    fun theNumeralsAreNeverSmallerThanTheirFloorOrTallerThanTheirCeiling() {
        assertEquals(shortSide * 0.22f, SplitClockScale.numeralHeight(size = 0f, room = 10f, shortSide), 1e-3f)
        assertEquals(shortSide * 1.25f, SplitClockScale.numeralHeight(size = 1f, room = 9000f, shortSide), 1e-3f)
    }
}
