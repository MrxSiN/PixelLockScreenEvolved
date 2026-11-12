package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Method

/**
 * How Wallpaper & style's clock picker tells an edit from the setting in
 * effect, reached through the picker's class loader.
 *
 * The Apply button is enabled while the clock, its colour, its size switch or
 * its axis preset differs from what is stored. The preset is compared in
 * `ClockPickerViewModel.isClockAxisStyleEdited`, from the preset chosen in the
 * picker (null until one is) and the one stored. HOOK_NOTES.md records the
 * shapes.
 */
internal class PickerClockEditApi(classLoader: ClassLoader) {

    private val editedLambda = load("$VIEW_MODEL\$isClockAxisStyleEdited\$1", classLoader)

    /** Answers whether the chosen axis preset differs from the stored one; its receiver holds both. */
    val axisPresetEdited: Method = editedLambda.getDeclaredMethod("invokeSuspend", Any::class.java)

    private val chosenPresetArgument = editedLambda.field("L\$0")
    private val storedPresetArgument = editedLambda.field("L\$1")

    private val optionsClockViewModel =
        load("com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel", classLoader)
            .field("clockPickerViewModel")
    private val chosenPresetFlow = load(VIEW_MODEL, classLoader).field("overridingClockPresetIndexedStyle")
    private val chosenClockFlow = runCatching { load(VIEW_MODEL, classLoader).field("overridingClock") }.getOrNull()
    private val clockModelId = runCatching {
        load("com.android.customization.picker.clock.shared.model.ClockMetadataModel", classLoader).field("clockId")
    }.getOrNull()
    private var flowValue: Method? = null

    /** The preset chosen in the picker, or null, as [axisPresetEdited]'s receiver last compared it. */
    fun comparedChosenPreset(edited: Any): Any? = chosenPresetArgument.get(edited)

    /** The stored preset, or null, as [axisPresetEdited]'s receiver last compared it. */
    fun comparedStoredPreset(edited: Any): Any? = storedPresetArgument.get(edited)

    /** The clock picker's view model, held by the customization options view model the sheet is bound with. */
    fun clockPickerViewModel(options: Any): Any? = optionsClockViewModel.get(options)

    /** The axis preset chosen in [clockPickerViewModel], or null while none is. */
    fun chosenPreset(clockPickerViewModel: Any): Any? {
        val flow = chosenPresetFlow.get(clockPickerViewModel) ?: return null
        return flow.javaClass.getMethod("getValue").invoke(flow)
    }

    /**
     * The id of the clock tapped in [clockPickerViewModel]'s list since it
     * opened, or null while none is and the stored clock is previewed; null
     * too where the picker no longer holds it.
     */
    fun chosenClockId(clockPickerViewModel: Any): String? {
        val flow = chosenClockFlow?.get(clockPickerViewModel) ?: return null
        val getValue = flowValue ?: flow.javaClass.getMethod("getValue").also { flowValue = it }
        return getValue.invoke(flow)?.let { clockModelId?.get(it) as String? }
    }

    /** Chooses [preset], or none, in [clockPickerViewModel], as its preset slider does. */
    fun choosePreset(clockPickerViewModel: Any, preset: Any?) {
        val flow = chosenPresetFlow.get(clockPickerViewModel) ?: return
        flow.javaClass.getMethod("setValue", Any::class.java).invoke(flow, preset)
    }

    private companion object {
        const val VIEW_MODEL = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel"

        fun load(name: String, classLoader: ClassLoader): Class<*> = classLoader.hostClass(name)
    }
}
