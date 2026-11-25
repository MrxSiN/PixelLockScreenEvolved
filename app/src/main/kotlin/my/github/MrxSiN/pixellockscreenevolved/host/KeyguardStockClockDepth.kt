package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Puts the photo's subject in front of Google's own clocks too: each clock the
 * registry builds that is not one of this module's ([ClockPluginApi.createCurrentClock])
 * is handed to the depth effect with its large face ([KeyguardDepthEffect.trackStock]).
 */
internal class KeyguardStockClockDepth(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val depth: KeyguardDepthEffect,
    private val logger: Logger,
) : HostPatch {

    private val moduleIds = styles.map { it.id }.toSet()

    override fun install(classLoader: ClassLoader) {
        val api = try {
            ClockPluginApi.of(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; Google's clocks get no depth effect", error)
            return
        }
        val create = api.createCurrentClock ?: return logger.info("The clock registry builds no clock by name here; Google's clocks get no depth effect")
        // The built clock is the call's result, which only a result hook sees; it is handed back unchanged.
        hooks.replaceResult(create) { _, _, result ->
            result?.takeIf { api.controllerClockId(it) !in moduleIds }?.let { controller ->
                api.largeView(controller)?.let { depth.trackStock(controller, it) }
            }
            result
        }
    }
}
