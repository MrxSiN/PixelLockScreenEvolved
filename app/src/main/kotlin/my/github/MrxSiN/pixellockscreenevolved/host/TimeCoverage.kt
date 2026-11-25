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
 * from that drawing, and the two are read part by part ([TimeOcclusion.read]).
 * A reading needs far fewer pixels than the time has, so it is cheap enough to
 * take each time the time or what hides it changes.
 */
internal object TimeCoverage {

    /** Longest side the time is drawn at to be measured. */
    private const val SIDE = 128f

    /**
     * [time] as [erase] leaves it. [erase] erases what is in front of the time
     * from a canvas whose units are the time's own pixels.
     */
    fun read(time: View, erase: (Canvas) -> Unit): TimeOcclusion.Reading? {
        if (time.width <= 0 || time.height <= 0) return null
        val scale = min(1f, SIDE / max(time.width, time.height))
        val ink = Bitmap.createBitmap(ceil(time.width * scale).toInt(), ceil(time.height * scale).toInt(), Bitmap.Config.ALPHA_8)
        try {
            val canvas = Canvas(ink).apply { scale(scale, scale) }
            time.draw(canvas)
            val drawn = alphaOf(ink)
            erase(canvas)
            return TimeOcclusion.read(drawn, alphaOf(ink), ink.width, ink.height)
        } finally {
            ink.recycle()
        }
    }

    /** An `ALPHA_8` [picture]'s alpha, one byte per pixel, without its row padding. */
    private fun alphaOf(picture: Bitmap): ByteArray {
        val raw = ByteBuffer.allocate(picture.rowBytes * picture.height).also(picture::copyPixelsToBuffer).array()
        if (picture.rowBytes == picture.width) return raw
        return ByteArray(picture.width * picture.height).also { compact ->
            repeat(picture.height) { y -> raw.copyInto(compact, y * picture.width, y * picture.rowBytes, y * picture.rowBytes + picture.width) }
        }
    }
}
