package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon

import my.github.MrxSiN.pixellockscreenevolved.R
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostSnapshot
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SafeModeAnswer
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SnapshotStore
import my.github.MrxSiN.pixellockscreenevolved.safemode.SafeModeText

/**
 * Tells the person, from SystemUI, what Safe Mode held off there, and asks
 * whether to restore it or keep it off.
 *
 * SystemUI posts it as soon as it has started again with the features held
 * off, since a crash loop in SystemUI leaves nothing else on screen to say so.
 * Each button asks the same way Wallpaper & style asks ([SnapshotStore.request]),
 * so SystemUI carries out the answer in one place; once answered, the
 * notification goes.
 */
internal class SafeModeNotice(
    private val modulePackage: String,
    private val logger: Logger,
) {

    private var receiverRegistered = false

    @SuppressLint("NotificationPermission") // SystemUI owns [context] and already has permission to post notifications.
    fun show(context: Context, snapshot: HostSnapshot) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!snapshot.asksAboutSafeMode) {
            manager.cancel(TAG, ID)
            return
        }
        runCatching {
            listenForAnswers(context, snapshot)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    setShowBadge(false)
                },
            )
            val message = SafeModeText.message(snapshot.host, snapshot.held)
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(Icon.createWithResource(modulePackage, R.drawable.ic_notification_safe_mode))
                .setContentTitle(SafeModeText.TITLE)
                .setContentText(message)
                .setStyle(Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_ERROR)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setLocalOnly(true)
                .addAction(action(context, SafeModeText.RESTORE, SafeModeAnswer.RESTORE))
                .addAction(action(context, SafeModeText.KEEP_DISABLED, SafeModeAnswer.KEEP_DISABLED))
                .build()
            manager.notify(TAG, ID, notification)
        }.onFailure { logger.warn("Safe Mode notification could not be posted", it) }
    }

    private fun action(context: Context, title: String, answer: SafeModeAnswer): Notification.Action {
        val intent = Intent(ACTION).setPackage(context.packageName).putExtra(EXTRA_ANSWER, answer.name)
        val pending = PendingIntent.getBroadcast(
            context, answer.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, title, pending).build()
    }

    private fun listenForAnswers(context: Context, snapshot: HostSnapshot) {
        if (receiverRegistered) return
        receiverRegistered = true
        val snapshots = SnapshotStore(context.contentResolver)
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(receiver: Context, intent: Intent) {
                    val answer = intent.getStringExtra(EXTRA_ANSWER)?.let(SafeModeAnswer::valueOf) ?: return
                    snapshots.request(snapshot.host, answer)
                }
            },
            IntentFilter(ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    private companion object {
        const val CHANNEL_ID = "pixel_lock_screen_evolved_safe_mode"
        const val CHANNEL_NAME = "Safe Mode"
        const val TAG = "pixel_lock_screen_evolved_safe_mode"
        const val ID = 2
        const val ACTION = "io.github.mrxsin.pixellockscreenevolved.SAFE_MODE_ANSWER"
        const val EXTRA_ANSWER = "answer"
    }
}
