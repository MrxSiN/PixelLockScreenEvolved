package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Icon

import my.github.MrxSiN.pixellockscreenevolved.R
import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * A notification that the depth effect is being prepared for a new lock screen
 * photo, and then that it is ready or could not be.
 *
 * Finding the subject takes some seconds after a photo is applied, and until
 * then the lock screen shows no effect, so it should not pass unnoticed. It is
 * posted in an alerting channel without sound or vibration: its icon shows in
 * the status bar and it sits with the notifications, but it never pops up. It
 * cannot be swiped away while the subject is being found, and once the effect
 * is ready it goes by itself a few seconds later.
 *
 * SystemUI posts it, as the module's own app has no screen from which it could
 * be allowed to notify. Its text and icon are the module's own resources.
 */
internal class DepthProgressNotice(
    private val context: Context,
    private val modulePackage: String,
    private val logger: Logger,
) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private val strings: Resources? by lazy {
        runCatching { context.packageManager.getResourcesForApplication(modulePackage) }
            .onFailure { logger.warn("Module resources not found; the depth effect posts no notification", it) }
            .getOrNull()
    }
    private var channelCreated = false

    /** The subject of a new photo is being found. */
    fun finding() = post(R.string.depth_notice_finding_title, R.string.depth_notice_finding_text) {
        setOngoing(true)
        setProgress(0, 0, true)
    }

    /** The effect is showing for the new photo. */
    fun ready() = post(R.string.depth_notice_ready_title, R.string.depth_notice_ready_text) {
        setTimeoutAfter(READY_SHOWN_MS)
    }

    /** No subject could be found for the new photo. */
    fun failed() = post(R.string.depth_notice_failed_title, R.string.depth_notice_failed_text) {}

    private fun post(title: Int, text: Int, configure: Notification.Builder.() -> Unit) {
        val resources = strings ?: return
        runCatching {
            createChannel(resources)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource(modulePackage, R.drawable.ic_notification_depth))
                .setContentTitle(resources.getString(title))
                .setContentText(resources.getString(text))
                .setStyle(Notification.BigTextStyle().bigText(resources.getString(text)))
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setLocalOnly(true)
                .apply(configure)
                .build()
            manager.notify(TAG, ID, notification)
        }.onFailure { logger.warn("Depth effect notification could not be posted", it) }
    }

    private fun createChannel(resources: Resources) {
        if (channelCreated) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, resources.getString(R.string.depth_notice_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = resources.getString(R.string.depth_notice_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        channelCreated = true
    }

    private companion object {
        const val CHANNEL_ID = "pixel_lock_screen_evolved_depth_effect"
        const val TAG = "pixel_lock_screen_evolved_depth_effect"
        const val ID = 1

        /** How long the notification stays once the effect is ready. */
        const val READY_SHOWN_MS = 5_000L
    }
}
