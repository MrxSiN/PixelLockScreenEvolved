package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Field

/**
 * The picker's preset slider binding, reached through its class loader.
 *
 * The slider's view model keeps the preset group it steps through only inside
 * the lambda R8 generated for its touch callback, so that field is found by
 * type rather than by its generated name. HOOK_NOTES.md records the shapes.
 */
internal class PresetSliderApi(private val api: ClockPluginApi) {

    /** The `Slider` a collector binds, held in its captured `$axisPresetSlider`. */
    fun slider(collector: Any): View =
        collector.javaClass.getDeclaredField("\$axisPresetSlider").accessible().get(collector) as View

    /** Whether the slider model steps through this module's size presets. */
    fun holdsSizePresets(sliderModel: Any): Boolean {
        val callback = sliderModel.javaClass.getDeclaredField("onSliderStopTrackingTouch")
            .accessible().get(sliderModel) ?: return false
        val group = callback.javaClass.declaredFields
            .firstOrNull { it.type == api.presetGroupType }
            ?.accessible()?.get(callback) ?: return false
        return api.isSizePresetGroup(group)
    }

    private fun Field.accessible(): Field = apply { isAccessible = true }
}
