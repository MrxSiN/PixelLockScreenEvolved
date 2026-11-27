package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectEditsTest {

    private val size = 20
    private val found = IntArray(size * size) { 77 }
    private val untouched = IntArray(size * size) { DepthComposite.UNEDITED }
    private fun edits() = SubjectEdits(size, size, found, untouched) { _, _, _, _ -> }

    @Test
    fun aFastStrokeLeavesNoGap() {
        val edits = edits()
        edits.begin(DepthComposite.ADDED, 2f, 10f, 1.5f)
        edits.moveTo(18f, 10f, 1.5f)
        edits.end()
        for (x in 2..17) assertEquals("x=$x", 255, edits.alphaAt(10 * size + x))
        assertEquals(77, edits.alphaAt(0))
    }

    @Test
    fun undoRedoAndResetAreSteps() {
        val edits = edits()
        edits.begin(DepthComposite.REMOVED, 5f, 5f, 2f)
        edits.end()
        assertEquals(0, edits.alphaAt(5 * size + 5))
        assertTrue(edits.isDirty)
        edits.undo()
        assertEquals(77, edits.alphaAt(5 * size + 5))
        assertFalse(edits.isDirty)
        edits.redo()
        assertEquals(0, edits.alphaAt(5 * size + 5))
        edits.reset()
        assertArrayEquals(untouched, edits.snapshot())
        edits.undo()
        assertEquals(0, edits.alphaAt(5 * size + 5))
    }

    @Test
    fun aCancelledStrokeLeavesNothing() {
        val edits = edits()
        edits.begin(DepthComposite.ADDED, 10f, 10f, 4f)
        edits.cancel()
        assertArrayEquals(untouched, edits.snapshot())
        assertFalse(edits.canUndo)
    }
}
