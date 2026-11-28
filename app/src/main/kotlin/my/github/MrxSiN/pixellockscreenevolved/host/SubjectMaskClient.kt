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
 * Asks the module's own app for the subject mask and depth map of a photo
 * ([SubjectMaskContract]), binding to it while a request is open.
 *
 * Only the latest photo matters, so a new request replaces one still waiting.
 * A failure is tried again a little later, a few times, before it is given up.
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

    private class Pending(
        val id: Int,
        val photo: Bitmap,
        val onMask: (mask: Bitmap, depth: Bitmap?) -> Unit,
        val onFailed: () -> Unit,
        val careful: Boolean,
        val depth: Boolean,
        /** The photo's mask, kept already, where only its depth map is asked for. */
        val known: Bitmap?,
        var attempts: Int = 0,
    )

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

    /**
     * Asks for [photo]'s subject mask, looked for twice over where [careful]
     * ([SubjectMaskContract.KEY_CAREFUL]), and its depth map too where [depth]; [onMask] hears
     * them, or [onFailed] that they were given up, on the main thread. Where the mask is [known]
     * already and the depth map is wanted, only the depth map is asked for
     * ([SubjectMaskContract.KEY_MASK_KNOWN]) and [onMask] hears it with [known]. Call on the main thread.
     */
    fun request(
        photo: Bitmap,
        careful: Boolean,
        depth: Boolean,
        onMask: (mask: Bitmap, depth: Bitmap?) -> Unit,
        onFailed: () -> Unit,
        known: Bitmap? = null,
    ) {
        pending = Pending(++requestId, photo, onMask, onFailed, careful, depth, known.takeIf { depth })
        if (service != null) send(requireNotNull(pending)) else bind()
    }

    private fun bind() {
        if (bound) return
        val intent = Intent().setComponent(ComponentName(modulePackage, SubjectMaskContract.SERVICE_CLASS))
        // Important: SystemUI is a foreground process, and the service runs at its level while bound, on every
        // core. Bound plainly, it ran in the background cpuset, on the little cores alone, and a subject took
        // 60 to 90 s instead of 13 to 20 whenever the phone was not in use.
        bound = runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT) }
            .onFailure { logger.warn("Subject mask service could not be bound", it) }
            .getOrDefault(false)
        if (!bound) logger.warn("Subject mask service is unavailable; is the module app installed?")
    }

    private fun send(request: Pending) {
        val message = Message.obtain(null, SubjectMaskContract.SEGMENT, request.id, 0).apply {
            data = Bundle().apply {
                putParcelable(SubjectMaskContract.KEY_PHOTO, request.photo)
                putBoolean(SubjectMaskContract.KEY_CAREFUL, request.careful)
                putBoolean(SubjectMaskContract.KEY_DEPTH, request.depth)
                putBoolean(SubjectMaskContract.KEY_MASK_KNOWN, request.known != null)
            }
            replyTo = replies
        }
        runCatching { requireNotNull(service).send(message) }
            .onFailure { retry(request, it.message ?: "send failed") }
    }

    private fun onReply(request: Pending, message: Message) {
        when (message.what) {
            SubjectMaskContract.MASK -> {
                @Suppress("DEPRECATION")
                val mask = message.data.getParcelable<Bitmap>(SubjectMaskContract.KEY_MASK) ?: request.known
                    ?: return retry(request, "empty mask")
                @Suppress("DEPRECATION")
                val depth = message.data.getParcelable<Bitmap>(SubjectMaskContract.KEY_DEPTH)
                if (request.known != null && depth == null) return retry(request, "empty depth map")
                pending = null
                unbind()
                request.onMask(mask, depth)
            }
            SubjectMaskContract.FAILED -> retry(request, message.data.getString(SubjectMaskContract.KEY_REASON) ?: "failed")
        }
    }

    private fun retry(request: Pending, reason: String) {
        if (++request.attempts > MAX_ATTEMPTS) {
            logger.warn("Subject mask not found after ${request.attempts} attempts: $reason")
            pending = null
            unbind()
            request.onFailed()
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
