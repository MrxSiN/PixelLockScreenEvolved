package my.github.MrxSiN.pixellockscreenevolved.host

import android.app.Activity
import android.app.AlertDialog

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Host
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostStartupReport
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SafeModeAnswer
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SnapshotStore
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch
import my.github.MrxSiN.pixellockscreenevolved.safemode.SafeModeText

/**
 * Asks, as Wallpaper & style opens, about anything Safe Mode held off in either
 * host that has not been answered: restore it, or keep it off.
 *
 * The answer goes to the host it is for ([SnapshotStore.request]), which alone
 * can carry it out. Asked once per opening of the picker.
 */
internal class PickerSafeModePrompt(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val asked = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    override fun install(classLoader: ClassLoader) {
        val activity = runCatching { Class.forName(PICKER_ACTIVITY, false, classLoader) }
            .onFailure { logger.warn("Wallpaper & style's activity not found; Safe Mode is only asked about from SystemUI", it) }
            .getOrNull() ?: return
        val resumed = Activity::class.java.getDeclaredMethod("onResume")

        hooks.after(resumed) { target, _ ->
            if (!activity.isInstance(target)) return@after
            ask(target as Activity)
        }
    }

    private fun ask(activity: Activity) {
        val store = SnapshotStore(activity.contentResolver)
        val bootedAt = HostStartupReport.bootedAt()
        val asking = Host.entries
            .mapNotNull { store.read(it)?.takeIf { snapshot -> snapshot.isFrom(bootedAt) && snapshot.asksAboutSafeMode } }
        if (asking.isEmpty() || !asked.add(activity)) return

        AlertDialog.Builder(activity)
            .setTitle(SafeModeText.TITLE)
            .setMessage(asking.joinToString("\n\n") { SafeModeText.message(it.host, it.held) })
            .setPositiveButton(SafeModeText.RESTORE) { _, _ -> asking.forEach { store.request(it.host, SafeModeAnswer.RESTORE) } }
            .setNegativeButton(SafeModeText.KEEP_DISABLED) { _, _ -> asking.forEach { store.request(it.host, SafeModeAnswer.KEEP_DISABLED) } }
            .show()
    }

    private companion object {
        const val PICKER_ACTIVITY = "com.android.wallpaper.picker.customization.ui.CustomizationPickerActivity"
    }
}
