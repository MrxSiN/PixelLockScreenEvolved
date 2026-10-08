package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The controls Wallpaper & style shows for this module's clocks.
 *
 * Style tab: the picker's own "Clock face width" preset slider steps through a
 * clock's width presets ([ClockPluginApi.pickerConfig]), as it does for Google's.
 *
 * Size tab: the picker has a switch there and no slider, so a "Clock size" row
 * is added, built from the picker's own slider layout so it looks like the one
 * in the Style tab and copying that slider's colours as the wallpaper changes
 * them. The Position tab ([PickerPositionTab]) shows its Adaptive switch
 * ([PickerPositionRows]) in the same slot, and says beside the preview that the
 * clock can be dragged ([PickerDragHint]). Either previews at once; Apply
 * stores them through [PickerClockSettings]. [PickerSizeTab] lays the slot out
 * for the clock previewed and the tab open.
 */
internal class PickerClockSliders(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val state: PickerClockState,
    /** The Position tab, or null where the feature is held off or the picker lacks what it needs. */
    private val positionTab: PickerPositionTab?,
    private val logger: Logger,
) : HostPatch {

    private val styles = styles.associateBy { it.id }

    override fun install(classLoader: ClassLoader) {
        val sliders = try {
            PickerSliderApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Picker clock sheet not found; the size slider is unavailable", error)
            return
        }
        val edits = runCatching { PickerClockEditApi(classLoader) }
            .onFailure { logger.warn("Picker clock choice not found; the Size tab follows the stored clock", it) }
            .getOrNull()
        val colors = runCatching { PickerThemeColors(classLoader) }.getOrNull()

        hooks.after(sliders.bindSheet) { binding, _ ->
            val sheetBinding = requireNotNull(binding)
            val sheet = sliders.sheetView(sheetBinding) ?: return@after
            val clockPicker = sliders.sheetOptions(sheetBinding)?.let { options -> edits?.clockPickerViewModel(options) }
            val previewed = {
                val chosen = runCatching { clockPicker?.let { edits?.chosenClockId(it) } }.getOrNull()
                styles[chosen ?: state.storedClockId()]
            }
            positionTab?.bind(sheetBinding, sheet)
            addRows(sliders, sheet, previewed, colors?.let { sliders.sheetColors(sheetBinding, it) })
        }
        positionTab?.install(hooks)
    }

    private fun addRows(sliders: PickerSliderApi, sheet: View, previewed: () -> ClockStyle?, colors: PickerThemeColors.InFragment?) {
        val context = sheet.context
        val sizeContent = sheet.findViewById<ViewGroup>(id(sheet, "clock_floating_sheet_size_content")) ?: return
        // The call that binds the sheet is a coroutine, and resumes more than once.
        if (sizeContent.findViewWithTag<View>(SIZE_LABEL) != null) return
        val presetSlider = sheet.findViewById<View>(id(sheet, "clock_axis_preset_slider")) ?: return
        val largeSwitch = sizeContent.findViewById<View>(id(sheet, "clock_style_clock_size_switch")) as? CompoundButton

        val size = sliderRow(sheet, SIZE_LABEL) ?: return
        state.reset()
        sliders.onValueChange(size.slider) { value, fromUser -> if (fromUser) state.chooseSizeStep(value) }
        followColours(sliders, presetSlider, size.slider)

        val position = positionTab?.let { PickerPositionRows(sheet, state, colors).view }
        if (position != null) PickerDragHint(sheet, state, colors)
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = SIZE_LABEL
            addView(size.row)
        }
        var steps = 0
        val onStyle = { style: ClockStyle? ->
            positionTab?.isAvailable = style?.isMovable == true
            // Each style has its own number of sizes; the slider is given them as its clock is previewed.
            val count = style?.sizeSteps ?: ClockStyle.DEFAULT_SIZE_STEPS
            if (count != steps) {
                steps = count
                val last = (count - 1).toFloat()
                sliders.configure(size.slider, 0f, last, 1f, (state.chosenSizeStep ?: storedSizeStep()).coerceAtMost(last))
            }
        }
        PickerSizeTab(sizeContent, rows, position, largeSwitch, sliders, previewed, state, onStyle, logger)
    }

    /** The size the setting in effect stores, when it is one of this module's clocks. */
    private fun storedSizeStep(): Float =
        state.storedAxis(ClockAxes.SIZE_KEY)?.takeIf { state.storedClockId() in styles } ?: 0f

    /** One of the Style tab's slider rows: its label and its stepped `Slider`. */
    class SliderRow(val row: ViewGroup, val slider: View)

    companion object {
        private const val SIZE_LABEL = "Clock size"

        /**
         * A copy of the Style tab's "Clock face width" row, labelled [label],
         * inflated afresh and given ids of its own, not yet added anywhere.
         */
        fun sliderRow(sheet: View, label: String): SliderRow? {
            val context = sheet.context
            val styleContent = runCatching {
                LayoutInflater.from(context).inflate(PickerRows.resource(context, "layout", "floating_sheet_clock_style_content"), null, false)
            }.getOrNull() ?: return null
            val row = styleContent.findViewById<ViewGroup>(id(sheet, "clock_face_width_container")) ?: return null
            val labelView = row.findViewById<TextView>(id(sheet, "clock_face_width_label")) ?: return null
            val slider = row.findViewById<View>(id(sheet, "clock_axis_preset_slider")) ?: return null
            (row.parent as ViewGroup).removeView(row)
            // The Style tab spaces the row from the clock list above it; alone, it starts at the sheet's padding, as the Large row does.
            (row.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
            listOf(row, labelView, slider).forEach { it.id = View.generateViewId() }
            labelView.text = label
            labelView.contentDescription = label
            slider.contentDescription = label
            return SliderRow(row, slider)
        }

        /** Keeps [slider] in [source]'s colours, which the picker changes with the wallpaper, before each of its draws. */
        fun followColours(sliders: PickerSliderApi, source: View, slider: View) {
            slider.viewTreeObserver.addOnPreDrawListener {
                runCatching { sliders.copyColours(source, slider) }
                true
            }
        }

        private fun id(view: View, name: String): Int =
            view.resources.getIdentifier(name, "id", view.context.packageName)
    }
}
