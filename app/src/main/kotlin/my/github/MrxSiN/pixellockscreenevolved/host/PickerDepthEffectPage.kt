package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.view.View

/**
 * Wallpaper & style's Depth effect page: the effect's switch, where it stands
 * with the current lock screen photo as SystemUI reports it ([DepthStatus]),
 * a way to have the photo's subject found again ([DepthReprocessRequest])
 * where the cut-out came out wrong or none was found, without clearing
 * anything by hand, or corrected by hand ([DepthEditRequest]), what stands in
 * front of the clock ([DepthLayers]), how the clock and the subject share the
 * screen ([DepthOcclusion]), which subjects stand in front ([DepthSubjects]),
 * and whether the clock moves with the phone's tilt ([DepthMotionSetting]).
 */
internal class PickerDepthEffectPage(private val rows: PickerRows) {

    /** Opens the page; [unavailable] says why SystemUI cannot take the effect now, or is null when it can. */
    fun show(context: Context, unavailable: String?, onClosed: () -> Unit) {
        val resolver = context.contentResolver
        val page = PickerPage(context, rows, TITLE, onClosed)

        val main = rows.group(context)
        val switchRow = rows.row(main, TITLE, unavailable ?: DESCRIPTION) ?: return
        main.addView(switchRow.view)
        val motionRow = rows.row(main, MOTION_TITLE, MOTION_DESCRIPTION) ?: return
        main.addView(motionRow.view)
        rows.shape(main)
        page.content.addView(main)

        val status = rows.group(context)
        val state = rows.row(status, "", null) ?: return
        val action = rows.row(status, "", null) ?: return
        val edit = rows.row(status, EDIT_TITLE, EDIT_DESCRIPTION) ?: return
        status.addView(state.view)
        status.addView(action.view)
        status.addView(edit.view)
        edit.view.setOnClickListener {
            if (edit.view.isEnabled) DepthEditRequest.ask(resolver)
        }
        val statusTitle = rows.sectionTitle(context, STATUS_TITLE)
        page.content.addView(statusTitle)
        page.content.addView(status)
        action.view.setOnClickListener {
            if (action.view.isEnabled) DepthReprocessRequest.ask(resolver)
        }

        val layers = choices(context, page, LAYERS_TITLE, DepthLayers.entries, { it.title }, { it.description }) {
            DepthLayers.put(resolver, it)
        }
        val occlusion = choices(context, page, OCCLUSION_TITLE, DepthOcclusion.entries, { it.title }, { it.description }) {
            DepthOcclusion.put(resolver, it)
        }
        val subjects = choices(context, page, SUBJECTS_TITLE, DepthSubjects.entries, { it.title }, { it.description }) {
            DepthSubjects.put(resolver, it)
        }

        fun render(on: Boolean, found: DepthStatus) {
            val text = StatusText.of(found)
            state.title.text = text.headline
            rows.describe(state, text.detail)
            action.title.text = text.action.orEmpty()
            rows.describe(action, text.actionDetail)
            action.view.visibility = if (text.action == null) View.GONE else View.VISIBLE
            rows.setEnabled(action, found != DepthStatus.FINDING)
            edit.view.visibility = if (found == DepthStatus.READY) View.VISIBLE else View.GONE
            val shown = if (on && unavailable == null) View.VISIBLE else View.GONE
            motionRow.view.visibility = shown
            rows.shape(main)
            layers.render(DepthLayers.get(resolver), shown)
            statusTitle.visibility = shown
            status.visibility = shown
            rows.shape(status)
            occlusion.render(DepthOcclusion.get(resolver), shown)
            subjects.render(DepthSubjects.get(resolver), shown)
        }

        val toggle = rows.switchAtEnd(switchRow, DepthEffectSetting.isOn(resolver)) { DepthEffectSetting.set(resolver, it) }
        rows.setEnabled(switchRow, unavailable == null)
        val motion = rows.switchAtEnd(motionRow, DepthMotionSetting.isOn(resolver)) { DepthMotionSetting.set(resolver, it) }
        val follow = {
            val on = DepthEffectSetting.isOn(resolver)
            if (toggle.isChecked != on) toggle.isChecked = on
            DepthMotionSetting.isOn(resolver).let { if (motion.isChecked != it) motion.isChecked = it }
            render(on, DepthStatus.get(resolver))
        }
        page.keep(DepthEffectSetting.observe(resolver, follow))
        page.keep(DepthStatus.watch(resolver) { follow() })
        page.keep(DepthOcclusion.observe(resolver, follow))
        page.keep(DepthSubjects.observe(resolver, follow))
        page.keep(DepthLayers.observe(resolver, follow))
        page.keep(DepthMotionSetting.observe(resolver, follow))
        page.show()
    }

    /** A titled group of single choices, one row each, [onChosen] hearing the one tapped. */
    private fun <T> choices(
        context: Context,
        page: PickerPage,
        title: String,
        entries: List<T>,
        titleOf: (T) -> String,
        descriptionOf: (T) -> String,
        onChosen: (T) -> Unit,
    ): Choices<T> {
        val group = rows.group(context)
        val radios = entries.mapNotNull { entry ->
            val row = rows.row(group, titleOf(entry), descriptionOf(entry)) ?: return@mapNotNull null
            group.addView(row.view)
            entry to rows.radioAtEnd(row, false) { onChosen(entry) }
        }
        rows.shape(group)
        val heading = rows.sectionTitle(context, title)
        page.content.addView(heading)
        page.content.addView(group)
        return Choices(listOf(heading, group), radios)
    }

    private class Choices<T>(private val views: List<View>, private val radios: List<Pair<T, android.widget.RadioButton>>) {
        fun render(chosen: T, visibility: Int) {
            views.forEach { it.visibility = visibility }
            radios.forEach { (entry, radio) -> radio.isChecked = entry == chosen }
        }
    }

    /** What the page says for one [DepthStatus], and the action it offers, if any. */
    private class StatusText(val headline: String, val detail: String, val action: String? = null, val actionDetail: String? = null) {
        companion object {
            fun of(status: DepthStatus): StatusText = when (status) {
                DepthStatus.NONE -> StatusText(
                    "Waiting for a photo",
                    "The subject is found once the lock screen shows a photo wallpaper",
                )
                DepthStatus.FINDING -> StatusText(
                    "Finding the subject…",
                    "This can take up to half a minute",
                    "Reprocess wallpaper",
                    "Available once the subject is found",
                )
                DepthStatus.READY -> StatusText(
                    "Subject detected",
                    "Your wallpaper's subject stands in front of the clock",
                    "Reprocess wallpaper",
                    "Look for the subject again, more carefully, if its outline looks wrong",
                )
                DepthStatus.NO_SUBJECT -> StatusText(
                    "Couldn't detect the subject",
                    "No subject could be cut out of this wallpaper",
                    "Try again",
                    "Look at the wallpaper again, more carefully",
                )
            }
        }
    }

    companion object {
        const val TITLE = "Depth effect"
        const val DESCRIPTION = "Place the wallpaper subject in front of the clock"
        private const val STATUS_TITLE = "Wallpaper subject"
        private const val OCCLUSION_TITLE = "Clock and subject"
        private const val LAYERS_TITLE = "In front of the clock"
        private const val MOTION_TITLE = "Tilt motion"
        private const val MOTION_DESCRIPTION = "The clock shifts a little against the photo as you tilt the phone"
        private const val EDIT_TITLE = "Edit subject"
        private const val EDIT_DESCRIPTION = "Paint to add to the subject or take from it"
        private const val SUBJECTS_TITLE = "Subjects in front"
    }
}
