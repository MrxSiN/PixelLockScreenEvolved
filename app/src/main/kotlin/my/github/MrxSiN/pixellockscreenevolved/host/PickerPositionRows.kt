package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView

/**
 * The content of the clock sheet's Position tab ([PickerPositionTab]): one
 * "Adaptive position" row, built from the sheet's own Size tab row so it reads
 * as the sheet's other controls. On, the clock stands where it hides least of
 * the wallpaper's subject; off, it stands where it is dragged in the preview
 * ([PickerClockDrag]), which [PickerDragHint] says beside the preview. The
 * preview is the only place a position is chosen, so no row repeats it.
 *
 * A choice previews on the clock at once and is kept until Apply, as the size
 * is ([PickerClockState]).
 */
internal class PickerPositionRows(
    sheet: View,
    private val state: PickerClockState,
    colors: PickerThemeColors.InFragment?,
) {

    /** The tab's content, or null where the sheet's layouts are gone. */
    val view: ViewGroup? = adaptiveRow(sheet)?.also { (row, toggle) ->
        toggle.isChecked = state.shownPosition().adaptive
        val recolour = colors?.let { runCatching { it.follow(toggle) }.getOrNull() }
        toggle.setOnCheckedChangeListener { _, checked ->
            recolour?.invoke(checked)
            state.choosePosition(state.shownPosition().copy(adaptive = checked))
        }
        row.setOnClickListener { toggle.toggle() }
    }?.first

    /**
     * The Size tab's own switch row, titled "Adaptive position": inflated
     * afresh, its views given ids of their own so nothing finds them for the
     * picker's row, and its constraints pointed at the new ids.
     */
    private fun adaptiveRow(sheet: View): Pair<ViewGroup, CompoundButton>? {
        val context = sheet.context
        val row = runCatching {
            LayoutInflater.from(context).inflate(PickerRows.resource(context, "layout", SIZE_CONTENT), null, false) as ViewGroup
        }.getOrNull() ?: return null
        val titleView = row.findViewById<TextView>(PickerRows.resource(context, "id", TITLE_ID)) ?: return null
        val descriptionView = row.findViewById<TextView>(PickerRows.resource(context, "id", DESCRIPTION_ID)) ?: return null
        val toggle = row.findViewById<View>(PickerRows.resource(context, "id", SWITCH_ID)) as? CompoundButton ?: return null
        titleView.text = TITLE
        descriptionView.text = DESCRIPTION
        toggle.contentDescription = TITLE

        val renamed = listOf(titleView, descriptionView, toggle).associate { view -> view.id to View.generateViewId().also { view.id = it } }
        for (child in listOf(titleView, descriptionView, toggle)) {
            val params = child.layoutParams
            for (field in params.javaClass.fields) {
                if (field.type != Int::class.javaPrimitiveType || !field.name.contains("To")) continue
                renamed[field.getInt(params)]?.let { field.setInt(params, it) }
            }
            runCatching { params.javaClass.getMethod("validate").invoke(params) }
        }
        row.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return row to toggle
    }

    private companion object {
        const val SIZE_CONTENT = "floating_sheet_clock_size_content"
        const val TITLE_ID = "clock_style_clock_size_title"
        const val DESCRIPTION_ID = "clock_style_clock_size_description"
        const val SWITCH_ID = "clock_style_clock_size_switch"

        const val TITLE = "Adaptive position"
        const val DESCRIPTION = "Keep the clock clear of your wallpaper's subject"
    }
}
