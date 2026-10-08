package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Lets the size slider and the position controls enable Wallpaper & style's
 * Apply button.
 *
 * Moving either alone left Apply greyed out, because the picker enables Apply
 * only for changes it knows of and these controls are this module's. They are
 * made part of the picker's own edit instead: choosing a size or a position
 * chooses an axis preset carrying it, which makes the picker compare its edits
 * once more, show the preset on its preview clock, and write the preset's axes
 * on Apply. For a clock with width presets that is the width preset already on
 * show, with the choices added to its axes, and the comparison is then made by
 * width, size and position, so Apply is enabled exactly while any differs from
 * the stored setting. A clock with no width presets is given one holding the
 * choices alone while they differ from the stored setting, and none once they
 * are moved back.
 */
internal class PickerSizeEdit(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val state: PickerClockState,
    private val logger: Logger,
) : HostPatch {

    private val styles = styles.associateBy { it.id }

    private var clockPickerViewModel = WeakReference<Any>(null)

    /** The stored preset of this module's clock the picker last compared against, once it has. */
    @Volatile
    private var storedPreset: Any? = null

    override fun install(classLoader: ClassLoader) {
        val (clocks, sheet, edits) = try {
            val clocks = ClockPluginApi(classLoader)
            Triple(clocks, PickerSliderApi(classLoader), PickerClockEditApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Picker edit state not found; moving the clock size alone does not enable Apply", error)
            return
        }

        hooks.after(sheet.bindSheet) { binding, _ ->
            val options = sheet.sheetOptions(requireNotNull(binding)) ?: return@after
            clockPickerViewModel = WeakReference(edits.clockPickerViewModel(options))
        }

        hooks.replaceResult(edits.axisPresetEdited) { edited, _, result ->
            val stored = edits.comparedStoredPreset(requireNotNull(edited))
            if (stored == null || !clocks.isWidthPreset(stored)) return@replaceResult result
            storedPreset = stored
            val chosen = edits.comparedChosenPreset(edited)
            val widthEdited = chosen != null && clocks.presetIndex(chosen) != clocks.presetIndex(stored)
            widthEdited || state.isAxisEdited()
        }

        state.onAxesChosen {
            val viewModel = clockPickerViewModel.get() ?: return@onAxesChosen
            val style = styles[edits.chosenClockId(viewModel) ?: state.storedClockId()] ?: return@onAxesChosen
            val chosen = state.chosenAxes()
            val preset = if (style.isWidthAdjustable) {
                val shown = edits.chosenPreset(viewModel)?.takeIf(clocks::isWidthPreset) ?: storedPreset ?: return@onAxesChosen
                clocks.withAxes(shown, chosen)
            } else {
                clocks.axesOnlyPreset(chosen).takeIf { state.isAxisEdited() }
            }
            runCatching { edits.choosePreset(viewModel, preset) }
                .onFailure { logger.warn("Clock size or position could not be marked as an edit", it) }
        }
    }
}
