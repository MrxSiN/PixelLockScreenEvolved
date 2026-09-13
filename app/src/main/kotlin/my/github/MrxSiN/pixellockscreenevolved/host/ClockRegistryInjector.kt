package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Adds this module's clock styles to a process's `ClockRegistry`.
 *
 * SystemUI and Wallpaper & style each build a registry and call
 * `registerListeners` on it straight away; the styles are added just before
 * that call, which is before the registry first reads the chosen clock. From
 * there the registry treats them as it treats its own clocks: the picker lists
 * them, SystemUI draws the chosen one, and the preview renders it. Nothing is
 * written anywhere, so turning the module off falls back to the default clock.
 */
class ClockRegistryInjector(
    private val hooks: Hooks,
    private val styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val api = try {
            ClockPluginApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; no clock style can be added", error)
            return
        }
        val proxies = InterfaceProxy(classLoader, logger)

        hooks.before(api.registerListeners) { registry ->
            inject(api, proxies, requireNotNull(registry))
        }
    }

    private fun inject(api: ClockPluginApi, proxies: InterfaceProxy, registry: Any) {
        val provider = ClockProviderAdapter(api, proxies, styles, api.registryContext(registry)).create()
        val clocks = api.availableClocks(registry)

        for (style in styles) {
            clocks.putIfAbsent(style.id, api.clockInfo(style, provider))
        }
        logger.info("Added clock styles ${styles.map { it.id }}")
    }
}
