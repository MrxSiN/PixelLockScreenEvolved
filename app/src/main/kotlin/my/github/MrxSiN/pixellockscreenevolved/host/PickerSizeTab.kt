package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton

import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.constrain
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.validate

/**
 * The content slot of the Size tab of Wallpaper & style's clock sheet, with
 * the rows this module adds: "Clock size", and, while the Position tab rides
 * on this slot ([PickerPositionTab]), the position controls
 * ([PickerPositionRows]) in place of everything else.
 *
 * For Google's clocks the Size tab is as the picker made it: its "Large"
 * switch, and none of these rows. For this module's clocks the slider is what
 * sizes the clock, so the rows show in place of the switch and its text, and
 * the switch is left on (the large clock whenever notifications allow). Which
 * clock is previewed is asked whenever the sheet is laid out, which it is as
 * its tabs change, so the slot always matches the clock tapped last, and the
 * Position tab is offered only for one that can be moved ([ClockStyle.isMovable]).
 *
 * The height is recorded again whenever the rows shown change it, because the
 * picker animates its sheet to each tab's height, measured once, and holds the
 * tab's content to it.
 */
internal class PickerSizeTab(
    private val content: ViewGroup,
    private val rows: View,
    private val positionRows: View?,
    private val largeSwitch: CompoundButton?,
    private val sliders: PickerSliderApi,
    private val previewed: () -> ClockStyle?,
    private val state: PickerClockState,
    /** Told of the clock the slot is shown for, every time it changes: one of this module's, or null for Google's. */
    private val onStyle: (ClockStyle?) -> Unit,
    private val logger: Logger,
) {

    private val originalRows = (0 until content.childCount).map(content::getChildAt)
    private var heightFailureReported = false
    private var shown: ClockStyle? = null
    private var shownEditing = false
    private var shownOnce = false
    private var recordedHeight = -1
    private var heightGlide: ValueAnimator? = null

    init {
        content.addView(rows, topParams())
        positionRows?.let { content.addView(it, topParams()) }
        state.onPositionEditing { showFor(previewed()) }
        content.viewTreeObserver.addOnGlobalLayoutListener {
            showFor(previewed())
            keepHeight()
        }
        showFor(previewed())
    }

    /** Shows the tab for [style], one of this module's clocks, or for Google's where it is null. */
    private fun showFor(style: ClockStyle?) {
        val editing = state.isEditingPosition && positionRows != null
        if (shownOnce && style === shown && editing == shownEditing) return
        // Between the size and the position rows the picker keeps its Size tab, so it animates nothing itself.
        val swapping = shownOnce && style === shown && content.isShown && !SystemMotion.isReduced
        val leaving = if (editing) rows else positionRows
        val coming = if (editing) positionRows else rows
        shownOnce = true
        shown = style
        shownEditing = editing
        val ours = style != null
        originalRows.forEach { it.visibility = if (ours || editing) View.GONE else View.VISIBLE }
        rows.visibility = if (ours && !editing) View.VISIBLE else View.GONE
        positionRows?.visibility = if (editing) View.VISIBLE else View.GONE
        if (ours && largeSwitch?.isChecked == false) largeSwitch.isChecked = true
        onStyle(style)
        // Recorded now, before the picker selects the slot and animates the sheet to it: recorded at the
        // next layout, the sheet first grew to the old rows' height and then snapped to the new one.
        keepHeight(glide = swapping)
        if (swapping && leaving != null && coming != null) crossfade(leaving, coming)
    }

    /** Fades [leaving] out where it stands and [coming] in, as the picker fades its own tabs' content. */
    private fun crossfade(leaving: View, coming: View) {
        leaving.animate().cancel()
        coming.animate().cancel()
        leaving.visibility = View.VISIBLE
        leaving.alpha = 1f
        leaving.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction {
            leaving.visibility = View.GONE
            leaving.alpha = 1f
        }.start()
        coming.alpha = 0f
        coming.animate().alpha(1f).setStartDelay(FADE_OUT_MS).setDuration(FADE_IN_MS).start()
    }

    /** Parameters pinning an added view across the top of the slot. */
    private fun topParams(): ViewGroup.LayoutParams = ConstraintParams.forChild(content, 0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        constrain("topToTop", ConstraintParams.PARENT)
        constrain("leftToLeft", ConstraintParams.PARENT)
        constrain("rightToRight", ConstraintParams.PARENT)
        validate()
    }

    /**
     * Records the slot's height for the rows now shown; with [glide], moves
     * the recorded height there on the spatial spring, a frame at a time, so
     * the sheet grows or shrinks with the rows rather than jumping.
     */
    private fun keepHeight(glide: Boolean = false) {
        if (!glide && heightGlide != null) return
        // The slot has no width of its own until it is first shown; it is as wide as the sheet's content.
        val width = content.width.takeIf { it > 0 } ?: (content.parent as? View)?.width ?: 0
        if (width <= 0) return
        content.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val target = content.measuredHeight
        val from = recordedHeight
        heightGlide?.cancel()
        heightGlide = null
        if (!glide || from <= 0 || from == target) return record(target)
        heightGlide = RESIZE.animator(
            update = { seconds -> record((from + (target - from) * RESIZE.valueAt(seconds)).roundToInt()) },
            end = {
                if (heightGlide != null) {
                    heightGlide = null
                    record(target)
                }
            },
        ).apply { start() }
    }

    private fun record(height: Int) {
        if (height == recordedHeight) return
        recordedHeight = height
        runCatching { sliders.recordSizeTabHeight(height) }
            .onFailure { if (!heightFailureReported) logger.warn("Size tab height could not be recorded", it) }
            .onFailure { heightFailureReported = true }
    }

    private companion object {
        /** The picker's own sheet spring, as its tabs resize the sheet. */
        val RESIZE = ExpressiveSpring.DEFAULT_SPATIAL
        const val FADE_OUT_MS = 100L
        const val FADE_IN_MS = 200L
    }
}
