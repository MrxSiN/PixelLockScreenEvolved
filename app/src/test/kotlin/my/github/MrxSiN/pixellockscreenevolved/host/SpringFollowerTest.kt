package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpringFollowerTest {

    @Test
    fun aTargetThatLeapsIsFollowedGently() {
        val spring = SpringFollower(stiffness = 200f)
        val afterOneFrame = spring.follow(target = 1f, seconds = 0.016f)
        assertTrue("moves only a little in the first frame, was $afterOneFrame", afterOneFrame < 0.05f)
    }

    @Test
    fun settlesOnTheTargetWithoutOvershooting() {
        val spring = SpringFollower(stiffness = 200f)
        var highest = 0f
        repeat(120) { highest = maxOf(highest, spring.follow(target = 1f, seconds = 0.016f)) }
        assertTrue("overshot to $highest", highest <= 1.001f)
        assertEquals(1f, spring.value.toFloat(), 0.001f)
    }
}
