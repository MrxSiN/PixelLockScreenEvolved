package my.github.MrxSiN.pixellockscreenevolved.depth

import android.content.res.AssetManager
import android.graphics.Bitmap

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

import kotlin.math.max
import kotlin.math.roundToInt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

/**
 * Depth Anything V2 Small, run with ONNX Runtime: how near each part of a photo
 * is, as relative inverse depth, for laying the clock between what is near and
 * what is far ([my.github.MrxSiN.pixellockscreenevolved.host.DepthComposite]).
 *
 * The model sees the photo with its long side at [LONG_SIDE] and both sides a
 * multiple of its patch, [PATCH]; normalised as it was trained (ImageNet's mean
 * and deviation). It answers one value per pixel, larger for nearer, on no
 * fixed scale, so the answer is stretched between its 2nd and 98th percentiles
 * ([DepthInput.normalise]). It is mapped from the APK, where it is stored
 * uncompressed, rather than read into the heap.
 */
internal class DepthEstimator(assets: AssetManager) : AutoCloseable {

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(SubjectSegmenter.map(assets, MODEL_PATH), SubjectSegmenter.sessionOptions())
    private val inputName = session.inputNames.first()

    /** An `ALPHA_8` bitmap, at the size the model worked at, whose alpha is how near each pixel is: 255 nearest. */
    fun depthOf(photo: Bitmap): Bitmap {
        val (width, height) = DepthInput.sizeFor(photo.width, photo.height)
        val scaled = Bitmap.createScaledBitmap(photo, width, height, true)
        val pixels = IntArray(width * height).also { scaled.getPixels(it, 0, width, 0, 0, width, height) }
        if (scaled !== photo) scaled.recycle()
        val raw = OnnxTensor.createTensor(environment, DepthInput.of(pixels, width, height), longArrayOf(1, 3, height.toLong(), width.toLong())).use {
            session.run(mapOf(inputName to it)).use { outputs ->
                (outputs.get(0) as OnnxTensor).floatBuffer.let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
            }
        }
        val near = DepthInput.normalise(raw)
        for (i in pixels.indices) pixels[i] = near[i] shl ALPHA_SHIFT
        return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    }

    override fun close() {
        session.close()
    }

    private companion object {
        const val MODEL_PATH = "models/depth_anything_v2_small.onnx"
        const val ALPHA_SHIFT = 24

    }
}

/** The arithmetic around the depth model, kept apart from the runtime. */
internal object DepthInput {

    /** The long side the model was trained at. */
    const val LONG_SIDE = 518

    /** The model's patch: both sides must be a multiple of it. */
    const val PATCH = 14

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val DEVIATION = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The size a [width] by [height] photo is given to the model at, its aspect kept as near as the patch allows. */
    fun sizeFor(width: Int, height: Int): Pair<Int, Int> {
        val scale = LONG_SIDE.toFloat() / max(width, height)
        fun side(of: Int) = max(PATCH, (of * scale / PATCH).roundToInt() * PATCH)
        return side(width) to side(height)
    }

    /** The model's input for [pixels], [width] by [height]: channels first, normalised as it was trained. */
    fun of(pixels: IntArray, width: Int, height: Int): FloatBuffer {
        val input = ByteBuffer.allocateDirect(Float.SIZE_BYTES * 3 * width * height).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (channel in 0 until 3) {
            val shift = 16 - 8 * channel
            for (pixel in pixels) input.put((((pixel shr shift) and 0xff) / 255f - MEAN[channel]) / DEVIATION[channel])
        }
        return input.also { it.rewind() }
    }

    /** [raw] inverse depth, stretched to 0 to 255 between its 2nd and 98th percentiles; a flat answer is all 0. */
    fun normalise(raw: FloatArray): IntArray {
        val sorted = raw.copyOf().also { it.sort() }
        val low = sorted[(sorted.size * LOW).toInt()]
        val high = sorted[((sorted.size - 1) * HIGH).toInt()]
        val span = high - low
        if (span <= 0f) return IntArray(raw.size)
        return IntArray(raw.size) { (((raw[it] - low) / span).coerceIn(0f, 1f) * 255f + 0.5f).toInt() }
    }

    private const val LOW = 0.02f
    private const val HIGH = 0.98f
}
