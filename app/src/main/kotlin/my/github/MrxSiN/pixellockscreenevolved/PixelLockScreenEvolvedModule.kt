package my.github.MrxSiN.pixellockscreenevolved

import android.content.Context

import java.io.File

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyles
import my.github.MrxSiN.pixellockscreenevolved.core.AndroidLogger
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.ContractAnalyzer
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureFaults
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureGate
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Host
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostContracts
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostStartupReport
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch
import my.github.MrxSiN.pixellockscreenevolved.hook.XposedHooks
import my.github.MrxSiN.pixellockscreenevolved.hook.forFeature
import my.github.MrxSiN.pixellockscreenevolved.host.ClockRegistryInjector
import my.github.MrxSiN.pixellockscreenevolved.host.KeyguardAodWallpaper
import my.github.MrxSiN.pixellockscreenevolved.host.KeyguardClockSizeMotion
import my.github.MrxSiN.pixellockscreenevolved.host.KeyguardDepthEffect
import my.github.MrxSiN.pixellockscreenevolved.host.KeyguardPreviewWallpapers
import my.github.MrxSiN.pixellockscreenevolved.host.KeyguardSmartspacePlacement
import my.github.MrxSiN.pixellockscreenevolved.host.LockWallpaperFeed
import my.github.MrxSiN.pixellockscreenevolved.host.PickerClockPatches
import my.github.MrxSiN.pixellockscreenevolved.host.SafeModeNotice
import my.github.MrxSiN.pixellockscreenevolved.safemode.CrashGuard
import my.github.MrxSiN.pixellockscreenevolved.safemode.FileSafeModeStore

/**
 * Module entry point.
 *
 * Its job is to hand each scoped process the patches it can take. Both need
 * the clock styles: SystemUI draws the lock screen clock and its preview, and
 * Wallpaper & style keeps its own registry to list the clocks on offer.
 * SystemUI also places its smartspace around the clock and moves the clock
 * smoothly between its sizes, and the picker gets the width presets, the size
 * slider and the compatibility page.
 *
 * Before any patch installs, the host is checked twice: whether it has been
 * crash looping, which holds this module's features off ([CrashGuard]), and
 * whether the members each feature needs are there ([HostContracts]). A feature
 * either leaves out is not installed ([FeatureGate]), and what was found is
 * written down once the host is up ([HostStartupReport]). Each feature hooks
 * through hooks of its own ([forFeature]), so one that keeps failing at run
 * time, or fails to install, is stopped alone ([FeatureFaults]) while the rest
 * carry on.
 *
 * The module can be hot reloaded: Vector swaps its code in a running host,
 * without restarting it, when a new build is installed (`autoHotReload` in
 * module.prop). The replaced code hands over each host it had loaded
 * ([onHotReloading]); the new code installs its patches there again under the
 * same hook ids, takes off the hooks it no longer makes, and has the clock
 * registry build its clock again from the new code ([onHotReloaded]). Crash
 * watching is left to the handler the first load installed.
 */
class PixelLockScreenEvolvedModule : XposedModule() {

    /**
     * One host this load of the module has patched, and what a hot reload needs
     * to patch it again: its application and clock registry once they exist.
     * SystemUI reports itself ready again whenever it builds a package context
     * for its own APK, and that second class loader holds none of the clock
     * classes, so each host is loaded once.
     */
    private class LoadedHost(val host: Host, val classLoader: ClassLoader, val dataDir: String) {
        @Volatile var application: Context? = null
        @Volatile var registry: Any? = null
    }

    private val loadedHosts = mutableMapOf<Host, LoadedHost>()
    private val hookIds = mutableSetOf<String>()

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        val host = Host.of(param.packageName) ?: return
        if (host in loadedHosts) return

        val loaded = LoadedHost(host, param.classLoader, param.applicationInfo.deviceProtectedDataDir)
        loadedHosts[host] = loaded
        load(loaded, reloaded = false)
    }

    /** Hands each loaded host to the code replacing this one. */
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        // Only platform types cross over: the new code's classes are not this code's.
        val hosts = ArrayList(loadedHosts.values.map { loaded ->
            hashMapOf<String, Any?>(
                KEY_HOST to loaded.host.packageName,
                KEY_CLASS_LOADER to loaded.classLoader,
                KEY_DATA_DIR to loaded.dataDir,
                KEY_APPLICATION to loaded.application,
                KEY_REGISTRY to loaded.registry,
            )
        })
        param.setSavedInstanceState(hosts)
        AndroidLogger.info("Hot reloading in ${hosts.map { it[KEY_HOST] }}")
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        (param.savedInstanceState as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().forEach { saved ->
            val host = Host.of(saved[KEY_HOST] as? String ?: return@forEach) ?: return@forEach
            val loaded = LoadedHost(host, saved[KEY_CLASS_LOADER] as? ClassLoader ?: return@forEach, saved[KEY_DATA_DIR] as String).apply {
                application = saved[KEY_APPLICATION] as? Context
                registry = saved[KEY_REGISTRY]
            }
            loadedHosts[host] = loaded
            runCatching { load(loaded, reloaded = true) }.onFailure { AndroidLogger.warn("Hot reload failed in ${host.packageName}", it) }
        }
        val stale = param.oldHookHandles.filter { it.id !in hookIds }
        stale.forEach { it.unhook() }
        AndroidLogger.info("Hot reloaded; ${hookIds.size} hooks replaced, ${stale.size} taken off")
    }

    private fun load(loaded: LoadedHost, reloaded: Boolean) {
        val host = loaded.host
        val logger = AndroidLogger
        logger.info("${if (reloaded) "Reloading" else "Loading"} in ${host.packageName}")

        val safeMode = FileSafeModeStore(File(loaded.dataDir, SAFE_MODE_FILE))
        if (!reloaded) {
            CrashGuard(safeMode, logger).apply {
                watch()
                recover(Feature.of(host))
            }
        }
        val loadClass = { name: String -> runCatching { Class.forName(name, false, loaded.classLoader) }.getOrNull() }
        val report = ContractAnalyzer(loadClass).analyze(HostContracts.forRuntime(host, loadClass))
        val faults = FeatureFaults(logger)
        val gate = FeatureGate(report, safeMode.disabled(), logger, faults)
        val hooks = XposedHooks(this, logger, hookIds)
        val hooksFor = { feature: Feature -> hooks.forFeature(feature, faults) }

        val notice = SafeModeNotice(moduleApplicationInfo.packageName, logger)
        val startup = HostStartupReport(
            hooks, host, report, safeMode, faults, HostStartupReport.Framework(apiVersion, frameworkName, frameworkVersion), logger,
            onSnapshot = { context, snapshot ->
                loaded.application = context.applicationContext ?: context
                if (host == Host.SYSTEMUI) notice.show(context, snapshot)
            },
            application = loaded.application,
        )
        // One patch failing to install never keeps the next from installing.
        (listOf(startup) + patchesFor(loaded, hooks, hooksFor, gate, logger)).forEach { patch ->
            runCatching { patch.install(loaded.classLoader) }.onFailure { logger.warn("A patch failed to install in ${host.packageName}", it) }
        }
    }

    private fun patchesFor(
        loaded: LoadedHost,
        hooks: Hooks,
        hooksFor: (Feature) -> Hooks,
        gate: FeatureGate,
        logger: Logger,
    ): List<HostPatch> {
        val host = loaded.host
        val reloadedRegistry = loaded.registry
        val onRegistry = { registry: Any -> loaded.registry = registry }
        val styles = ClockStyles.all()
        return when (host) {
            Host.SYSTEMUI -> {
                val depth = gate.allows(Feature.DEPTH_EFFECT)
                val aod = gate.allows(Feature.AOD_WALLPAPER)
                val previewWallpapers = KeyguardPreviewWallpapers(hooksFor(Feature.DEPTH_EFFECT), logger)
                val wallpaperFeed = LockWallpaperFeed(hooks, logger)
                val aodWallpaper = KeyguardAodWallpaper(hooksFor(Feature.AOD_WALLPAPER), wallpaperFeed, logger)
                val depthEffect = KeyguardDepthEffect(wallpaperFeed, moduleApplicationInfo.packageName, previewWallpapers, logger)
                if (aod) wallpaperFeed.addListener(aodWallpaper)
                if (depth) {
                    wallpaperFeed.addListener(depthEffect)
                    depthEffect.addSubjectListener(aodWallpaper::onSubject)
                }
                val sizeMotion = KeyguardClockSizeMotion(hooksFor(Feature.CLOCK_SIZE_MOTION), logger)
                listOfNotNull(
                    gate.gate(
                        Feature.CLOCK_STYLES,
                        ClockRegistryInjector(
                            hooksFor(Feature.CLOCK_STYLES), styles, logger,
                            onRegistry = onRegistry,
                            reloadedRegistry = reloadedRegistry,
                            onClockCreated = { clock ->
                                if (depth) depthEffect.track(clock)
                                if (aod) aodWallpaper.track(clock)
                                if (gate.allows(Feature.CLOCK_SIZE_MOTION)) sizeMotion.track(clock)
                            },
                        ),
                    ),
                    gate.gate(Feature.SMARTSPACE_PLACEMENT, KeyguardSmartspacePlacement(hooksFor(Feature.SMARTSPACE_PLACEMENT), styles, logger)),
                    gate.gate(Feature.CLOCK_SIZE_MOTION, sizeMotion),
                    previewWallpapers.takeIf { depth },
                    wallpaperFeed.takeIf { depth || aod },
                    aodWallpaper.takeIf { aod }?.let { gate.gate(Feature.AOD_WALLPAPER, it) },
                )
            }
            Host.PICKER -> listOf(PickerClockPatches(hooks, hooksFor, styles, gate, moduleApplicationInfo, logger, onRegistry, reloadedRegistry))
        }
    }

    private companion object {
        /** Safe Mode's record, in each host's own device-protected storage. */
        const val SAFE_MODE_FILE = "pixel_lock_screen_evolved_safe_mode"

        /** Keys of what a host hands over through a hot reload. */
        const val KEY_HOST = "host"
        const val KEY_CLASS_LOADER = "classLoader"
        const val KEY_DATA_DIR = "dataDir"
        const val KEY_APPLICATION = "application"
        const val KEY_REGISTRY = "registry"
    }
}
