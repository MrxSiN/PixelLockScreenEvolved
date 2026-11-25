package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class SubjectComponentsTest {

    private val width = 128
    private val height = 64

    /** A large subject on the left, a small one on the right, empty between. */
    private fun twoSubjects() = IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        when {
            x in 8 until 48 && y in 8 until 56 -> 255
            x in 100 until 112 && y in 20 until 32 -> 255
            else -> 0
        }
    }

    @Test
    fun onlyTheMainSubjectIsKept() {
        val kept = SubjectComponents.mainOnly(twoSubjects(), width, height)
        assertEquals(255, kept[30 * width + 20])
        assertEquals(0, kept[25 * width + 105])
    }

    @Test
    fun oneSubjectIsLeftAsItWas() {
        val one = IntArray(width * height) { if (it % width in 8 until 48) 200 else 0 }
        val kept = SubjectComponents.mainOnly(one.copyOf(), width, height)
        assertEquals(one.toList(), kept.toList())
    }

    @Test
    fun theMainSubjectsSoftEdgeIsKept() {
        val soft = twoSubjects().also { it[30 * width + 49] = 60 }
        val kept = SubjectComponents.mainOnly(soft, width, height)
        assertEquals(60, kept[30 * width + 49])
    }
}
