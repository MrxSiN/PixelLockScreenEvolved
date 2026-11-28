package my.github.MrxSiN.pixellockscreenevolved.depth

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix

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
    private val session: OrtSession = environment.createSession(map(assets, MODEL_PATH), sessionOptions())
    private val inputName = session.inputNames.first()

    /**
     * An `ALPHA_8` mask the size of [photo]; see [SubjectMaskContract]. A
     * [careful] mask also looks at the photo's mirror image and averages the two
     * answers ([SubjectInput.averageMirrored]).
     */
    fun maskOf(photo: Bitmap, careful: Boolean = false): Bitmap {
        val scaled = Bitmap.createScaledBitmap(photo, SIDE, SIDE, true)
        var logits = logitsOf(scaled)
        if (careful) {
            val mirror = Matrix().apply { setScale(-1f, 1f) }
            val mirrored = Bitmap.createBitmap(scaled, 0, 0, SIDE, SIDE, mirror, false)
            logits = SubjectInput.averageMirrored(logits, logitsOf(mirrored))
            mirrored.recycle()
        }
        if (scaled !== photo) scaled.recycle()

        val width = photo.width
        val height = photo.height
        val pixels = IntArray(width * height).also { photo.getPixels(it, 0, width, 0, 0, width, height) }
        val alpha = SubjectInput.refinedAlpha(logits, pixels, width, height)
        // Written as colours, whose alpha is kept: an ALPHA_8 row may be padded past its width.
        for (i in pixels.indices) pixels[i] = (alpha[i].toInt() and 0xff) shl 24
        return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    }

    private fun logitsOf(photo: Bitmap): FloatArray =
        OnnxTensor.createTensor(environment, SubjectInput.of(photo), longArrayOf(1, 3, SIDE.toLong(), SIDE.toLong())).use {
            session.run(mapOf(inputName to it)).use { outputs ->
                (outputs.get(0) as OnnxTensor).floatBuffer.let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }
            }
        }

    override fun close() {
        session.close()
    }

    companion object {
        private const val MODEL_PATH = "models/birefnet_lite.onnx"
        private const val SIDE = SubjectInput.SIDE

        /**
         * Without memory pattern planning: with it, the second run of a session
         * (the mirror image of a careful look, or a retry) allocated its planned
         * block on top of the arena the first run had grown, and peaked at
         * 4.4 GB instead of 2.3 GB, no faster (measured with scripts/bench-segmentation-ab.sh).
         *
         * On half the cores: ONNX Runtime takes one thread per core by default, and the service runs at
         * SystemUI's own foreground level while a request is open, so a look of 20 s or more left the
         * phone's own UI fighting for every core and stuttering throughout.
         */
        // ponytail: half the cores is a guess at the split; measure jank vs. look time per device if it matters.
        internal fun sessionOptions() = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(false)
            setIntraOpNumThreads((Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1))
        }

        internal fun map(assets: AssetManager, path: String): ByteBuffer = assets.openFd(path).use { file ->
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

    /** The mean of [logits] and of [mirrored], the logits of the photo's mirror image, turned back the right way round. */
    fun averageMirrored(logits: FloatArray, mirrored: FloatArray): FloatArray = FloatArray(SIDE * SIDE) { i ->
        val x = i % SIDE
        (logits[i] + mirrored[i - x + SIDE - 1 - x]) / 2f
    }

    /**
     * One alpha byte for each of the photo's [pixels], [width] by [height]: the
     * model's confidence (its logits through a sigmoid), stretched from [SIDE]
     * square over the photo, then snapped to the photo's own edges by a guided
     * filter ([GuidedFilter]) and eased at the edge ([MaskEdge]). Stretching
     * the model's answer alone smeared it over the photo's outline by as many
     * pixels as the photo is larger than the model's input.
     */
    fun refinedAlpha(logits: FloatArray, pixels: IntArray, width: Int, height: Int): ByteArray {
        val confidence = resize(FloatArray(SIDE * SIDE) { 1f / (1f + exp(-logits[it])) }, SIDE, SIDE, width, height)
        val guide = FloatArray(pixels.size) { luma(pixels[it]) }
        val radius = maxOf(MIN_RADIUS, maxOf(width, height) / RADIUS_DIVISOR)
        val refined = GuidedFilter.apply(guide, confidence, width, height, radius, EPSILON)
        return ByteArray(refined.size) { (MaskEdge.alphaOf(refined[it]) * 255f + 0.5f).toInt().toByte() }
    }

    /** [values], [fromWidth] by [fromHeight], stretched bilinearly to [toWidth] by [toHeight], pixel centres aligned. */
    fun resize(values: FloatArray, fromWidth: Int, fromHeight: Int, toWidth: Int, toHeight: Int): FloatArray {
        val sx = fromWidth.toFloat() / toWidth
        val sy = fromHeight.toFloat() / toHeight
        return FloatArray(toWidth * toHeight) { i ->
            val fx = ((i % toWidth + 0.5f) * sx - 0.5f).coerceIn(0f, fromWidth - 1f)
            val fy = ((i / toWidth + 0.5f) * sy - 0.5f).coerceIn(0f, fromHeight - 1f)
            val x0 = fx.toInt()
            val y0 = fy.toInt()
            val x1 = minOf(x0 + 1, fromWidth - 1)
            val y1 = minOf(y0 + 1, fromHeight - 1)
            val tx = fx - x0
            val ty = fy - y0
            val top = values[y0 * fromWidth + x0] * (1 - tx) + values[y0 * fromWidth + x1] * tx
            val bottom = values[y1 * fromWidth + x0] * (1 - tx) + values[y1 * fromWidth + x1] * tx
            top * (1 - ty) + bottom * ty
        }
    }

    /** Rec. 601 luma of an ARGB colour, from 0 to 1. */
    private fun luma(color: Int): Float =
        (0.299f * ((color shr 16) and 0xff) + 0.587f * ((color shr 8) and 0xff) + 0.114f * (color and 0xff)) / 255f

    /** The guided filter's box, against the photo's long side: about 8 pixels on a 1024 pixel photo. */
    private const val RADIUS_DIVISOR = 128
    private const val MIN_RADIUS = 2

    /** How sharp an edge in the photo must be to hold the mask to it; tuned by eye on hair and fur. */
    private const val EPSILON = 2e-3f
}
