package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView

/**
 * A switch row of the clock sheet's own, the Size tab's "Large clock" row,
 * inflated afresh with another title and description, so the switches this
 * module adds ("Adaptive position", "Adaptive size") read as the sheet's other
 * controls. Its views are given ids of their own so nothing finds them for the
 * picker's row, and its constraints are pointed at the new ids.
 */
internal object PickerSwitchRow {

    /** The row and its switch, titled [title] over [description]; null where the sheet's layout is gone. */
    fun inflate(sheet: View, title: String, description: String): Pair<ViewGroup, CompoundButton>? {
        val context = sheet.context
        val row = runCatching {
            LayoutInflater.from(context).inflate(PickerRows.resource(context, "layout", SIZE_CONTENT), null, false) as ViewGroup
        }.getOrNull() ?: return null
        val titleView = row.findViewById<TextView>(PickerRows.resource(context, "id", TITLE_ID)) ?: return null
        val descriptionView = row.findViewById<TextView>(PickerRows.resource(context, "id", DESCRIPTION_ID)) ?: return null
        val toggle = row.findViewById<View>(PickerRows.resource(context, "id", SWITCH_ID)) as? CompoundButton ?: return null
        titleView.text = title
        descriptionView.text = description
        toggle.contentDescription = title

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
        row.setOnClickListener { toggle.toggle() }
        return row to toggle
    }

    private const val SIZE_CONTENT = "floating_sheet_clock_size_content"
    private const val TITLE_ID = "clock_style_clock_size_title"
    private const val DESCRIPTION_ID = "clock_style_clock_size_description"
    private const val SWITCH_ID = "clock_style_clock_size_switch"
}
