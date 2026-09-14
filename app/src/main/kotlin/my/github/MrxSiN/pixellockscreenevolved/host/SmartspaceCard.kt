package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

/**
 * SystemUI's lock screen smartspace card: weather forecasts, events, and the
 * like. SystemUI keeps the view in the keyguard at all times and hides it
 * (`GONE`) while it has nothing to show.
 */
internal object SmartspaceCard {

    private const val ID_NAME = "bc_smartspace_view"

    /** The card's view id, as SystemUI's resources name it, or 0 where it has none. */
    fun id(view: View): Int = view.resources.getIdentifier(ID_NAME, "id", view.context.packageName)

    /** Whether the window [view] is in shows a card now. */
    fun isShownAround(view: View): Boolean {
        val id = id(view).takeIf { it != 0 } ?: return false
        val card = view.rootView.findViewById<View>(id) ?: return false
        return card.isShown && card.height > 0
    }
}
