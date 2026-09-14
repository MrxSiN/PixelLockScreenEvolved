package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.depth.SubjectMaskContract

/**
 * Asks the module's own app for the subject mask of a photo
 * ([SubjectMaskContract]), binding to it while a request is open.
 *
 * Only the latest photo matters, so a new request replaces one still waiting.
 * A failure is tried again a little later, a few times.
 */
internal class SubjectMaskClient(
    private val context: Context,
    private val modulePackage: String,
    private val logger: Logger,
) {

    private val main = Handler(Looper.getMainLooper())
    private var service: Messenger? = null
    private var bound = false
    private var requestId = 0
    private var pending: Pending? = null

    private class Pending(val id: Int, val photo: Bitmap, val onMask: (Bitmap) -> Unit, var attempts: Int = 0)

    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        val current = pending
        if (current != null && message.arg1 == current.id) onReply(current, message)
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            pending?.let(::send)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    /** Asks for [photo]'s subject mask; [onMask] hears it on the main thread. Call on the main thread. */
    fun request(photo: Bitmap, onMask: (Bitmap) -> Unit) {
        pending = Pending(++requestId, photo, onMask)
        if (service != null) send(requireNotNull(pending)) else bind()
    }

    private fun bind() {
        if (bound) return
        val intent = Intent().setComponent(ComponentName(modulePackage, SubjectMaskContract.SERVICE_CLASS))
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }
            .onFailure { logger.warn("Subject mask service could not be bound", it) }
            .getOrDefault(false)
        if (!bound) logger.warn("Subject mask service is unavailable; is the module app installed?")
    }

    private fun send(request: Pending) {
        val message = Message.obtain(null, SubjectMaskContract.SEGMENT, request.id, 0).apply {
            data = Bundle().apply { putParcelable(SubjectMaskContract.KEY_PHOTO, request.photo) }
            replyTo = replies
        }
        runCatching { requireNotNull(service).send(message) }
            .onFailure { retry(request, it.message ?: "send failed") }
    }

    private fun onReply(request: Pending, message: Message) {
        when (message.what) {
            SubjectMaskContract.MASK -> {
                @Suppress("DEPRECATION")
                val mask = message.data.getParcelable<Bitmap>(SubjectMaskContract.KEY_MASK)
                    ?: return retry(request, "empty mask")
                pending = null
                unbind()
                request.onMask(mask)
            }
            SubjectMaskContract.FAILED -> retry(request, message.data.getString(SubjectMaskContract.KEY_REASON) ?: "failed")
        }
    }

    private fun retry(request: Pending, reason: String) {
        if (++request.attempts > MAX_ATTEMPTS) {
            logger.warn("Subject mask not found after ${request.attempts} attempts: $reason")
            pending = null
            unbind()
            return
        }
        logger.info("Subject mask not ready ($reason); asking again")
        main.postDelayed({ if (pending === request) (service?.let { send(request) } ?: bind()) }, RETRY_DELAY_MS)
    }

    private fun unbind() {
        if (!bound) return
        bound = false
        service = null
        runCatching { context.unbindService(connection) }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 10_000L
    }
}
