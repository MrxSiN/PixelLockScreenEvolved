package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockRotationTest {

    @Test
    fun aStoredTurnReadsBackClampedAndAMissingOneIsUpright() {
        val turned = ClockPosition(0.25f, rotation = -30f)
        assertEquals(turned, ClockPosition.of(turned.modeValue, turned.share, turned.rotation))
        assertEquals(0f, ClockPosition.of(null, null).rotation, 0f)
        assertEquals(90f, ClockPosition.of(null, null, 400f).rotation, 0f)
        assertEquals(-90f, ClockPosition.of(null, null, -91f).rotation, 0f)
        assertEquals(0f, ClockPosition.of(null, null, Float.NaN).rotation, 0f)
    }

    @Test
    fun aTurnedFaceCoversItsTurnedBox() {
        assertEquals(200f, ClockRotation.extentHeight(1000f, 200f, 0f), 0.01f)
        assertEquals(1000f, ClockRotation.extentHeight(1000f, 200f, 90f), 0.01f)
        assertEquals(1000f, ClockRotation.extentHeight(1000f, 200f, -90f), 0.01f)
        // 1000 sin 30 + 200 cos 30.
        assertEquals(673.2f, ClockRotation.extentHeight(1000f, 200f, 30f), 0.1f)
        assertEquals(966.0f, ClockRotation.extentWidth(1000f, 200f, 30f), 0.1f)
    }

    @Test
    fun anUprightFaceIsPlacedAsBefore() {
        for (share in floatArrayOf(0f, 0.3f, 1f)) {
            assertEquals(
                100f + share * (1100f - 100f - 200f),
                ClockPosition.topAt(share, 100f, 1100f, 200f, extent = 200f),
                0.001f,
            )
        }
    }

    @Test
    fun aTurnedFaceKeepsItsTurnedBoxInTheBand() {
        val height = 200f
        val extent = ClockRotation.extentHeight(1000f, height, 30f)
        val top = ClockPosition.topAt(0f, 100f, 1100f, height, extent)
        // Its middle stands half its turned box below the band's top.
        assertEquals(100f + extent / 2, top + height / 2, 0.001f)
        val bottom = ClockPosition.topAt(1f, 100f, 1100f, height, extent)
        assertEquals(1100f - extent / 2, bottom + height / 2, 0.001f)
        // And a share read back from where it stands is the one it was placed at.
        assertEquals(0.4f, ClockPosition.shareAt(ClockPosition.topAt(0.4f, 100f, 1100f, height, extent), 100f, 1100f, height, extent), 0.0001f)
    }

    @Test
    fun aFillingFaceGivesUpHeightToStayInItsRoomTurned() {
        assertEquals(1000f, ClockRotation.uprightRoom(1000f, 600f, 0f), 0.01f)
        val upright = ClockRotation.uprightRoom(1000f, 600f, 20f)
        assertTrue(upright < 1000f)
        assertEquals(1000f, ClockRotation.extentHeight(600f, upright, 20f), 0.5f)
        // Nearly on its side, its width, not its height, decides: it keeps its room.
        assertEquals(1000f, ClockRotation.uprightRoom(1000f, 600f, 85f), 0.01f)
        // Never less than a third or so of it, however wide the face.
        assertEquals(350f, ClockRotation.uprightRoom(1000f, 5000f, 40f), 0.01f)
    }

    @Test
    fun aTurnSettlesOnTheNearestDetentAndIsSaid() {
        assertEquals(15f, ClockRotation.snap(13f), 0f)
        assertEquals(-45f, ClockRotation.snap(-47.5f), 0f)
        assertEquals(22f, ClockRotation.snap(22f), 0f)
        assertEquals(-1, ClockRotation.detentOf(22f))
        assertEquals("Upright", ClockRotation.nameOf(0.3f))
        assertEquals("Turned 15 degrees right", ClockRotation.nameOf(15f))
        assertEquals("Turned 30 degrees left", ClockRotation.nameOf(-30f))
        assertEquals("−30°", ClockRotation.label(-30f))
        assertEquals("90°", ClockRotation.label(90f))
    }
}
