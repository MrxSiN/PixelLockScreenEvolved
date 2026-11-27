package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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
    fun aStrokePastThePhotosEdgeIsSnappedBackToIt() {
        // A dark subject on the left half of a bright sky; nothing found, and an Add stroke that
        // runs two pixels past the subject's edge into the sky, within the filter's reach.
        val size = 32
        val edge = 16
        val guide = FloatArray(size * size) { if (it % size < edge) 0.1f else 0.9f }
        val edits = IntArray(size * size) { if (it % size < edge + 2) DepthComposite.ADDED else DepthComposite.UNEDITED }
        val alpha = DepthComposite.applyEdits(IntArray(size * size), edits)
        DepthComposite.snapEdits(alpha, edits, guide, size, size, radius = 4, epsilon = 2e-3f)
        val row = size * (size / 2)
        assertEquals("deep inside the stroke stays painted", 255, alpha[row + 4])
        assertEquals("well clear of any stroke is untouched", 0, alpha[row + 28])
        assertTrue("the brush's overshoot into the sky is pulled back: ${alpha[row + edge + 1]}", alpha[row + edge + 1] < 160)
        assertTrue("the subject's side of the edge stays solid: ${alpha[row + edge - 2]}", alpha[row + edge - 2] > 200)
    }

    @Test
    fun withoutEditsNothingIsSnapped() {
        val alpha = IntArray(16) { it * 16 }
        val before = alpha.copyOf()
        DepthComposite.snapEdits(alpha, IntArray(16) { DepthComposite.UNEDITED }, FloatArray(16), 4, 4, radius = 1, epsilon = 2e-3f)
        assertEquals(before.toList(), alpha.toList())
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
