package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.view.View
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.host.ViewPictures.screenLocation

/**
 * The pictures of the lock screen time a flight to the status bar clock shows
 * ([ClockFlightOverlay]): the time whole, and with what stands in front of it
 * ([TimeOcclusion]) cut away. Drawing them takes some 13ms, too long for the
 * first frame of an unlock, so they are drawn ahead of it ([ClockFlightStandby])
 * and remember what they were drawn from, to tell when they no longer match.
 */
internal class TimePictures private constructor(
    val whole: Bitmap,
    /** The time with what stood in front of it cut away; null if nothing did. */
    val hidden: Bitmap?,
    /** The colour the time is drawn in. */
    val color: Int,
    private val drawnFrom: List<Any?>,
) {

    /** Whether these still show [time] as it is drawn now, after [version] changes to how it is written. */
    fun match(time: View, version: Int, occlusion: TimeOcclusion): Boolean = drawnFrom == sourceOf(time, version, occlusion)

    /** Starts uploading the pictures to the GPU now, so the flight's first frame does not wait for it. */
    fun prepareToDraw() {
        whole.prepareToDraw()
        hidden?.prepareToDraw()
    }

    fun recycle() {
        whole.recycle()
        hidden?.recycle()
    }

    companion object {
        private const val OPAQUE = 255

        /** Draws [time], after [version] changes to how it is written; null while it has no size. */
        fun of(time: View, version: Int, occlusion: TimeOcclusion): TimePictures? {
            val source = sourceOf(time, version, occlusion)
            val whole = ViewPictures.of(time) ?: return null
            val hidden = whole.copy(Bitmap.Config.ARGB_8888, true).takeIf { occlusion.eraseSubject(time, it) }
            return TimePictures(whole, hidden, inkColour(whole), source)
        }

        private fun sourceOf(time: View, version: Int, occlusion: TimeOcclusion): List<Any?> =
            listOf(System.identityHashCode(time), version, time.width, time.height, occlusion.stateOf(time))

        /** The colour the time is drawn in, read off the picture's first solid pixel along its middle. */
        private fun inkColour(picture: Bitmap): Int {
            val y = picture.height / 2
            return (0 until picture.width).asSequence()
                .map { picture.getPixel(it, y) }
                .firstOrNull { Color.alpha(it) == OPAQUE }
                ?: Color.WHITE
        }
    }
}

/** The status bar clock's text as that clock draws it, with where its ink sits relative to the clock. */
internal class LandingPicture private constructor(
    val picture: Bitmap,
    private val inkLeft: Float,
    private val inkTop: Float,
    private val drawnFrom: List<Any?>,
) {
    val width get() = picture.width
    val height get() = picture.height

    /** Whether this still shows what [target] draws now. */
    fun match(target: TextView): Boolean = drawnFrom == sourceOf(target)

    /** Where the picture's top left corner goes on screen for [target] where it is now. */
    fun origin(target: TextView): PointF {
        val on = target.screenLocation()
        val lineLeft = target.layout?.getLineLeft(0) ?: 0f
        return PointF(on[0] + target.compoundPaddingLeft + lineLeft + inkLeft, on[1] + target.baseline + inkTop)
    }

    fun prepareToDraw() = picture.prepareToDraw()

    fun recycle() = picture.recycle()

    companion object {
        /** Room around the text so its antialiased edges are not cut off. */
        private const val EDGE = 2

        /** Draws [target]'s text with its own paint and colour. */
        fun of(target: TextView): LandingPicture {
            val text = target.text.toString()
            val paint = Paint(target.paint).apply { color = target.currentTextColor }
            val ink = Rect().also { paint.getTextBounds(text, 0, text.length, it) }

            val picture = Bitmap.createBitmap(ink.width() + 2 * EDGE, ink.height() + 2 * EDGE, Bitmap.Config.ARGB_8888)
            Canvas(picture).drawText(text, (EDGE - ink.left).toFloat(), (EDGE - ink.top).toFloat(), paint)
            return LandingPicture(picture, (ink.left - EDGE).toFloat(), (ink.top - EDGE).toFloat(), sourceOf(target))
        }

        private fun sourceOf(target: TextView): List<Any?> =
            listOf(System.identityHashCode(target), target.text.toString(), target.currentTextColor, target.textSize, target.typeface, target.letterSpacing)
    }
}
