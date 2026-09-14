package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * Whether the depth effect is on: a secure setting Wallpaper & style writes
 * from its Lock screen list and SystemUI watches. It is off until turned on.
 */
internal object DepthEffectSetting {

    /** Renaming it turns the effect off on every device that had it on. */
    const val KEY = "pixel_lock_screen_evolved_depth_effect"

    val uri: Uri get() = Settings.Secure.getUriFor(KEY)

    fun isOn(resolver: ContentResolver): Boolean = Settings.Secure.getInt(resolver, KEY, OFF) == ON

    fun set(resolver: ContentResolver, on: Boolean) {
        Settings.Secure.putInt(resolver, KEY, if (on) ON else OFF)
    }

    /** Calls [onChange] on the main thread with the setting now and whenever it changes. */
    fun watch(resolver: ContentResolver, onChange: (Boolean) -> Unit) {
        resolver.registerContentObserver(
            uri,
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChange(isOn(resolver))
            },
        )
        onChange(isOn(resolver))
    }

    private const val ON = 1
    private const val OFF = 0
}
