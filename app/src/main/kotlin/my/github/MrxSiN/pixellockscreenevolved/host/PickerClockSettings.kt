package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Makes every clock setting Wallpaper & style writes for this module's clocks
 * carry the width, the size and the position.
 *
 * The picker writes one set of axes: the preset its "Clock face width"
 * slider is on, which holds the width. The size and position come from the controls this module adds, so they are merged in
 * just before the registry writes. Whatever the write does not choose is kept
 * from the setting already in effect, so choosing a colour does not reset the
 * width or the size.
 */
internal class PickerClockSettings(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val state: PickerClockState,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }.toSet()

    override fun install(classLoader: ClassLoader) {
        val api = try {
            ClockPluginApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock plugin API not found; the size cannot be stored", error)
            return
        }
        val applySettings = api.applySettings
            ?: return logger.warn("Picker registry has no applySettings; the size cannot be stored")

        hooks.before(applySettings) { _, args ->
            val settings = args.firstOrNull() ?: return@before
            val clockId = api.clockId(settings)
            if (clockId !in styleIds) return@before
            val sameClock = state.storedClockId() == clockId

            val width = api.axis(settings, ClockAxes.WIDTH_KEY)
                ?: state.storedAxis(ClockAxes.WIDTH_KEY)?.takeIf { sameClock }
                ?: ClockAxes.DEFAULT_WIDTH_STEP.toFloat()
            val size = state.chosenSizeStep
                ?: state.storedAxis(ClockAxes.SIZE_KEY)?.takeIf { sameClock }
                ?: 0f

            val position = state.chosenPosition
                ?: state.storedPosition().takeIf { sameClock }
                ?: ClockPosition.TOP

            api.putAxis(settings, ClockAxes.WIDTH_KEY, width)
            api.putAxis(settings, ClockAxes.SIZE_KEY, size)
            api.putAxis(settings, ClockAxes.POSITION_KEY, position.modeValue)
            api.putAxis(settings, ClockAxes.POSITION_SHARE_KEY, position.share)
            api.putAxis(settings, ClockAxes.WIDTH_AXIS_KEY, ClockAxes.WIDE_CLOCK_WIDTH)
        }
    }
}
