package my.github.MrxSiN.pixellockscreenevolved.host

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperPlacementTest {

    /** Measured on a Pixel 8 Pro: a 2426 by 2245 surface whose portrait crop starts 618 in. */
    private fun pixel(scrollX: Float = 0f, zoom: Float = 1f) = WallpaperPlacement.place(
        screen = WallpaperPlacement.Size(1344, 2992),
        surface = WallpaperPlacement.Size(2426, 2245),
        crop = WallpaperPlacement.Crop(618, 0, 2426, 2244),
        scroll = WallpaperPlacement.Scroll(scrollX, 0.5f),
        zoom = zoom,
        zoomScales = WallpaperPlacement.ZoomScales(1f, 1.1f),
    )

    @Test
    fun theCropStartsAtTheScreensEdge() {
        val placed = pixel()
        assertEquals(-823f, placed.left, 1f)
        assertEquals(3233f, placed.width, 1f)
        assertEquals(2992f, placed.height, 1f)
    }

    @Test
    fun scrollingSlidesAcrossTheCropsSpareWidth() {
        val start = pixel(scrollX = 0f).left
        val end = pixel(scrollX = 1f).left
        // The crop is 1808 wide, 2410 on screen, so 1066 of it is spare.
        assertEquals(-1066f, end - start, 2f)
    }

    @Test
    fun zoomingInScalesAboutTheScreensCentre() {
        val placed = pixel(zoom = 0f)
        assertEquals(1.1f, placed.scale, 0.01f)
        assertEquals(672f, placed.toScreenX((672f + 823f) / placed.width * 2426, 2426), 2f)
    }
}
