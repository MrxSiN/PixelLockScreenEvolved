package my.github.MrxSiN.pixellockscreenevolved

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyles
import my.github.MrxSiN.pixellockscreenevolved.core.AndroidLogger
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch
import my.github.MrxSiN.pixellockscreenevolved.hook.XposedHooks
import my.github.MrxSiN.pixellockscreenevolved.host.ClockRegistryInjector
import my.github.MrxSiN.pixellockscreenevolved.host.PickerSizeLabel

/**
 * Module entry point.
 *
 * Its only job is to hand each scoped process the patches it needs. Both need
 * the clock styles: SystemUI draws the lock screen clock and its preview, and
 * Wallpaper & style keeps its own registry to list the clocks on offer. Only
 * the picker has a size slider to label.
 */
class PixelLockScreenEvolvedModule : XposedModule() {

    /**
     * Hosts already handled in this process. SystemUI reports itself ready
     * again whenever it builds a package context for its own APK, and that
     * second class loader holds none of the clock classes.
     */
    private val readyHosts = mutableSetOf<String>()

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage || param.packageName !in HOST_PACKAGES) return
        if (!readyHosts.add(param.packageName)) return

        val logger = AndroidLogger
        logger.info("Loading in ${param.packageName}")
        patchesFor(param.packageName, XposedHooks(this, logger), logger)
            .forEach { it.install(param.classLoader) }
    }

    private fun patchesFor(packageName: String, hooks: Hooks, logger: Logger): List<HostPatch> = buildList {
        add(ClockRegistryInjector(hooks, ClockStyles.all, logger))
        if (packageName == PICKER_PACKAGE) add(PickerSizeLabel(hooks, logger))
    }

    private companion object {
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val PICKER_PACKAGE = "com.google.android.apps.wallpaper"
        val HOST_PACKAGES = setOf(SYSTEMUI_PACKAGE, PICKER_PACKAGE)
    }
}
