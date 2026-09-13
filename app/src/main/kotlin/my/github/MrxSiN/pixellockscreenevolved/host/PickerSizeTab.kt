package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The Size tab of Wallpaper & style's clock sheet, with the "Clock size" row
 * this module adds.
 *
 * For Google's clocks the tab is as the picker made it: its "Large" switch, and
 * no row. For this module's clocks the slider is what sizes the clock, so the
 * row shows in place of the switch and its text, and the switch is left on
 * (the large clock whenever notifications allow).
 *
 * The picker animates its sheet to each tab's height, measured once, and holds
 * the tab's content to it, so the height is recorded again whenever the rows
 * shown change it.
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
        val params = generateLayoutParams(content, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
        params.setConstraint("topToTop", PARENT)
        params.setConstraint("leftToLeft", PARENT)
        params.setConstraint("rightToRight", PARENT)
        params.validateConstraints()
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

    private companion object {
        const val PARENT = 0

        fun generateLayoutParams(content: ViewGroup, source: ViewGroup.LayoutParams): ViewGroup.MarginLayoutParams =
            ViewGroup::class.java.getDeclaredMethod("generateLayoutParams", ViewGroup.LayoutParams::class.java)
                .apply { isAccessible = true }
                .invoke(content, source) as ViewGroup.MarginLayoutParams

        fun ViewGroup.LayoutParams.setConstraint(name: String, value: Int) {
            javaClass.getField(name).setInt(this, value)
        }

        /** Resolves the constraint fields just set, as inflation does; skipped where R8 removed it. */
        fun ViewGroup.LayoutParams.validateConstraints() {
            runCatching { javaClass.getMethod("validate").invoke(this) }
        }
    }
}
