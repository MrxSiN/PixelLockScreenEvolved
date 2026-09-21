package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.view.View

/**
 * Wallpaper & style's Depth effect page: the effect's switch, where it stands
 * with the current lock screen photo as SystemUI reports it ([DepthStatus]),
 * and a way to have the photo's subject found again ([DepthReprocessRequest])
 * where the cut-out came out wrong or none was found, without clearing
 * anything by hand.
 */
internal class PickerDepthEffectPage(private val rows: PickerRows) {

    /** Opens the page; [unavailable] says why SystemUI cannot take the effect now, or is null when it can. */
    fun show(context: Context, unavailable: String?, onClosed: () -> Unit) {
        val resolver = context.contentResolver
        val page = PickerPage(context, rows, TITLE, onClosed)

        val main = rows.group(context)
        val switchRow = rows.row(main, TITLE, unavailable ?: DESCRIPTION) ?: return
        main.addView(switchRow.view)
        rows.shape(main)
        page.content.addView(main)

        val status = rows.group(context)
        val state = rows.row(status, "", null) ?: return
        val action = rows.row(status, "", null) ?: return
        status.addView(state.view)
        status.addView(action.view)
        val statusTitle = rows.sectionTitle(context, STATUS_TITLE)
        page.content.addView(statusTitle)
        page.content.addView(status)
        action.view.setOnClickListener {
            if (action.view.isEnabled) DepthReprocessRequest.ask(resolver)
        }

        fun render(on: Boolean, found: DepthStatus) {
            val text = StatusText.of(found)
            state.title.text = text.headline
            rows.describe(state, text.detail)
            action.title.text = text.action.orEmpty()
            rows.describe(action, text.actionDetail)
            action.view.visibility = if (text.action == null) View.GONE else View.VISIBLE
            rows.setEnabled(action, found != DepthStatus.FINDING)
            val shown = if (on && unavailable == null) View.VISIBLE else View.GONE
            statusTitle.visibility = shown
            status.visibility = shown
            rows.shape(status)
        }

        val toggle = rows.switchAtEnd(switchRow, DepthEffectSetting.isOn(resolver)) { DepthEffectSetting.set(resolver, it) }
        rows.setEnabled(switchRow, unavailable == null)
        val follow = {
            val on = DepthEffectSetting.isOn(resolver)
            if (toggle.isChecked != on) toggle.isChecked = on
            render(on, DepthStatus.get(resolver))
        }
        page.keep(DepthEffectSetting.observe(resolver, follow))
        page.keep(DepthStatus.watch(resolver) { follow() })
        page.show()
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
    }
}
