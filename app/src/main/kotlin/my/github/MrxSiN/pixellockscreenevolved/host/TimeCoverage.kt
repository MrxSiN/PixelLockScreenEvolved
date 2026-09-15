package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View

import java.nio.ByteBuffer

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * How much of a lock screen clock's time is hidden by what is drawn in front of it.
 *
 * The time is drawn small and alpha-only, what stands in front of it is erased
 * from that drawing, and the ink left is weighed against the ink drawn. A share
 * needs far fewer pixels than the time has, so it is cheap enough to measure
 * each time the time or what hides it changes.
 */
internal object TimeCoverage {

    /** Longest side the time is drawn at to be measured. */
    private const val SIDE = 128f

    private const val BYTE_MASK = 0xff

    /**
     * The share of [time]'s ink, from 0 to 1, that [erase] removes. [erase]
     * erases what is in front of the time from a canvas whose units are the
     * time's own pixels.
     */
    fun of(time: View, erase: (Canvas) -> Unit): Float {
        if (time.width <= 0 || time.height <= 0) return 0f
        val scale = min(1f, SIDE / max(time.width, time.height))
        val ink = Bitmap.createBitmap(ceil(time.width * scale).toInt(), ceil(time.height * scale).toInt(), Bitmap.Config.ALPHA_8)
        try {
            val canvas = Canvas(ink).apply { scale(scale, scale) }
            time.draw(canvas)
            val drawn = inkOf(ink)
            if (drawn == 0L) return 0f
            erase(canvas)
            return 1f - inkOf(ink).toFloat() / drawn
        } finally {
            ink.recycle()
        }
    }

    /** The sum of an `ALPHA_8` [picture]'s alpha. */
    private fun inkOf(picture: Bitmap): Long =
        ByteBuffer.allocate(picture.byteCount).also(picture::copyPixelsToBuffer).array()
            .fold(0L) { sum, alpha -> sum + (alpha.toInt() and BYTE_MASK) }
}
