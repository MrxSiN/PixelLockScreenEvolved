package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.constrain
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.validate

/**
 * The Size tab of Wallpaper & style's clock sheet, with the "Clock size" row
 * this module adds.
 *
 * For Google's clocks the tab is as the picker made it: its "Large" switch, and
 * no row. For this module's clocks the slider is what sizes the clock, so the
 * row shows in place of the switch and its text, and the switch is left on
 * (the large clock whenever notifications allow).
 *
 * The height is recorded again whenever the rows shown change it, because the
 * picker animates its sheet to each tab's height, measured once, and holds the
 * tab's content to it.
 */
internal class PickerSizeTab(
    private val content: ViewGroup,
    private val row: View,
    private val largeSwitch: CompoundButton?,
    private val sliders: PickerSliderApi,
    private val logger: Logger,
) {

    private val originalRows = (0 until content.childCount).map(content::getChildAt)
    private var heightFailureReported = false

    init {
        val params = ConstraintParams.forChild(content, 0, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.constrain("topToTop", ConstraintParams.PARENT)
        params.constrain("leftToLeft", ConstraintParams.PARENT)
        params.constrain("rightToRight", ConstraintParams.PARENT)
        params.validate()
        content.addView(row, params)
        content.viewTreeObserver.addOnGlobalLayoutListener { keepHeight() }
    }

    /** Shows the tab for one of this module's clocks when [ours], or for Google's. */
    fun showFor(ours: Boolean) {
        originalRows.forEach { it.visibility = if (ours) View.GONE else View.VISIBLE }
        row.visibility = if (ours) View.VISIBLE else View.GONE
        if (ours && largeSwitch?.isChecked == false) largeSwitch.isChecked = true
    }

    private fun keepHeight() {
        if (content.width <= 0) return
        content.measure(
            View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        runCatching { sliders.recordSizeTabHeight(content.measuredHeight) }
            .onFailure { if (!heightFailureReported) logger.warn("Size tab height could not be recorded", it) }
            .onFailure { heightFailureReported = true }
    }
}
