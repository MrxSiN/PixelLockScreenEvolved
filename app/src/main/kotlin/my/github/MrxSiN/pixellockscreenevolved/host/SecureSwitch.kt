package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * A whole-number secure setting of this module's that Wallpaper & style writes
 * from its Lock screen list ([PickerLockScreenOptions]) and SystemUI reads.
 * Renaming a key sends every device back to [default].
 */
internal open class SecureIntSetting(private val key: String, private val default: Int) {

    fun get(resolver: ContentResolver): Int = Settings.Secure.getInt(resolver, key, default)

    fun put(resolver: ContentResolver, value: Int) {
        Settings.Secure.putInt(resolver, key, value)
    }

    /** Calls [onChange] on the main thread now and whenever the setting changes. */
    fun observe(resolver: ContentResolver, onChange: () -> Unit) {
        resolver.registerContentObserver(
            Settings.Secure.getUriFor(key),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChange()
            },
        )
        onChange()
    }
}

/** An on/off secure setting, off until turned on. */
internal open class SecureSwitch(key: String) : SecureIntSetting(key, OFF) {

    fun isOn(resolver: ContentResolver): Boolean = get(resolver) == ON

    fun set(resolver: ContentResolver, on: Boolean) = put(resolver, if (on) ON else OFF)

    /** Calls [onChange] on the main thread with the setting now and whenever it changes. */
    fun watch(resolver: ContentResolver, onChange: (Boolean) -> Unit) = observe(resolver) { onChange(isOn(resolver)) }

    private companion object {
        const val ON = 1
        const val OFF = 0
    }
}

/** Whether the depth effect is on. */
internal object DepthEffectSetting : SecureSwitch("pixel_lock_screen_evolved_depth_effect")
