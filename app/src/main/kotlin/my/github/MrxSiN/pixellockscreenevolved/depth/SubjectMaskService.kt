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
import android.util.Log

import java.util.concurrent.Executors

/**
 * Finds the subject of a photo, for the depth effect.
 *
 * It runs in the module's own app, where the segmentation model and its
 * runtime live ([SubjectSegmenter]); SystemUI binds to it and sends the lock
 * screen photo ([SubjectMaskContract]). Segmentation runs off the main thread,
 * one photo at a time, and needs neither the network nor Play services.
 */
class SubjectMaskService : Service() {

    private val worker = Executors.newSingleThreadExecutor()
    private var segmenter: SubjectSegmenter? = null

    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == SubjectMaskContract.SEGMENT) segment(Message.obtain(message))
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.execute {
            segmenter?.close()
            segmenter = null
        }
        worker.shutdown()
        super.onDestroy()
    }

    private fun segment(request: Message) {
        val reply = request.replyTo ?: return
        val id = request.arg1
        if (SubjectMaskContract.CALLER_PACKAGE !in packageManager.getPackagesForUid(request.sendingUid).orEmpty()) {
            fail(reply, id, "caller not allowed")
            return
        }
        @Suppress("DEPRECATION")
        val photo = request.data.getParcelable<Bitmap>(SubjectMaskContract.KEY_PHOTO)
            ?: return fail(reply, id, "no photo")

        worker.execute {
            runCatching {
                val model = segmenter ?: SubjectSegmenter(assets).also { segmenter = it }
                model.maskOf(photo)
            }.onSuccess { mask ->
                send(reply, SubjectMaskContract.MASK, id, Bundle().apply { putParcelable(SubjectMaskContract.KEY_MASK, mask) })
            }.onFailure { error ->
                Log.w(TAG, "Subject segmentation failed", error)
                fail(reply, id, error.message ?: error.javaClass.simpleName)
            }
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
    }
}
