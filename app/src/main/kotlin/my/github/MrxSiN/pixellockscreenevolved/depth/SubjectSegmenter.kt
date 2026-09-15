package my.github.MrxSiN.pixellockscreenevolved.depth

import android.content.res.AssetManager
import android.graphics.Bitmap

import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel

import kotlin.math.exp

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * BiRefNet_lite, run with ONNX Runtime: dichotomous image segmentation, which
 * cuts the main subject of a photo, whether a person, an animal or a thing,
 * out of it with fine edges such as hair.
 *
 * The model sees the photo at [SIDE] by [SIDE], normalised as it was trained
 * (ImageNet's mean and deviation), and answers, for each pixel, how likely it
 * is to be the subject before a sigmoid. That is eased into an alpha and
 * stretched back over the photo's own size.
 *
 * The model is this module's own export of BiRefNet_lite
 * (`scripts/export_subject_model.py`): its deformable convolutions are ONNX
 * `DeformConv`, and its weights are stored in half precision and widened to
 * full precision as the session loads, so it computes in full precision. It is
 * mapped from the APK, where it is stored uncompressed, rather than read into
 * the heap.
 */
internal class SubjectSegmenter(assets: AssetManager) : AutoCloseable {

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(map(assets, MODEL_PATH), OrtSession.SessionOptions())
    private val inputName = session.inputNames.first()

    /** An `ALPHA_8` mask the size of [photo]; see [SubjectMaskContract]. */
    fun maskOf(photo: Bitmap): Bitmap {
        val scaled = Bitmap.createScaledBitmap(photo, SIDE, SIDE, true)
        val input = OnnxTensor.createTensor(environment, SubjectInput.of(scaled), longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong()))
        if (scaled !== photo) scaled.recycle()

        val logits = input.use {
            session.run(mapOf(inputName to it)).use { outputs ->
                (outputs.get(0) as OnnxTensor).floatBuffer.let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
            }
        }

        val small = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ALPHA_8).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(SubjectInput.alphaOf(logits)))
        }
        return Bitmap.createScaledBitmap(small, photo.width, photo.height, true).also { if (it !== small) small.recycle() }
    }

    override fun close() {
        session.close()
    }

    private companion object {
        const val MODEL_PATH = "models/birefnet_lite.onnx"
        const val SIDE = SubjectInput.SIDE

        fun map(assets: AssetManager, path: String): ByteBuffer = assets.openFd(path).use { file ->
            FileInputStream(file.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, file.startOffset, file.length)
            }
        }
    }
}

/** The arithmetic around the model, kept apart from the runtime. */
internal object SubjectInput {

    const val SIDE = 1024

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val DEVIATION = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The model's input for a [SIDE] by [SIDE] photo: channels first, normalised as BiRefNet was trained. */
    fun of(photo: Bitmap): FloatBuffer {
        val pixels = IntArray(SIDE * SIDE).also { photo.getPixels(it, 0, SIDE, 0, 0, SIDE, SIDE) }
        val input = ByteBuffer.allocateDirect(Float.SIZE_BYTES * 3 * SIDE * SIDE).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (channel in 0 until 3) {
            val shift = 16 - 8 * channel
            for (pixel in pixels) {
                input.put((((pixel shr shift) and 0xff) / 255f - MEAN[channel]) / DEVIATION[channel])
            }
        }
        return input.also { it.rewind() }
    }

    /** One alpha byte per pixel from the model's logits, through a sigmoid and eased at the edge. */
    fun alphaOf(logits: FloatArray): ByteArray = ByteArray(SIDE * SIDE) { i ->
        val confidence = 1f / (1f + exp(-logits[i]))
        (MaskEdge.alphaOf(confidence) * 255f + 0.5f).toInt().toByte()
    }
}
