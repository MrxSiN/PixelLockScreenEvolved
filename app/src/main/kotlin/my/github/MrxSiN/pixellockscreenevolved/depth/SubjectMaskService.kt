package my.github.MrxSiN.pixellockscreenevolved.depth

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log

import java.util.concurrent.Executors

/**
 * Finds the subject of a photo, and how near each part of it is, for the depth effect.
 *
 * It runs in the module's own app, where the models and their runtime live
 * ([SubjectSegmenter], [DepthEstimator]); SystemUI binds to it and sends the
 * lock screen photo ([SubjectMaskContract]). Only SystemUI is answered
 * ([SubjectMaskCallers]); docs/depth-effect.md sets out why the service is
 * exported and what guards it. The models run off the main thread, one photo
 * at a time, newest first ([NewestOnlyWorker]), and need neither the network
 * nor Play services.
 */
class SubjectMaskService : Service() {

    private class Request(val reply: Messenger, val id: Int, val photo: Bitmap, val careful: Boolean, val depth: Boolean, val maskKnown: Boolean)

    private val executor = Executors.newSingleThreadExecutor()
    private val worker = NewestOnlyWorker<Request>(executor, ::segment) { fail(it.reply, it.id, SUPERSEDED) }
    private val callers by lazy { SubjectMaskCallers(packageManager::getPackagesForUid) }
    private var segmenter: SubjectSegmenter? = null
    private var estimator: DepthEstimator? = null

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == SubjectMaskContract.SEGMENT) receive(message)
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.execute {
            segmenter?.close()
            segmenter = null
            estimator?.close()
            estimator = null
        }
        executor.shutdown()
        super.onDestroy()
    }

    private fun receive(message: Message) {
        // Checked before the message's data is unparcelled, which a stranger could make throw.
        if (!callers.isAllowed(message.sendingUid)) {
            Log.w(TAG, "Ignored a subject mask request from uid ${message.sendingUid}")
            return
        }
        val reply = message.replyTo ?: return
        val id = message.arg1
        @Suppress("DEPRECATION")
        val photo = runCatching { message.data.getParcelable<Bitmap>(SubjectMaskContract.KEY_PHOTO) }.getOrNull()
            ?: return fail(reply, id, "no photo")
        val data = message.data
        val depth = data.getBoolean(SubjectMaskContract.KEY_DEPTH)
        // A known mask with no depth map wanted would ask for nothing, so it is taken as asking for the mask.
        val maskKnown = depth && data.getBoolean(SubjectMaskContract.KEY_MASK_KNOWN)
        worker.submit(Request(reply, id, photo, data.getBoolean(SubjectMaskContract.KEY_CAREFUL), depth, maskKnown))
    }

    private fun segment(request: Request) {
        val photo = request.photo
        val started = SystemClock.elapsedRealtime()
        runCatching {
            val mask = if (request.maskKnown) null else (segmenter ?: SubjectSegmenter(assets).also { segmenter = it }).maskOf(photo, request.careful)
            // The depth model is loaded and run only for a caller that lays the clock out by depth.
            val depth = if (!request.depth) null else runCatching { (estimator ?: DepthEstimator(assets).also { estimator = it }).depthOf(photo) }
                .onFailure { Log.w(TAG, if (mask != null) "Depth estimation failed; the mask is sent alone" else "Depth estimation failed", it) }
                .getOrNull()
            // A request for the depth map alone that did not get one has nothing to send.
            if (mask == null && depth == null) error("no depth map")
            Found(mask, depth)
        }.onSuccess { found ->
            val what = listOfNotNull("the subject".takeIf { found.mask != null }, "depth".takeIf { found.depth != null }).joinToString(" and ")
            Log.i(TAG, "Found $what of a ${photo.width}x${photo.height} " +
                "photo${if (request.careful) " carefully" else ""} in ${SystemClock.elapsedRealtime() - started} ms")
            send(request.reply, SubjectMaskContract.MASK, request.id, found.bundle())
        }.onFailure { error ->
            Log.w(TAG, "Subject segmentation failed", error)
            fail(request.reply, request.id, error.message ?: error.javaClass.simpleName)
        }
    }

    private class Found(val mask: Bitmap?, val depth: Bitmap?) {
        fun bundle() = Bundle().apply {
            mask?.let { putParcelable(SubjectMaskContract.KEY_MASK, it) }
            depth?.let { putParcelable(SubjectMaskContract.KEY_DEPTH, it) }
        }
    }

    private fun fail(reply: Messenger, id: Int, reason: String) {
        send(reply, SubjectMaskContract.FAILED, id, Bundle().apply { putString(SubjectMaskContract.KEY_REASON, reason) })
    }

    private fun send(reply: Messenger, what: Int, id: Int, data: Bundle) {
        runCatching { reply.send(Message.obtain(null, what, id, 0).apply { this.data = data }) }
            .onFailure { Log.w(TAG, "Could not answer SystemUI", it) }
    }

    private companion object {
        const val TAG = "PixelLockScreenEvolved"

        /** A request replaced by a newer one before it ran; its caller has already moved on from it. */
        const val SUPERSEDED = "superseded"
    }
}

