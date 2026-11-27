package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

import my.github.MrxSiN.pixellockscreenevolved.depth.DepthInput

class DepthCompositeTest {

    /** Ten pixels: a subject at depth 150 (pixels 2 to 6), a railing in front at 220 the mask missed, sky at 20 inside the mask. */
    private val alpha = intArrayOf(0, 0, 255, 255, 255, 255, 255, 0, 0, 0)
    private val depth = intArrayOf(20, 220, 150, 150, 20, 150, 150, 20, 90, 140)

    @Test
    fun whatIsAsNearAsTheSubjectStandsInFrontAndWhatIsFarBehindGoesBack() {
        val combined = DepthComposite.combine(alpha.copyOf(), depth)
        assertEquals("the railing the mask missed", 255, combined[1])
        assertEquals("the subject", 255, combined[2])
        assertEquals("the sky between an arm and a body", 0, combined[4])
        assertEquals("the background", 0, combined[0])
        assertEquals("far enough behind the plane", 0, combined[8])
    }

    @Test
    fun withoutASubjectTheDepthMapSetsNoPlane() {
        val none = IntArray(10)
        assertSame(none, DepthComposite.combine(none, depth))
    }

    @Test
    fun editsWinOverWhatWasFound() {
        val edits = IntArray(10) { DepthComposite.UNEDITED }.also {
            it[0] = DepthComposite.ADDED
            it[3] = DepthComposite.REMOVED
        }
        val edited = DepthComposite.applyEdits(alpha.copyOf(), edits)
        assertEquals(255, edited[0])
        assertEquals(0, edited[3])
        assertEquals(255, edited[2])
    }

    @Test
    fun theDepthModelSeesThePhotoInWholePatches() {
        val (width, height) = DepthInput.sizeFor(1024, 872)
        assertEquals(518, width)
        assertEquals(0, height % DepthInput.PATCH)
        assertEquals(1024f / 872f, width.toFloat() / height, 0.03f)
    }

    @Test
    fun depthIsStretchedBetweenItsPercentiles() {
        val raw = FloatArray(100) { it.toFloat() }
        val near = DepthInput.normalise(raw)
        assertEquals(0, near[0])
        assertEquals(255, near[99])
        assertEquals(IntArray(4).toList(), DepthInput.normalise(FloatArray(4) { 3f }).toList())
    }
}
