package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView

import java.lang.ref.WeakReference

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The two sliders Wallpaper & style shows for this module's clocks.
 *
 * Style tab: the picker's own preset slider steps through a clock's font
 * presets. It is labelled "Clock face width" for every clock, which is what it
 * means for Google's; for this module's it is named "Font".
 *
 * Size tab: the picker has a switch there and no slider, so a "Clock size" row
 * is added, built from the picker's own slider layout so it looks like the one
 * in the Style tab and copying that slider's colours as the wallpaper changes
 * them. Moving it previews the size at once; Apply stores it through
 * [PickerClockSettings]. [PickerSizeTab] lays the tab out for the chosen clock.
 */
internal class PickerClockSliders(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val state: PickerClockState,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }.toSet()
    private var sizeTab = WeakReference<PickerSizeTab>(null)

    override fun install(classLoader: ClassLoader) {
        val sliders = try {
            PickerSliderApi(classLoader, ClockPluginApi(classLoader))
        } catch (error: ReflectiveOperationException) {
            logger.warn("Picker clock sheet not found; the font and size sliders are unavailable", error)
            return
        }

        hooks.after(sliders.bindSheet) { _, args ->
            val sheet = args.firstOrNull() as? View ?: return@after
            addSizeRow(sliders, sheet)
        }

        hooks.after(sliders.bindPresetSlider) { collector, args ->
            val model = args.firstOrNull()
            val ours = model != null && sliders.holdsFontPresets(model)
            if (model != null) nameFontSlider(sliders.presetSlider(requireNotNull(collector)), ours)
            sizeTab.get()?.showFor(ours)
        }
    }

    private fun addSizeRow(sliders: PickerSliderApi, sheet: View) {
        val context = sheet.context
        val sizeContent = sheet.findViewById<ViewGroup>(id(sheet, "clock_floating_sheet_size_content")) ?: return
        val presetSlider = sheet.findViewById<View>(id(sheet, "clock_axis_preset_slider")) ?: return

        val styleContent = LayoutInflater.from(context)
            .inflate(context.resources.getIdentifier("floating_sheet_clock_style_content", "layout", context.packageName), null, false)
        val row = styleContent.findViewById<ViewGroup>(id(sheet, "clock_face_width_container")) ?: return
        val label = row.findViewById<TextView>(id(sheet, "clock_face_width_label")) ?: return
        val slider = row.findViewById<View>(id(sheet, "clock_axis_preset_slider")) ?: return
        (row.parent as ViewGroup).removeView(row)
        listOf(row, label, slider).forEach { it.id = View.generateViewId() }

        label.text = SIZE_LABEL
        slider.contentDescription = SIZE_LABEL
        state.reset()
        sliders.configure(slider, 0f, (ClockAxes.SIZE_STEPS - 1).toFloat(), 1f, storedSizeStep())
        sliders.onValueChange(slider) { value, fromUser -> if (fromUser) state.chooseSizeStep(value) }
        slider.viewTreeObserver.addOnPreDrawListener {
            runCatching { sliders.copyColours(presetSlider, slider) }
            true
        }

        val largeSwitch = sizeContent.findViewById<View>(id(sheet, "clock_style_clock_size_switch")) as? CompoundButton
        val tab = PickerSizeTab(sizeContent, row, largeSwitch, sliders, logger)
        tab.showFor(state.storedClockId() in styleIds)
        sizeTab = WeakReference(tab)
    }

    /** The size the setting in effect stores, when it is one of this module's clocks. */
    private fun storedSizeStep(): Float =
        state.storedAxis(ClockAxes.SIZE_KEY)?.takeIf { state.storedClockId() in styleIds } ?: 0f

    private fun nameFontSlider(presetSlider: View, ours: Boolean) {
        val context = presetSlider.context
        val label = (presetSlider.parent as? View)?.findViewById<TextView>(id(presetSlider, "clock_face_width_label")) ?: return
        val text = if (ours) {
            FONT_LABEL
        } else {
            context.getString(context.resources.getIdentifier("clock_face_width", "string", context.packageName))
        }
        label.text = text
        presetSlider.contentDescription = text
    }

    private fun id(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", view.context.packageName)

    private companion object {
        const val FONT_LABEL = "Font"
        const val SIZE_LABEL = "Clock size"
    }
}
