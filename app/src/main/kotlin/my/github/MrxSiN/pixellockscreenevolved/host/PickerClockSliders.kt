package my.github.MrxSiN.pixellockscreenevolved.host

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * is added under the switch, built from the picker's own slider layout so it
 * looks like the one in the Style tab and copying that slider's colours as the
 * wallpaper changes them. Moving it previews the size at once; Apply stores it
 * through [PickerClockSettings]. It is enabled only while one of this module's
 * clocks is chosen.
 */
internal class PickerClockSliders(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val state: PickerClockState,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }.toSet()
    private var sizeRow = WeakReference<View>(null)
    private var heightFailureReported = false

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
            sizeRow.get()?.let { setEnabled(it, ours) }
        }
    }

    private fun addSizeRow(sliders: PickerSliderApi, sheet: View) {
        val context = sheet.context
        val sizeContent = sheet.findViewById<ViewGroup>(id(sheet, "clock_floating_sheet_size_content")) ?: return
        val description = sizeContent.findViewById<View>(id(sheet, "clock_style_clock_size_description")) ?: return
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

        placeBelow(sizeContent, row, description, topMargin = dp(sheet, ROW_TOP_MARGIN_DP))
        setEnabled(row, state.storedClockId() in styleIds)
        sizeRow = WeakReference(row)
        keepSheetHeight(sliders, sizeContent, row, description)
    }

    /**
     * The picker sizes its sheet to each tab's height, measured once, and then
     * holds the tab's content to that height. The size tab's rows are centred
     * in its content, so rather than growing the content, room for the new row
     * is reserved as bottom padding the row draws into, and the tab's height is
     * recorded with that room added.
     */
    private fun keepSheetHeight(sliders: PickerSliderApi, sizeContent: ViewGroup, row: View, above: View) {
        val basePadding = sizeContent.paddingBottom
        sizeContent.clipToPadding = false
        sizeContent.viewTreeObserver.addOnGlobalLayoutListener {
            if (sizeContent.width <= 0) return@addOnGlobalLayoutListener
            row.measure(
                View.MeasureSpec.makeMeasureSpec(sizeContent.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val rowParams = row.layoutParams as ViewGroup.MarginLayoutParams
            if (rowParams.height != row.measuredHeight) row.layoutParams = rowParams.apply { height = row.measuredHeight }
            val room = rowParams.topMargin + row.measuredHeight
            if (sizeContent.paddingBottom != basePadding + room) {
                sizeContent.setPadding(sizeContent.paddingLeft, sizeContent.paddingTop, sizeContent.paddingRight, basePadding + room)
            }
            val height = above.bottom + sizeContent.getChildAt(0).top + room
            runCatching { sliders.recordSizeTabHeight(height) }
                .onFailure { if (!heightFailureReported) logger.warn("Size tab height could not be recorded", it) }
                .onFailure { heightFailureReported = true }
        }
    }

    /** The size the setting in effect stores, when it is one of this module's clocks. */
    private fun storedSizeStep(): Float =
        state.storedAxis(ClockAxes.SIZE_KEY)?.takeIf { state.storedClockId() in styleIds } ?: 0f

    /**
     * Adds [row] to the size tab's ConstraintLayout under [above], spanning the
     * width.
     */
    private fun placeBelow(content: ViewGroup, row: View, above: View, topMargin: Int) {
        val generate = ViewGroup::class.java.getDeclaredMethod("generateLayoutParams", ViewGroup.LayoutParams::class.java)
            .apply { isAccessible = true }
        val params = generate.invoke(content, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT)) as ViewGroup.MarginLayoutParams
        params.topMargin = topMargin
        params.setConstraint("topToBottom", above.id)
        params.setConstraint("leftToLeft", PARENT)
        params.setConstraint("rightToRight", PARENT)
        params.validateConstraints()
        content.addView(row, params)
    }

    private fun ViewGroup.LayoutParams.setConstraint(name: String, value: Int) {
        javaClass.getField(name).setInt(this, value)
    }

    /** Resolves the constraint fields just set, as inflation does; skipped where R8 removed it. */
    private fun ViewGroup.LayoutParams.validateConstraints() {
        runCatching { javaClass.getMethod("validate").invoke(this) }
    }

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

    private fun setEnabled(row: View, enabled: Boolean) {
        row.alpha = if (enabled) 1f else DISABLED_ALPHA
        (row as? ViewGroup)?.let { group -> (0 until group.childCount).forEach { group.getChildAt(it).isEnabled = enabled } }
    }

    private fun id(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", view.context.packageName)

    private fun dp(view: View, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, view.resources.displayMetrics).toInt()

    private companion object {
        const val FONT_LABEL = "Font"
        const val SIZE_LABEL = "Clock size"
        const val ROW_TOP_MARGIN_DP = 16f
        const val DISABLED_ALPHA = 0.38f
        const val PARENT = 0
    }
}
