package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The Size tab of Wallpaper & style's clock sheet, with the "Clock size" row
 * this module adds.
 *
 * For Google's clocks the tab shows its own "Large" switch with the row under
 * it, disabled. For this module's clocks the slider is what sizes the clock, so
 * the switch and its text are hidden, the switch is left on (the large clock
 * whenever notifications allow), and the row moves to the top.
 *
 * The picker animates its sheet to each tab's height, measured once, and holds
 * the tab's content to it, so the height is recorded again whenever the tab's
 * rows change. The original rows are centred in the content, so while they
 * show, the room for the new row is bottom padding they are not centred in.
 */
internal class PickerSizeTab(
    private val content: ViewGroup,
    private val row: View,
    private val above: View,
    private val largeSwitch: CompoundButton?,
    private val rowGap: Int,
    private val sliders: PickerSliderApi,
    private val logger: Logger,
) {

    private val originalRows = (0 until content.childCount).map(content::getChildAt)
    private val basePadding = content.paddingBottom
    private var originalHeight = 0
    private var ours = false
    private var heightFailureReported = false

    init {
        val params = generateLayoutParams(content, ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT))
        params.setConstraint("leftToLeft", PARENT)
        params.setConstraint("rightToRight", PARENT)
        content.addView(row, params)
        content.clipToPadding = false
        placeRow()
        content.viewTreeObserver.addOnGlobalLayoutListener { keepHeight() }
    }

    /** Shows the tab for one of this module's clocks when [ours], or for Google's. */
    fun showFor(ours: Boolean) {
        this.ours = ours
        originalRows.forEach { it.visibility = if (ours) View.GONE else View.VISIBLE }
        if (ours && largeSwitch?.isChecked == false) largeSwitch.isChecked = true
        row.alpha = if (ours) 1f else DISABLED_ALPHA
        (row as? ViewGroup)?.let { group -> (0 until group.childCount).forEach { group.getChildAt(it).isEnabled = ours } }
        placeRow()
    }

    /** Under the switch's text, or at the top where the text is hidden. */
    private fun placeRow() {
        val params = row.layoutParams as ViewGroup.MarginLayoutParams
        params.topMargin = if (ours) 0 else rowGap
        params.setConstraint("topToTop", if (ours) PARENT else UNSET)
        params.setConstraint("topToBottom", if (ours) UNSET else above.id)
        params.validateConstraints()
        row.layoutParams = params
    }

    private fun keepHeight() {
        if (content.width <= 0) return

        row.measure(
            View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val rowParams = row.layoutParams as ViewGroup.MarginLayoutParams
        if (rowParams.height != row.measuredHeight) row.layoutParams = rowParams.apply { height = row.measuredHeight }

        val height: Int
        val padding: Int
        if (ours) {
            height = content.paddingTop + row.measuredHeight + basePadding
            padding = basePadding
        } else {
            if (originalHeight == 0) originalHeight = naturalHeight()
            height = originalHeight + rowGap + row.measuredHeight
            padding = basePadding + rowGap + row.measuredHeight
        }

        if (content.paddingBottom != padding) {
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, padding)
        }
        val contentParams = content.layoutParams
        if (contentParams.height != height) content.layoutParams = contentParams.apply { this.height = height }

        runCatching { sliders.recordSizeTabHeight(height) }
            .onFailure { if (!heightFailureReported) logger.warn("Size tab height could not be recorded", it) }
            .onFailure { heightFailureReported = true }
    }

    /**
     * The height the tab's own rows need, as the picker measured it before the
     * row was added: ConstraintLayout leaves the row, hung below them, out of
     * its measured height, so only the padding made for it is taken off.
     */
    private fun naturalHeight(): Int {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return content.measuredHeight - (content.paddingBottom - basePadding)
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
        const val PARENT = 0
        const val UNSET = -1

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
