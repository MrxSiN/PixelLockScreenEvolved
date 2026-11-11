package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.ColorStateList
import android.graphics.Paint
import android.view.View

import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * The clock sheet's binding coroutine, `ClockFloatingSheetBinder.bind`'s body.
 * Kotlin numbers it by position, so a build that renumbered it is searched for
 * by what it holds ([my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostSymbols]).
 */
internal const val SHEET_BINDING = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder\$bind\$13\$1"

/**
 * Wallpaper & style's clock sheet and its Material slider, reached through the
 * picker's class loader.
 *
 * The picker ships Material shrunk: the slider's setters are gone, so its range,
 * value and colours are set the way the picker's own code sets them, through
 * the fields and the one method R8 kept. Lambda fields R8 generated are found by
 * type rather than by name. HOOK_NOTES.md records the shapes.
 */
internal class PickerSliderApi(classLoader: ClassLoader) {

    private val sheetBinding = classLoader.hostClass(SHEET_BINDING)

    /**
     * Binds the whole clock sheet: every tab's content, once per sheet.
     *
     * Android 17 QPR1 folded `ClockFloatingSheetBinder.bind` into its one caller,
     * leaving only the body it launches. That body holds everything the call
     * used to be given, read back through [sheetView] and [sheetOptions]. It is
     * the resuming of a coroutine, so it runs more than once per sheet.
     */
    val bindSheet: Method = sheetBinding.getDeclaredMethod("invokeSuspend", Any::class.java)

    private val bindingView = sheetBinding.field("\$view")
    private val bindingOptions = sheetBinding.field("\$optionsViewModel")
    private val bindingColors = runCatching { sheetBinding.field("\$colorUpdateViewModel") }.getOrNull()
    private val bindingLifecycle = runCatching { sheetBinding.field("\$lifecycleOwner") }.getOrNull()

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
    /**
     * The slider's thumbs. Material kept one thumb drawable per slider until
     * Android 17 QPR1, and keeps a list of them since, one per value; these
     * sliders carry a single value, so the colour is read off the first.
     */
    private val thumbDrawables = runCatching { sliderType.field("defaultThumbDrawables") }.getOrNull()
    private val thumbDrawable = thumbDrawables ?: sliderType.field("defaultThumbDrawable")
    private val shapeDrawableType = load("com.google.android.material.shape.MaterialShapeDrawable", classLoader)
    private val shapeState = shapeDrawableType.field("drawableState")
    private val shapeFillColor = load("com.google.android.material.shape.MaterialShapeDrawable\$MaterialShapeDrawableState", classLoader)
        .field("fillColor")
    private val shapeSetFillColor = shapeDrawableType.getMethod("setFillColor", ColorStateList::class.java)

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
        val fill = shapeFillColor.get(shapeState.get(thumb(source) ?: return))
        val targetThumb = thumb(target) ?: return
        if (shapeFillColor.get(shapeState.get(targetThumb)) != fill) shapeSetFillColor.invoke(targetThumb, fill)
    }

    /** The one thumb a single-valued slider draws. */
    private fun thumb(slider: Any): Any? = thumbDrawable.get(slider)?.let { held ->
        if (thumbDrawables == null) held else (held as List<*>).firstOrNull()
    }

    /** The sheet's own view, from the call that binds it. */
    fun sheetView(binding: Any): View? = bindingView.get(binding) as View?

    /** The picker's options, which hold the clock picker's view model. */
    fun sheetOptions(binding: Any): Any? = bindingOptions.get(binding)

    /**
     * The picker's colours as the sheet follows them, for a switch of this
     * module's in the sheet ([PickerThemeColors]), or null where the sheet's
     * binding no longer holds them.
     */
    fun sheetColors(binding: Any, colors: PickerThemeColors): PickerThemeColors.InFragment? {
        val viewModel = bindingColors?.get(binding) ?: return null
        val owner = bindingLifecycle?.get(binding) ?: return null
        return colors.InFragment(viewModel, owner)
    }

    private companion object {
        const val SHEET_BINDER = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder"

        const val SIZE_HEIGHT = 2

        val COLOUR_FIELDS = listOf("trackColorActive", "trackColorInactive", "tickColorActive", "tickColorInactive")
        val PAINT_FIELDS = listOf("activeTrackPaint", "inactiveTrackPaint", "activeTicksPaint", "inactiveTicksPaint")

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)
    }
}
