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
 * Finds the subject of a photo, for the depth effect.
 *
 * It runs in the module's own app, where the segmentation model and its
 * runtime live ([SubjectSegmenter]); SystemUI binds to it and sends the lock
 * screen photo ([SubjectMaskContract]). Only SystemUI is answered
 * ([SubjectMaskCallers]); docs/depth-effect.md sets out why the service is
 * exported and what guards it. Segmentation runs off the main thread, one
 * photo at a time, newest first ([NewestOnlyWorker]), and needs neither the
 * network nor Play services.
 */
class SubjectMaskService : Service() {

    private class Request(val reply: Messenger, val id: Int, val photo: Bitmap, val careful: Boolean)

    private val executor = Executors.newSingleThreadExecutor()
    private val worker = NewestOnlyWorker(executor, ::segment) { fail(it.reply, it.id, SUPERSEDED) }
    private var segmenter: SubjectSegmenter? = null
    private val callers by lazy { SubjectMaskCallers(packageManager::getPackagesForUid) }

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == SubjectMaskContract.SEGMENT) receive(message)
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        executor.execute {
            segmenter?.close()
            segmenter = null
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
        worker.submit(Request(reply, id, photo, message.data.getBoolean(SubjectMaskContract.KEY_CAREFUL)))
    }

    private fun segment(request: Request) {
        val photo = request.photo
        val started = SystemClock.elapsedRealtime()
        runCatching {
            val model = segmenter ?: SubjectSegmenter(assets).also { segmenter = it }
            model.maskOf(photo, request.careful)
        }.onSuccess { mask ->
            Log.i(TAG, "Found the subject of a ${photo.width}x${photo.height} photo${if (request.careful) " carefully" else ""} in ${SystemClock.elapsedRealtime() - started} ms")
            send(request.reply, SubjectMaskContract.MASK, request.id, Bundle().apply { putParcelable(SubjectMaskContract.KEY_MASK, mask) })
        }.onFailure { error ->
            Log.w(TAG, "Subject segmentation failed", error)
            fail(request.reply, request.id, error.message ?: error.javaClass.simpleName)
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

        /** A request replaced by a newer one before it ran; the client has already moved on from it. */
        const val SUPERSEDED = "superseded"
    }
}
