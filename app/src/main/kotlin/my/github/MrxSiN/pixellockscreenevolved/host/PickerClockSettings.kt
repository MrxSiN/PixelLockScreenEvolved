package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Makes every clock setting Wallpaper & style writes for this module's clocks
 * carry both the font and the size.
 *
 * The picker writes one set of axes: the preset its slider is on, which holds
 * the font. The size comes from the slider this module adds, so it is merged in
 * just before the registry writes. Whatever the write does not choose is kept
 * from the setting already in effect, so choosing a colour does not reset the
 * font or the size.
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

            val font = api.axis(settings, ClockAxes.FONT_KEY)
                ?: state.storedAxis(ClockAxes.FONT_KEY)?.takeIf { sameClock }
                ?: 0f
            val size = state.chosenSizeStep
                ?: state.storedAxis(ClockAxes.SIZE_KEY)?.takeIf { sameClock }
                ?: 0f

            api.putAxis(settings, ClockAxes.FONT_KEY, font)
            api.putAxis(settings, ClockAxes.SIZE_KEY, size)
            api.putAxis(settings, ClockAxes.WIDTH_AXIS_KEY, ClockAxes.WIDE_CLOCK_WIDTH)
        }
    }
}
