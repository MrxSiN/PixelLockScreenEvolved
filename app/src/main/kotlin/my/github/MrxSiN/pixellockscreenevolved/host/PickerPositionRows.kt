package my.github.MrxSiN.pixellockscreenevolved.host

import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The content of the clock sheet's Position tab ([PickerPositionTab]):
 *
 * - "Adaptive position", the sheet's own switch row ([PickerSwitchRow]). On, the clock stands where it hides least
 *   of the wallpaper's subject; off, it stands where it is dragged in the
 *   preview ([PickerClockDrag]), which [PickerDragHint] says beside the
 *   preview. The preview is the only place a height is chosen, so no row
 *   repeats it.
 * - "Rotation", a slider built as the Size tab's "Clock size" is, from 90
 *   degrees left to 90 right in single degrees, the angle written after its
 *   label and a tick felt on each of [ClockRotation.DETENTS]. Twisting the
 *   preview's clock with two fingers turns it too, and the slider follows.
 *   A "Reset" button at the end of its label stands the clock upright,
 *   dimmed while it is upright already.
 *
 * A choice previews on the clock at once and is kept until Apply, as the size
 * is ([PickerClockState]).
 */
internal class PickerPositionRows(
    sheet: View,
    private val state: PickerClockState,
    colors: PickerThemeColors.InFragment?,
    private val sliders: PickerSliderApi,
    /** The Style tab's own slider, whose colours the Rotation slider copies. */
    colourSource: View,
) {

    private val rotationRow = PickerClockSliders.sliderRow(sheet, ROTATION)

    /** Stands the clock upright; shares the Rotation label's line, at its end. */
    private val reset = TextView(sheet.context).apply {
        text = RESET
        gravity = Gravity.CENTER
        minWidth = PickerRows.dp(context, TOUCH_DP)
        minHeight = PickerRows.dp(context, TOUCH_DP)
        val padding = PickerRows.dp(context, PADDING_DP)
        setPadding(padding, 0, padding, 0)
        PickerRows.resource(context, "attr", TEXT_APPEARANCE).takeIf { it != 0 }?.let { attr ->
            val value = TypedValue()
            if (context.theme.resolveAttribute(attr, value, true)) setTextAppearance(value.resourceId)
        }
        val ripple = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)) setBackgroundResource(ripple.resourceId)
        colors?.let { runCatching { it.follow(RESET_COLOUR, ::setTextColor) } }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            state.choosePosition(state.shownPosition().copy(rotation = 0f))
            showDegrees(0f)
        }
    }

    /** The angle the Rotation slider shows, so a turn chosen elsewhere moves it only when it differs. */
    private var shownDegrees = Float.NaN

    /** The tab's content, or null where the sheet's layouts are gone. */
    val view: ViewGroup? = PickerSwitchRow.inflate(sheet, TITLE, DESCRIPTION)?.let { (row, toggle) ->
        toggle.isChecked = state.shownPosition().adaptive
        val recolour = colors?.let { runCatching { it.follow(toggle) }.getOrNull() }
        toggle.setOnCheckedChangeListener { _, checked ->
            recolour?.invoke(checked)
            state.choosePosition(state.shownPosition().copy(adaptive = checked))
        }
        LinearLayout(sheet.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            rotationRow?.let { addView(it.row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        }
    }

    init {
        rotationRow?.let { (row, label, slider) ->
            besideLabel(row, label)
            PickerClockSliders.followColours(sliders, colourSource, slider)
            sliders.hideTicks(slider)
            showDegrees(state.shownPosition().rotation)
            sliders.onValueChange(slider) { value, fromUser ->
                if (!fromUser) return@onValueChange
                if (ClockRotation.detentOf(value, 0f) >= 0 && value != shownDegrees) slider.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                shownDegrees = value
                showReset(value)
                label.text = labelFor(value)
                state.choosePosition(state.shownPosition().copy(rotation = value))
            }
            // A twist of the preview's clock, or a double tap that stood it upright, moves the slider with it.
            state.onAxesChosen { showDegrees(state.shownPosition().rotation) }
            state.onPositionEditing { if (it) showDegrees(state.shownPosition().rotation) }
        }
    }

    /** Puts the Rotation slider and its label at [degrees], unless they are there already. */
    private fun showDegrees(degrees: Float) {
        val (_, label, slider) = rotationRow ?: return
        if (degrees == shownDegrees) return
        shownDegrees = degrees
        showReset(degrees)
        label.text = labelFor(degrees)
        label.contentDescription = ROTATION
        slider.stateDescription = ClockRotation.nameOf(degrees)
        sliders.configure(slider, -ClockRotation.MAX_DEGREES, ClockRotation.MAX_DEGREES, 1f, degrees)
    }

    /** Puts [reset] at the end of [label]'s line, the label taking the rest of it. */
    private fun besideLabel(row: ViewGroup, label: TextView) {
        val index = row.indexOfChild(label)
        if (index < 0) return
        val params = label.layoutParams
        row.removeView(label)
        val line = LinearLayout(row.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reset, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        row.addView(line, index, (params as? ViewGroup.MarginLayoutParams)?.let { LinearLayout.LayoutParams(it).apply { width = ViewGroup.LayoutParams.MATCH_PARENT } }
            ?: LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Enables [reset] only while the clock is turned, dimmed as a disabled button is otherwise. */
    private fun showReset(degrees: Float) {
        val turned = degrees != 0f
        reset.isEnabled = turned
        reset.alpha = if (turned) 1f else DISABLED_ALPHA
    }

    private fun labelFor(degrees: Float): String = "$ROTATION  ${ClockRotation.label(degrees)}"

    private companion object {
        const val ROTATION = "Rotation"
        const val RESET = "Reset"
        const val RESET_COLOUR = "colorPrimary"
        const val TEXT_APPEARANCE = "textAppearanceLabelLarge"
        const val TOUCH_DP = 48f
        const val PADDING_DP = 12f

        /** Material's opacity for a disabled control's content. */
        const val DISABLED_ALPHA = 0.38f

        const val TITLE = "Adaptive position"
        const val DESCRIPTION = "Keep the clock clear of your wallpaper's subject"
    }
}
