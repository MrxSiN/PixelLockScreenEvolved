package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Wallpaper & style's clock sheet and its Material slider, reached through the
 * picker's class loader.
 *
 * The picker ships Material shrunk: the slider's setters are gone, so its range,
 * value and colours are set the way the picker's own code sets them, through
 * the fields and the one method R8 kept. Lambda fields R8 generated are found by
 * type rather than by name. HOOK_NOTES.md records the shapes.
 */
internal class PickerSliderApi(
    classLoader: ClassLoader,
    private val clocks: ClockPluginApi,
) {

    /** Binds the whole clock sheet: every tab's content, once per sheet. */
    val bindSheet: Method = load(SHEET_BINDER, classLoader).declaredMethods
        .firstOrNull { it.name == "bind" && it.parameterCount == 4 }
        ?: throw NoSuchMethodException("$SHEET_BINDER.bind")

    /** Rebinds the preset slider each time a clock is chosen; its argument is the slider's view model, or null. */
    val bindPresetSlider: Method = load(PRESET_SLIDER_COLLECTOR, classLoader).declaredMethods
        .firstOrNull { it.name == "emit" && it.parameterCount == 2 }
        ?: throw NoSuchMethodException("$PRESET_SLIDER_COLLECTOR.emit")

    private val collectorSlider = load(PRESET_SLIDER_COLLECTOR, classLoader).field("\$axisPresetSlider")

    private val sheetHeights = load(SHEET_BINDER, classLoader).field("_clockFloatingSheetHeights")
    private val heightsType = load("com.android.customization.picker.clock.ui.viewmodel.ClockFloatingSheetHeightsViewModel", classLoader)
    private val heightsConstructor = heightsType.getConstructor(
        Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType, Int::class.javaObjectType,
    )
    private val heightFields = listOf(
        "clockStyleContentHeight", "clockColorContentHeight", "clockSizeContentHeight", "axisPresetSliderHeight",
    ).map { heightsType.field(it) }

    private val sliderType = load("com.google.android.material.slider.BaseSlider", classLoader)
    private val valueFrom = sliderType.field("valueFrom")
    private val valueTo = sliderType.field("valueTo")
    private val stepSize = sliderType.field("stepSize")
    private val dirtyConfig = sliderType.field("dirtyConfig")
    private val changeListeners = sliderType.field("changeListeners")
    private val setValues = sliderType.getDeclaredMethod("setValuesInternal", ArrayList::class.java)
        .apply { isAccessible = true }
    private val changeListenerType = load("com.google.android.material.slider.BaseOnChangeListener", classLoader)

    private val colourFields = COLOUR_FIELDS.map { sliderType.field(it) }
    private val paintFields = PAINT_FIELDS.map { sliderType.field(it) }
    private val thumbDrawable = sliderType.field("defaultThumbDrawable")
    private val shapeDrawableType = load("com.google.android.material.shape.MaterialShapeDrawable", classLoader)
    private val shapeState = shapeDrawableType.field("drawableState")
    private val shapeFillColor = load("com.google.android.material.shape.MaterialShapeDrawable\$MaterialShapeDrawableState", classLoader)
        .field("fillColor")
    private val shapeSetFillColor = shapeDrawableType.getMethod("setFillColor", ColorStateList::class.java)

    /** The preset slider a collector binds, held in its captured `$axisPresetSlider`. */
    fun presetSlider(collector: Any): View = collectorSlider.get(collector) as View

    /** Whether a preset slider view model steps through this module's font presets. */
    fun holdsFontPresets(sliderModel: Any): Boolean {
        val callback = sliderModel.javaClass.getDeclaredField("onSliderStopTrackingTouch")
            .apply { isAccessible = true }.get(sliderModel) ?: return false
        val group = callback.javaClass.declaredFields
            .firstOrNull { it.type == clocks.presetGroupType }
            ?.apply { isAccessible = true }?.get(callback) ?: return false
        return clocks.isFontPresetGroup(group)
    }

    /**
     * Records the size tab's content as [height] tall in the sheet heights the
     * picker animates its sheet to, unless it already is.
     */
    fun recordSizeTabHeight(height: Int) {
        val flow = sheetHeights.get(null) ?: return
        val getValue = flow.javaClass.getMethod("getValue")
        val current = getValue.invoke(flow) ?: return
        val values = heightFields.map { it.get(current) as Int? }
        if (values[SIZE_HEIGHT] == height) return

        val updated = heightsConstructor.newInstance(
            *values.mapIndexed { index, value -> if (index == SIZE_HEIGHT) height else value }.toTypedArray(),
        )
        flow.javaClass.getMethod("updateState", Any::class.java, Any::class.java).invoke(flow, null, updated)
    }

    /** Makes [slider] a stepped slider from [from] to [to] in steps of [step], at [value]. */
    fun configure(slider: View, from: Float, to: Float, step: Float, value: Float) {
        valueFrom.setFloat(slider, from)
        valueTo.setFloat(slider, to)
        stepSize.setFloat(slider, step)
        dirtyConfig.setBoolean(slider, true)
        setValues.invoke(slider, arrayListOf(value.coerceIn(from, to)))
        slider.postInvalidate()
    }

    /** Calls [onChange] with every new value and whether a person moved it. */
    fun onValueChange(slider: View, onChange: (value: Float, fromUser: Boolean) -> Unit) {
        val listener = Proxy.newProxyInstance(changeListenerType.classLoader, arrayOf(changeListenerType)) { proxy, method, args ->
            when (method.name) {
                "onValueChange" -> onChange(args[1] as Float, args[2] as Boolean).let { null }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                else -> null
            }
        }
        @Suppress("UNCHECKED_CAST")
        (changeListeners.get(slider) as MutableList<Any>).add(listener)
    }

    /**
     * Gives [target] the colours [source] has now. The picker recolours its own
     * sliders from the wallpaper; a slider it did not create follows by copying.
     */
    fun copyColours(source: View, target: View) {
        colourFields.forEach { if (it.get(target) != it.get(source)) it.set(target, it.get(source)) }
        paintFields.forEach {
            val colour = (it.get(source) as Paint).color
            val paint = it.get(target) as Paint
            if (paint.color != colour) paint.color = colour
        }
        // Setting the fill redraws the slider, so it is only set when it differs.
        val fill = shapeFillColor.get(shapeState.get(thumbDrawable.get(source)))
        val targetThumb = thumbDrawable.get(target)
        if (shapeFillColor.get(shapeState.get(targetThumb)) != fill) shapeSetFillColor.invoke(targetThumb, fill)
    }

    private companion object {
        const val SHEET_BINDER = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder"
        const val PRESET_SLIDER_COLLECTOR = "$SHEET_BINDER\$bind\$13\$1\$12\$1"

        const val SIZE_HEIGHT = 2

        val COLOUR_FIELDS = listOf("trackColorActive", "trackColorInactive", "tickColorActive", "tickColorInactive")
        val PAINT_FIELDS = listOf("activeTrackPaint", "inactiveTrackPaint", "activeTicksPaint", "inactiveTicksPaint")

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
