package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Names the picker's preset slider "Clock size" while it holds size presets.
 *
 * Wallpaper & style draws the same stepped slider for every clock with axis
 * presets and always labels it "Clock face width", which is what it means for
 * Google's clocks and not for this module's. The slider is rebound each time a
 * clock with presets is chosen; at that moment its presets say whose they are,
 * so the label is set for this module's and put back for anyone else's.
 */
class PickerSizeLabel(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val (bind, api) = try {
            collectorEmit(Class.forName(SLIDER_COLLECTOR, false, classLoader)) to
                PresetSliderApi(ClockPluginApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Picker slider binding not found; the size slider keeps the picker's label", error)
            return
        }

        hooks.after(bind) { collector, args ->
            val sliderModel = args.firstOrNull() ?: return@after
            val slider = api.slider(requireNotNull(collector))
            relabel(slider, api.holdsSizePresets(sliderModel))
        }
    }

    /**
     * The collector's `emit(Object, Continuation)`, found by name and arity.
     * Naming Kotlin's `Continuation` here would not work in a release build:
     * R8 rewrites that class name to the module's own shrunk copy of Kotlin,
     * which is not the class the host's method takes.
     */
    private fun collectorEmit(collector: Class<*>) =
        collector.declaredMethods.firstOrNull { it.name == "emit" && it.parameterCount == 2 }
            ?: throw NoSuchMethodException("${collector.name}.emit")

    private fun relabel(slider: View, holdsSizePresets: Boolean) {
        val context = slider.context
        val resources = context.resources
        val labelId = resources.getIdentifier(LABEL_VIEW, "id", context.packageName)
        val label = (slider.parent as? View)?.findViewById<TextView>(labelId) ?: return

        val text = if (holdsSizePresets) {
            SIZE_LABEL
        } else {
            context.getString(resources.getIdentifier(PICKER_LABEL, "string", context.packageName))
        }
        label.text = text
        slider.contentDescription = text
    }

    private companion object {
        const val SLIDER_COLLECTOR =
            "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder\$bind\$13\$1\$12\$1"
        const val LABEL_VIEW = "clock_face_width_label"
        const val PICKER_LABEL = "clock_face_width"
        const val SIZE_LABEL = "Clock size"
    }
}
