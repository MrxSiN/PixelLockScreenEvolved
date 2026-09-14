package my.github.MrxSiN.pixellockscreenevolved.depth

import android.content.res.AssetManager
import android.graphics.Bitmap

import java.nio.ByteBuffer
import java.nio.FloatBuffer

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * U²-Net lite (U2NetP), run with ONNX Runtime: salient object segmentation,
 * which finds the main subject of a photo, whether a person, an animal or a
 * thing.
 *
 * The model sees the photo at [SIDE] by [SIDE], normalised as it was trained
 * (each channel over the photo's brightest value, then ImageNet's mean and
 * deviation), and answers how likely each pixel is to be the subject. That is
 * stretched to the range 0 to 1 and back over the photo's own size.
 */
internal class SubjectSegmenter(assets: AssetManager) : AutoCloseable {

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(
        assets.open(MODEL_PATH).use { it.readBytes() },
        OrtSession.SessionOptions(),
    )
    private val inputName = session.inputNames.first()

    /** An `ALPHA_8` mask the size of [photo]; see [SubjectMaskContract]. */
    fun maskOf(photo: Bitmap): Bitmap {
        val scaled = Bitmap.createScaledBitmap(photo, SIDE, SIDE, true)
        val input = OnnxTensor.createTensor(environment, SubjectInput.of(scaled), longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong()))
        if (scaled !== photo) scaled.recycle()

        val saliency = input.use {
            session.run(mapOf(inputName to it)).use { outputs ->
                (outputs.get(0) as OnnxTensor).floatBuffer.let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
            }
        }

        val small = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(SubjectInput.alphaOf(saliency)))
        }
        return Bitmap.createScaledBitmap(small, photo.width, photo.height, true).also { if (it !== small) small.recycle() }
    }

    override fun close() {
        session.close()
    }

    private companion object {
        const val MODEL_PATH = "models/u2netp.onnx"
        const val SIDE = SubjectInput.SIDE
    }
}

/** The arithmetic around the model, kept apart from the runtime. */
internal object SubjectInput {

    const val SIDE = 320

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val DEVIATION = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The model's input for a [SIDE] by [SIDE] photo: channels first, normalised as U²-Net was trained. */
    fun of(photo: Bitmap): FloatBuffer {
        val pixels = IntArray(SIDE * SIDE).also { photo.getPixels(it, 0, SIDE, 0, 0, SIDE, SIDE) }
        var brightest = 0
        for (pixel in pixels) {
            brightest = maxOf(brightest, (pixel shr 16) and 0xff, (pixel shr 8) and 0xff, pixel and 0xff)
        }
        val scale = 1f / maxOf(brightest, 1)
        val input = FloatBuffer.allocate(3 * SIDE * SIDE)
        for (channel in 0 until 3) {
            val shift = 16 - 8 * channel
            for (pixel in pixels) {
                input.put((((pixel shr shift) and 0xff) * scale - MEAN[channel]) / DEVIATION[channel])
            }
        }
        return input.also { it.rewind() }
    }

    /** One alpha byte per pixel from the model's saliency, stretched to its own range and eased at the edge. */
    fun alphaOf(saliency: FloatArray): ByteArray {
        val pixels = SIDE * SIDE
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        for (i in 0 until pixels) {
            low = minOf(low, saliency[i])
            high = maxOf(high, saliency[i])
        }
        val range = (high - low).takeIf { it > 0f } ?: 1f
        return ByteArray(pixels) { i -> (MaskEdge.alphaOf((saliency[i] - low) / range) * 255f + 0.5f).toInt().toByte() }
    }
}
