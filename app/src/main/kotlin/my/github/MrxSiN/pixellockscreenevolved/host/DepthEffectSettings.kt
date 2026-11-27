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
 * One side asking the other to do something, through a secure setting that
 * counts each ask, so asking twice is seen twice.
 */
internal open class SecureRequest(key: String) {

    private val setting = SecureIntSetting(key, 0)

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

/**
 * Wallpaper & style asking SystemUI to find the current photo's subject again,
 * more carefully, and to forget the mask it keeps for it.
 */
internal object DepthReprocessRequest : SecureRequest("pixel_lock_screen_evolved_depth_reprocess")

/** Wallpaper & style asking SystemUI to open the subject's editor ([DepthMaskEditor]) over it. */
internal object DepthEditRequest : SecureRequest("pixel_lock_screen_evolved_depth_edit")

/**
 * Wallpaper & style handing SystemUI a photo it previews before it is set, for
 * the preview's clock to stand behind its subject ([PickerDepthPreview]): an
 * explicit broadcast to SystemUI carrying the photo, as Wallpaper & style
 * cannot see the module's own app to send it there. Only a sender holding
 * [PERMISSION] reaches SystemUI's receiver, and only one Android names as
 * [SENDER] is heard.
 */
internal object DepthPreviewBroadcast {
    const val ACTION = "my.github.MrxSiN.pixellockscreenevolved.DEPTH_PREVIEW"

    /** The photo, as JPEG bytes: a bitmap would not fit in a broadcast. */
    const val EXTRA_PHOTO = "photo"

    /** Signature or privileged: Wallpaper & style holds it, no downloaded app can. */
    const val PERMISSION = "android.permission.SET_WALLPAPER_COMPONENT"
    const val SENDER = "com.google.android.apps.wallpaper"
    const val RECEIVER = "com.android.systemui"
}

/** Whether the clock moves a little against the photo as the phone tilts ([MotionParallax]). */
internal object DepthMotionSetting : SecureSwitch("pixel_lock_screen_evolved_depth_motion")

/** What stands in front of the clock ([DepthComposite]). [value] is what is stored. */
internal enum class DepthLayers(val value: Int, val title: String, val description: String) {
    SUBJECT(0, "Subject", "The subject found in the photo stands in front of the clock"),
    DEPTH(1, "Depth map", "Everything as near as the subject stands in front; what is behind it stays behind"),
    ;

    companion object {
        private val setting = SecureIntSetting("pixel_lock_screen_evolved_depth_layers", SUBJECT.value)

        fun get(resolver: ContentResolver): DepthLayers = entries.firstOrNull { it.value == setting.get(resolver) } ?: SUBJECT

        fun put(resolver: ContentResolver, chosen: DepthLayers) = setting.put(resolver, chosen.value)

        fun observe(resolver: ContentResolver, onChange: () -> Unit) = setting.observe(resolver, onChange)
    }
}

/**
 * How the clock and the subject share the screen, as the Depth effect page
 * chooses ([TimeOcclusion]). [value] is what is stored.
 */
internal enum class DepthOcclusion(val value: Int, val title: String, val description: String) {
    BALANCED(0, "Balanced", "The subject stands in front while every digit stays readable"),
    READABLE(1, "Readability first", "The time comes forward sooner, for small or thin clocks"),
    DEPTH(2, "Depth first", "The subject always stands in front, however much of the time it hides"),
    ;

    companion object {
        private val setting = SecureIntSetting("pixel_lock_screen_evolved_depth_occlusion", BALANCED.value)

        fun get(resolver: ContentResolver): DepthOcclusion = entries.firstOrNull { it.value == setting.get(resolver) } ?: BALANCED

        fun put(resolver: ContentResolver, chosen: DepthOcclusion) = setting.put(resolver, chosen.value)

        fun observe(resolver: ContentResolver, onChange: () -> Unit) = setting.observe(resolver, onChange)
    }
}

/** Which of the photo's subjects stand in front of the clock ([SubjectComponents]). [value] is what is stored. */
internal enum class DepthSubjects(val value: Int, val title: String, val description: String) {
    ALL(0, "All subjects", "Everything found in the photo stands in front"),
    MAIN(1, "Main subject only", "Only the largest subject stands in front; the rest stays behind the clock"),
    ;

    companion object {
        private val setting = SecureIntSetting("pixel_lock_screen_evolved_depth_subjects", ALL.value)

        fun get(resolver: ContentResolver): DepthSubjects = entries.firstOrNull { it.value == setting.get(resolver) } ?: ALL

        fun put(resolver: ContentResolver, chosen: DepthSubjects) = setting.put(resolver, chosen.value)

        fun observe(resolver: ContentResolver, onChange: () -> Unit) = setting.observe(resolver, onChange)
    }
}
