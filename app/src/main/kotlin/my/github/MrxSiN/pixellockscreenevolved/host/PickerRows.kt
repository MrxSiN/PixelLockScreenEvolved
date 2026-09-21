package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.constrain
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.validate

/**
 * Wallpaper & style's own option rows, for everything this module shows in
 * the picker: the entries of its Lock screen list and the rows of the pages
 * they open ([PickerPage]). Each row is the picker's own entry layout, with the
 * picker's switch or a radio button at its end, in rounded groups, coloured as
 * the picker colours its entries and recoloured as the previewed theme changes
 * ([PickerThemeColors]).
 *
 * One instance serves one fragment of the picker, whose colours it follows.
 */
internal class PickerRows(
    fragment: Any,
    colors: PickerThemeColors?,
    private val logger: Logger,
) {

    /** An inflated entry: the row and its two lines of text. */
    class Row(val view: ViewGroup, val title: TextView, val description: TextView)

    private val themed = colors?.let { runCatching { it.InFragment(fragment) }.getOrNull() }

    /** The latest colour of each theme colour followed, and the views painted with it. */
    private val tones = mutableMapOf<String, Tone>()

    private class Tone(var color: Int? = null) {
        val painters: MutableMap<Any, (Int) -> Unit> = Collections.synchronizedMap(WeakHashMap())
    }

    /** One of the picker's entries, titled [title] and described by [description], not yet added anywhere. */
    fun row(parent: ViewGroup, title: String, description: String?, tag: String? = null): Row? {
        val context = parent.context
        val view = LayoutInflater.from(context).inflate(resource(context, "layout", ENTRY_LAYOUT), parent, false) as? ViewGroup ?: return null
        view.tag = tag
        val titleView = view.findViewById<TextView>(resource(context, "id", "option_entry_title")) ?: return null
        val descriptionView = view.findViewById<TextView>(resource(context, "id", "option_entry_description")) ?: return null
        titleView.text = title
        paint(titleView, "colorOnSurface", titleView::setTextColor)
        paint(descriptionView, "colorOnSurfaceVariant", descriptionView::setTextColor)
        return Row(view, titleView, descriptionView).also { describe(it, description) }
    }

    /** Sets [row]'s second line, hiding it where there is none. */
    fun describe(row: Row, description: String?) {
        row.description.text = description.orEmpty()
        row.description.visibility = if (description.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * The picker's switch at the end of [row], set to [on], telling [onChange] of every flip. Where the row
     * [opensPage], a chevron and a thin line part the switch from the rest of the row, whose tap is then the
     * row's own, as Android's settings mark such a row.
     */
    fun switchAtEnd(row: Row, on: Boolean, opensPage: Boolean = false, onChange: (Boolean) -> Unit): CompoundButton {
        val toggle = pickerSwitch(row.view).apply {
            id = View.generateViewId()
            isChecked = on
        }
        val recolour = themed?.let { runCatching { it.follow(toggle) }.getOrNull() }
        if (recolour == null) tintFromSystemPalette(toggle)
        toggle.setOnCheckedChangeListener { _, checked ->
            recolour?.invoke(checked)
            onChange(checked)
        }
        placeAtEnd(row, toggle)
        if (opensPage) placePageMarks(row, toggle) else row.view.setOnClickListener { if (row.view.isEnabled) toggle.toggle() }
        return toggle
    }

    /** A radio button at the end of [row], checked where [checked]; a tap on the row chooses it. */
    fun radioAtEnd(row: Row, checked: Boolean, onChosen: () -> Unit): RadioButton {
        val radio = RadioButton(row.view.context).apply {
            id = View.generateViewId()
            isChecked = checked
            isClickable = false
            background = null
        }
        placeAtEnd(row, radio)
        paintRadio(radio)
        row.view.setOnClickListener { if (row.view.isEnabled && !radio.isChecked) onChosen() }
        return radio
    }

    /** A vertical group of rows spaced as the picker spaces its lists; give it rows, then call [shape]. */
    fun group(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        runCatching { dividerDrawable = context.getDrawable(resource(context, "drawable", "customization_option_entry_divider")) }
    }

    /** Gives each visible row of [group] the picker's rounded corners for where it sits: top, middle, bottom or alone. */
    fun shape(group: ViewGroup) {
        val rows = (0 until group.childCount).map(group::getChildAt).filter { it.visibility != View.GONE }
        rows.forEachIndexed { index, row ->
            val name = when {
                rows.size == 1 -> "customization_option_entry_singleton_background"
                index == 0 -> "customization_option_entry_top_background"
                index == rows.lastIndex -> "customization_option_entry_bottom_background"
                else -> "customization_option_entry_background"
            }
            setEntryBackground(row, name)
        }
    }

    /** Sets [view]'s background to the picker's entry background [name], tinted as the picker tints its entries. */
    fun setEntryBackground(view: View, name: String) {
        view.setBackgroundResource(resource(view.context, "drawable", name))
        paint(view, "colorSurfaceBright") { view.background?.setTint(it) }
    }

    /** A section title over a group, in the picker's section style. */
    fun sectionTitle(context: Context, text: String): TextView {
        val style = resource(context, "style", "SectionTitleTextStyle")
        return TextView(context, null, 0, style).apply {
            this.text = text
            val padding = dp(context, SECTION_TITLE_PADDING_DP)
            setPaddingRelative(padding, dp(context, SECTION_TITLE_TOP_DP), padding, dp(context, SECTION_TITLE_BOTTOM_DP))
            paint(this, "colorPrimary", ::setTextColor)
        }
    }

    /** Greys out, and stops, [row] while it is not [enabled], as Material greys a disabled control. */
    fun setEnabled(row: Row, enabled: Boolean) {
        row.view.alpha = if (enabled) 1f else UNAVAILABLE_ALPHA
        row.view.isEnabled = enabled
        (0 until row.view.childCount).forEach { row.view.getChildAt(it).isEnabled = enabled }
    }

    /** Calls [apply] with the theme colour [name] now and as it changes, while [owner] lives. */
    fun paint(owner: Any, name: String, apply: (Int) -> Unit) {
        val theme = themed ?: return
        val tone = tones.getOrPut(name) {
            Tone().also { tone ->
                runCatching {
                    theme.follow(name) { color ->
                        tone.color = color
                        synchronized(tone.painters) { tone.painters.values.toList() }.forEach { it(color) }
                    }
                }.onFailure { logger.warn("The picker's colour $name could not be followed", it) }
            }
        }
        tone.painters[owner] = apply
        tone.color?.let(apply)
    }

    private fun paintRadio(radio: RadioButton) {
        var primary: Int? = null
        var variant: Int? = null
        fun tint() {
            val checked = primary ?: return
            val unchecked = variant ?: return
            radio.buttonTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(checked, unchecked))
        }
        paint(radio, "colorPrimary") { primary = it; tint() }
        paint(radio, "colorOnSurfaceVariant") { variant = it; tint() }
    }

    /** Puts [control] at the end of [row], centred, with the row's text ending before it. */
    private fun placeAtEnd(row: Row, control: View) {
        val gap = dp(row.view.context, CONTROL_GAP_DP)
        row.view.addView(
            control,
            ConstraintParams.forChild(row.view, control.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = gap
                constrain("topToTop", ConstraintParams.PARENT)
                constrain("bottomToBottom", ConstraintParams.PARENT)
                constrain("endToEnd", ConstraintParams.PARENT)
                validate()
            },
        )
        endTextBefore(row, control.id)
    }

    /**
     * The marks Android 17's settings give a row that opens a page and has a switch: a chevron, then a
     * thin line parting it from [control], with the row's text ending before the chevron. Spaced as
     * Settings → Display & touch spaces them.
     */
    private fun placePageMarks(row: Row, control: View) {
        val context = row.view.context
        val line = View(context).apply { id = View.generateViewId() }
        row.view.addView(
            line,
            ConstraintParams.forChild(row.view, dp(context, DIVIDER_WIDTH_DP), dp(context, DIVIDER_HEIGHT_DP)).apply {
                marginEnd = dp(context, LINE_TO_SWITCH_DP)
                constrain("topToTop", ConstraintParams.PARENT)
                constrain("bottomToBottom", ConstraintParams.PARENT)
                constrain("endToStart", control.id)
                validate()
            },
        )
        val chevron = ChevronDrawable(context)
        val arrow = android.widget.ImageView(context).apply {
            id = View.generateViewId()
            setImageDrawable(chevron)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val side = ChevronDrawable.sizePx(context)
        row.view.addView(
            arrow,
            ConstraintParams.forChild(row.view, side, side).apply {
                marginStart = dp(context, CONTROL_GAP_DP)
                marginEnd = dp(context, CHEVRON_TO_LINE_DP)
                constrain("topToTop", ConstraintParams.PARENT)
                constrain("bottomToBottom", ConstraintParams.PARENT)
                constrain("endToStart", line.id)
                validate()
            },
        )
        paint(line, "colorOutline") { line.setBackgroundColor(it) }
        paint(arrow, "colorOnSurfaceVariant") { chevron.color = it }
        if (themed == null) {
            line.setBackgroundColor(context.getColor(android.R.color.system_neutral2_500))
            chevron.color = context.getColor(android.R.color.system_neutral2_300)
        }
        endTextBefore(row, arrow.id)
    }

    private fun endTextBefore(row: Row, id: Int) {
        listOf(row.title, row.description).forEach { text ->
            text.layoutParams = text.layoutParams.apply {
                constrain("endToEnd", ConstraintParams.UNSET)
                constrain("endToStart", id)
                validate()
            }
        }
    }

    /**
     * The picker's own switch, taken from the clock sheet's Size tab layout so
     * it is styled as the picker styles it; built in code it misses its style
     * and fails to measure. The platform's switch where that layout is gone.
     */
    private fun pickerSwitch(parent: ViewGroup): CompoundButton {
        val context = parent.context
        val sizeContent = runCatching {
            LayoutInflater.from(context).inflate(resource(context, "layout", "floating_sheet_clock_size_content"), parent, false) as ViewGroup
        }.getOrNull()
        val toggle = sizeContent?.findViewById<View>(resource(context, "id", "clock_style_clock_size_switch")) as? CompoundButton
            ?: return android.widget.Switch(context)
        (toggle.parent as ViewGroup).removeView(toggle)
        return toggle
    }

    /**
     * Colours [toggle] from the system's dynamic palette, as Material 3 colours
     * a switch, where the picker's own switch colouring cannot be used; left to
     * its style the switch would be grey.
     */
    private fun tintFromSystemPalette(toggle: CompoundButton) {
        val context = toggle.context
        val dark = context.resources.configuration.isNightModeActive
        fun color(id: Int) = context.getColor(id)
        fun states(checked: Int, unchecked: Int) = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checked, unchecked),
        )
        val track = if (dark) {
            states(color(android.R.color.system_accent1_200), color(android.R.color.system_neutral1_800))
        } else {
            states(color(android.R.color.system_accent1_600), color(android.R.color.system_neutral1_100))
        }
        val thumb = if (dark) {
            states(color(android.R.color.system_accent1_800), color(android.R.color.system_neutral2_400))
        } else {
            states(color(android.R.color.system_accent1_0), color(android.R.color.system_neutral2_500))
        }
        val icon = if (dark) {
            states(color(android.R.color.system_accent1_100), color(android.R.color.system_neutral1_800))
        } else {
            states(color(android.R.color.system_accent1_900), color(android.R.color.system_neutral1_100))
        }
        listOf("setTrackTintList" to track, "setThumbTintList" to thumb, "setThumbIconTintList" to icon).forEach { (name, tint) ->
            runCatching { toggle.javaClass.getMethod(name, ColorStateList::class.java).invoke(toggle, tint) }
        }
    }

    companion object {
        private const val ENTRY_LAYOUT = "customization_option_entry_lock_screen_notifications"
        private const val CONTROL_GAP_DP = 16f
        private const val DIVIDER_WIDTH_DP = 1f
        private const val DIVIDER_HEIGHT_DP = 40f
        private const val LINE_TO_SWITCH_DP = 12f

        /** From the chevron's 24dp box to the line: the glyph ends 13dp before it. */
        private const val CHEVRON_TO_LINE_DP = 5f
        private const val SECTION_TITLE_PADDING_DP = 20f
        private const val SECTION_TITLE_TOP_DP = 24f
        private const val SECTION_TITLE_BOTTOM_DP = 8f

        /** How faded a row is while it cannot be changed, as Material fades a disabled control. */
        private const val UNAVAILABLE_ALPHA = 0.38f

        /** A resource of the picker's own, by type and name. */
        fun resource(context: Context, type: String, name: String): Int = context.resources.getIdentifier(name, type, context.packageName)

        fun dp(context: Context, value: Float): Int =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
    }
}
