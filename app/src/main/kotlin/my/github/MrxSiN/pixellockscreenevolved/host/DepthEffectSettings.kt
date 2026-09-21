package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver

/** Whether the depth effect is on. */
internal object DepthEffectSetting : SecureSwitch("pixel_lock_screen_evolved_depth_effect")

/**
 * Where the depth effect stands with the current lock screen photo, as SystemUI
 * last found it ([KeyguardDepthEffect]) and Wallpaper & style shows it on the
 * Depth effect page. [value] is what is stored; renaming one sends a device
 * back to [NONE] until the next photo.
 */
internal enum class DepthStatus(val value: Int) {
    /** No lock screen photo has been looked at yet, as with a live wallpaper. */
    NONE(0),
    FINDING(1),
    READY(2),

    /** The photo was looked at and no subject could be cut out of it. */
    NO_SUBJECT(3),
    ;

    companion object {
        private val setting = SecureIntSetting("pixel_lock_screen_evolved_depth_status", NONE.value)

        fun get(resolver: ContentResolver): DepthStatus = entries.firstOrNull { it.value == setting.get(resolver) } ?: NONE

        fun put(resolver: ContentResolver, status: DepthStatus) = setting.put(resolver, status.value)

        /** Calls [onChange] on the main thread with the status now and whenever it changes. */
        fun watch(resolver: ContentResolver, onChange: (DepthStatus) -> Unit) = setting.observe(resolver) { onChange(get(resolver)) }
    }
}

/**
 * Wallpaper & style asking SystemUI to find the current photo's subject again,
 * more carefully, and to forget the mask it keeps for it. Each ask counts up,
 * so asking twice is seen twice.
 */
internal object DepthReprocessRequest {

    private val setting = SecureIntSetting("pixel_lock_screen_evolved_depth_reprocess", 0)

    fun ask(resolver: ContentResolver) = setting.put(resolver, setting.get(resolver) + 1)

    /** Calls [onAsked] on the main thread each time it is asked from now on. */
    fun listen(resolver: ContentResolver, onAsked: () -> Unit) {
        var seen = setting.get(resolver)
        setting.observe(resolver) {
            val now = setting.get(resolver)
            if (now != seen) {
                seen = now
                onAsked()
            }
        }
    }
}
