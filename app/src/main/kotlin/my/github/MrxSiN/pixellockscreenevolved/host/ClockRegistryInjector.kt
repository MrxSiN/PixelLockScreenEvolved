package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Adds this module's clock styles to a process's `ClockRegistry`.
 *
 * SystemUI and Wallpaper & style each build a registry and reach for its clocks
 * straight away ([ClockPluginApi.registryReady]); the styles are added just
 * before that call, which is before the registry first reads the chosen clock.
 * From there the registry treats them as it treats its own clocks: the picker
 * lists them, SystemUI draws the chosen one, and the preview renders it. That
 * call is repeated whenever the chosen clock changes, so the styles are added
 * only where they are missing. Nothing is written anywhere, so turning the
 * module off falls back to the default clock. Where [isOffered] says the
 * styles cannot be drawn, they are not added at all, so none can be chosen.
 *
 * After a hot reload ([reloadedRegistry]) the registry already holds this
 * module's clocks, built by the code that was replaced. They are replaced at
 * once, and the registry is asked to build its current clock again, so the new
 * code draws it without the host restarting.
 */
internal class ClockRegistryInjector(
    private val hooks: Hooks,
    private val styles: List<ClockStyle>,
    private val logger: Logger,
    private val onRegistry: (registry: Any) -> Unit = {},
    private val onClockCreated: (ClockControllerAdapter) -> Unit = {},
    private val isOffered: (Context) -> Boolean = { true },
    private val reloadedRegistry: Any? = null,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val api = try {
            ClockPluginApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; no clock style can be added", error)
            return
        }
        val proxies = InterfaceProxy(classLoader, logger)

        hooks.before(api.registryReady) { registry, _ ->
            inject(api, proxies, requireNotNull(registry), replace = false)
        }
        reloadedRegistry?.let { registry ->
            inject(api, proxies, registry, replace = true)
            runCatching { api.currentClockChanged?.invoke(registry) }
                .onFailure { logger.warn("The current clock could not be built again after a hot reload", it) }
        }
    }

    private fun inject(api: ClockPluginApi, proxies: InterfaceProxy, registry: Any, replace: Boolean) {
        onRegistry(registry)
        if (!isOffered(api.registryContext(registry))) return
        val provider = ClockProviderAdapter(api, proxies, styles, api.registryContext(registry), logger, onClockCreated).create()
        val clocks = api.availableClocks(registry)

        val added = styles.filter { style ->
            val info = api.clockInfo(style, provider)
            if (replace) clocks.put(style.id, info) == null else clocks.putIfAbsent(style.id, info) == null
        }
        if (replace) logger.info("Replaced clock styles ${styles.map { it.id }} after a hot reload")
        else if (added.isNotEmpty()) logger.info("Added clock styles ${added.map { it.id }}")
    }
}
