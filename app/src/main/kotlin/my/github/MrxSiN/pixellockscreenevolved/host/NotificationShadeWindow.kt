package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.WindowManager

/** `WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE`, hidden from the public SDK. */
private const val TYPE_NOTIFICATION_SHADE = 2040

/**
 * Whether this view is in the lock screen's own window, rather than in a
 * preview SystemUI draws for Wallpaper & style; a Compose holder sized to the
 * clock passes, as the window is read rather than the parent.
 */
internal fun View.isInNotificationShade(): Boolean =
    (rootView.layoutParams as? WindowManager.LayoutParams)?.type == TYPE_NOTIFICATION_SHADE
