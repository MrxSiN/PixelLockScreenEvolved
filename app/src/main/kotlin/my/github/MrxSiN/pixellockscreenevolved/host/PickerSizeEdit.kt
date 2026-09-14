package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Lets the size slider enable Wallpaper & style's Apply button.
 *
 * The picker enables Apply only for changes it knows of, and the size slider is
 * this module's, so moving it alone left Apply greyed out. The size is made part
 * of the picker's own edit instead: moving the slider chooses the axis preset
 * already on show again, with the size added to its axes, which makes the
 * picker compare its edits once more. For this module's clocks that comparison
 * is then made by font and size, so Apply is enabled exactly while either
 * differs from the stored setting, and greys out again when both are moved back.
 */
internal class PickerSizeEdit(
    private val hooks: Hooks,
    private val state: PickerClockState,
    private val logger: Logger,
) : HostPatch {

    private var clockPickerViewModel = WeakReference<Any>(null)

    /** The stored preset of this module's clock the picker last compared against, once it has. */
    @Volatile
    private var storedPreset: Any? = null

    override fun install(classLoader: ClassLoader) {
        val (clocks, sheet, edits) = try {
            val clocks = ClockPluginApi(classLoader)
            Triple(clocks, PickerSliderApi(classLoader, clocks), PickerClockEditApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Picker edit state not found; moving the clock size alone does not enable Apply", error)
            return
        }

        hooks.after(sheet.bindSheet) { _, args ->
            val options = args.getOrNull(1) ?: return@after
            clockPickerViewModel = WeakReference(edits.clockPickerViewModel(options))
        }

        hooks.replaceResult(edits.axisPresetEdited) { edited, _, result ->
            val stored = edits.comparedStoredPreset(requireNotNull(edited))
            if (stored == null || !clocks.isFontPreset(stored)) return@replaceResult result
            storedPreset = stored
            val chosen = edits.comparedChosenPreset(edited)
            val fontEdited = chosen != null && clocks.presetIndex(chosen) != clocks.presetIndex(stored)
            fontEdited || state.isSizeStepEdited()
        }

        state.onSizeStepChosen { step ->
            val viewModel = clockPickerViewModel.get() ?: return@onSizeStepChosen
            val shown = edits.chosenPreset(viewModel) ?: storedPreset ?: return@onSizeStepChosen
            runCatching { edits.choosePreset(viewModel, clocks.withSizeStep(shown, step)) }
                .onFailure { logger.warn("Clock size could not be marked as an edit", it) }
        }
    }
}
