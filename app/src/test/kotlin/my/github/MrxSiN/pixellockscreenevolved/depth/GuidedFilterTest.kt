package my.github.MrxSiN.pixellockscreenevolved.depth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedFilterTest {

    private val width = 40
    private val height = 4

    /** A photo dark on the left half and light on the right: the subject's true edge is at x = 20. */
    private val guide = FloatArray(width * height) { if (it % width < 20) 0.1f else 0.9f }

    /** The model's answer: the same edge, smeared over twelve pixels. */
    private val blurred = FloatArray(width * height) { ((it % width - 14) / 12f).coerceIn(0f, 1f) }

    @Test
    fun aSmearedMaskSnapsToThePhotosEdge() {
        val refined = GuidedFilter.apply(guide, blurred, width, height, radius = 4, epsilon = 1e-4f)
        val row = 1 * width
        // Away from the edge, inside the blur, the mask now agrees with the side of the edge it is on.
        assertTrue("left of the edge: ${refined[row + 17]}", refined[row + 17] < blurred[row + 17])
        assertTrue("right of the edge: ${refined[row + 23]}", refined[row + 23] > blurred[row + 23])
        // And far from the edge it is left as it was.
        assertEquals(0f, refined[row + 2], 0.01f)
        assertEquals(1f, refined[row + 38], 0.01f)
    }

    @Test
    fun aFlatPhotoOnlySmoothes() {
        val flat = FloatArray(width * height) { 0.5f }
        val refined = GuidedFilter.apply(flat, blurred, width, height, radius = 2, epsilon = 1e-4f)
        val mean = GuidedFilter.boxMean(GuidedFilter.boxMean(blurred, width, height, 2), width, height, 2)
        refined.indices.forEach { assertEquals(mean[it], refined[it], 1e-4f) }
    }

    @Test
    fun theBoxMeanClipsAtTheEdges() {
        val values = FloatArray(5) { it.toFloat() }
        val mean = GuidedFilter.boxMean(values, 5, 1, 1)
        assertEquals(0.5f, mean[0], 1e-6f)
        assertEquals(2f, mean[2], 1e-6f)
        assertEquals(3.5f, mean[4], 1e-6f)
    }

    @Test
    fun theModelsAnswerIsStretchedWithItsPixelCentresAligned() {
        val stretched = SubjectInput.resize(floatArrayOf(0f, 1f), 2, 1, 4, 1)
        assertEquals(listOf(0f, 0.25f, 0.75f, 1f), stretched.toList())
    }
}
