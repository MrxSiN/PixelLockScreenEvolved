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

    /** A rectangle in surface pixels. */
    data class Crop(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left
        val height get() = bottom - top
    }

    fun place(
        screenWidth: Int,
        screenHeight: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        crop: Crop,
        scrollX: Float,
        scrollY: Float,
        zoom: Float,
        minScale: Float,
        maxScale: Float,
    ): Placed {
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

        val offsetX = (if (spareWidth > 0) -(spareWidth * scrollX + 0.5f).toInt() else 0) + (baseX * hScale).toInt()
        val offsetY = (if (spareHeight > 0) -(spareHeight * scrollY + 0.5f).toInt() else 0) + (baseY * vScale).toInt()

        val zoomScale = minScale + (maxScale - minScale) * (1f - zoom.coerceIn(0f, 1f))
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
