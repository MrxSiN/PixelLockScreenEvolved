package my.github.MrxSiN.pixellockscreenevolved.host

import kotlin.math.roundToInt

/**
 * Where the window manager puts a still wallpaper on screen, worked out as
 * `WallpaperController.updateWallpaperOffset` and
 * `WindowState.updateSurfacePosition` do, so something drawn over it can
 * match it pixel for pixel. Kept free of Android so it can be unit tested.
 *
 * The wallpaper's surface is scaled so its crop for the screen fills the
 * screen, moved so the crop starts at the screen's edge, slid across the
 * crop's spare width or height by the scroll, and finally scaled by the zoom
 * about the screen's centre.
 */
internal object WallpaperPlacement {

    /**
     * The surface's place: its top left corner and size before the zoom, and
     * the zoom [scale] applied about the screen's centre ([pivotX], [pivotY]).
     */
    data class Placed(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val scale: Float,
        val pivotX: Float,
        val pivotY: Float,
    ) {
        /** Where a point of the surface, in surface pixels, lands on screen. */
        fun toScreenX(surfaceX: Float, surfaceWidth: Int): Float =
            pivotX + (left + surfaceX * width / surfaceWidth - pivotX) * scale

        fun toScreenY(surfaceY: Float, surfaceHeight: Int): Float =
            pivotY + (top + surfaceY * height / surfaceHeight - pivotY) * scale
    }

    /** A width and height in pixels. */
    data class Size(val width: Int, val height: Int)

    /** How far the wallpaper is scrolled across its crop's spare width and height, each from 0 to 1. */
    data class Scroll(val x: Float, val y: Float)

    /** The least and most the window manager scales a wallpaper as it zooms. */
    data class ZoomScales(val min: Float, val max: Float)

    /** A rectangle in surface pixels. */
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** Places a [surface] cropped to [crop] on a [screen], at [scroll] and [zoom], from 0 zoomed in to 1 zoomed out. */
    fun place(
        screen: Size,
        surface: Size,
        crop: Crop,
        scroll: Scroll,
        zoom: Float,
        zoomScales: ZoomScales,
    ): Placed {
        val screenWidth = screen.width
        val screenHeight = screen.height
        val surfaceWidth = surface.width
        val surfaceHeight = surface.height
        // The window is as tall as the screen; its width keeps the surface's shape.
        val vScale = screenHeight.toFloat() / surfaceHeight
        val hScale = vScale
        val screenRatio = screenWidth.toFloat() / screenHeight

        val cropIsWider = crop.width.toFloat() / crop.height >= screenRatio
        val extra: Float
        val visibleWidth: Float
        val visibleHeight: Float
        if (cropIsWider) {
            extra = screenHeight.toFloat() / crop.height / vScale
            visibleWidth = crop.height * screenRatio
            visibleHeight = crop.height.toFloat()
        } else {
            extra = screenWidth.toFloat() / crop.width / hScale
            visibleWidth = crop.width.toFloat()
            visibleHeight = crop.width / screenRatio
        }

        val baseX = -crop.left + ((extra - 1f) * visibleWidth / 2f).toInt()
        val baseY = -crop.top + ((extra - 1f) * visibleHeight / 2f).toInt()
        val spareWidth = ((crop.width - visibleWidth) * hScale).toInt()
        val spareHeight = ((crop.height - visibleHeight) * vScale).toInt()

        val offsetX = (if (spareWidth > 0) -(spareWidth * scroll.x + 0.5f).toInt() else 0) + (baseX * hScale).toInt()
        val offsetY = (if (spareHeight > 0) -(spareHeight * scroll.y + 0.5f).toInt() else 0) + (baseY * vScale).toInt()

        val zoomScale = zoomScales.min + (zoomScales.max - zoomScales.min) * (1f - zoom.coerceIn(0f, 1f))
        return Placed(
            left = offsetX.toFloat(),
            top = offsetY.toFloat(),
            width = (surfaceWidth * hScale).roundToInt().toFloat(),
            height = (surfaceHeight * vScale).roundToInt().toFloat(),
            scale = zoomScale * extra,
            pivotX = screenWidth / 2f,
            pivotY = screenHeight / 2f,
        )
    }
}
