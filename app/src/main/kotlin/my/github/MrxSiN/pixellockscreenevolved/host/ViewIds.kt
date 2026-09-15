package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

/** The view under this one with the id SystemUI names [name] in its own resources; null if there is none. */
internal fun <T : View> View.findViewByName(name: String): T? =
    resources.getIdentifier(name, "id", context.packageName).takeIf { it != 0 }?.let { findViewById(it) }
