package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Everything Wallpaper & style's process needs, sharing one [PickerClockState]:
 * the clock styles in its registry, the font and size sliders, the Apply button
 * the size enables, the setting Apply writes, and the depth effect switch in
 * the Lock screen list.
 */
internal class PickerClockPatches(
    private val hooks: Hooks,
    private val styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val state = try {
            PickerClockState(ClockPluginApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; no clock style can be added", error)
            return
        }

        listOf(
            ClockRegistryInjector(hooks, styles, logger, onRegistry = { state.registry = it }, onClockCreated = state::track),
            PickerClockSliders(hooks, styles, state, logger),
            PickerClockSettings(hooks, styles, state, logger),
            PickerSizeEdit(hooks, state, logger),
            PickerLockScreenOptions(hooks, logger),
        ).forEach { it.install(classLoader) }
    }
}
