package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Method

/**
 * SystemUI's lock screen status bar, reached through its class loader.
 * HOOK_NOTES.md records the shapes.
 */
internal class KeyguardStatusBarApi(classLoader: ClassLoader) {

    private val viewType = Class.forName("com.android.systemui.statusbar.phone.KeyguardStatusBarView", false, classLoader)

    /** Runs once the lock screen status bar has been inflated. */
    val inflated: Method = viewType.getDeclaredMethod("onFinishInflate")

    private val systemIconsContainer = viewType.getDeclaredField("mSystemIconsContainer").apply { isAccessible = true }

    /** The right side of the lock screen status bar: the status icons and the battery. */
    fun systemIcons(keyguardStatusBar: View): View? = systemIconsContainer.get(keyguardStatusBar) as View?
}
