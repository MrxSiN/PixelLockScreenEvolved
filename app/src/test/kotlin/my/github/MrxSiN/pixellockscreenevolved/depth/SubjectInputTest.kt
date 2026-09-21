package my.github.MrxSiN.pixellockscreenevolved.depth

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectInputTest {

    private val side = SubjectInput.SIDE

    @Test
    fun theMirroredAnswerIsTurnedBackBeforeItIsAveraged() {
        // A subject at the left edge of the photo sits at the right edge of its mirror image.
        val logits = FloatArray(side * side)
        val mirrored = FloatArray(side * side)
        val row = 7
        logits[row * side] = 4f
        mirrored[row * side + side - 1] = 2f

        val averaged = SubjectInput.averageMirrored(logits, mirrored)

        assertEquals(3f, averaged[row * side], 0f)
        assertEquals(0f, averaged[row * side + side - 1], 0f)
    }
}
