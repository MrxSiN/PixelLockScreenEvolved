package my.github.MrxSiN.pixellockscreenevolved.safemode

import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Host

/** How Safe Mode tells a person what it held off, the same in SystemUI's notification and in Wallpaper & style. */
object SafeModeText {

    const val TITLE = "Pixel Lock Screen Evolved Safe Mode"
    const val RESTORE = "Restore feature"
    const val KEEP_DISABLED = "Keep disabled"

    /** "Depth effect was automatically disabled because SystemUI crashed repeatedly after it was enabled." */
    fun message(host: Host, held: Collection<Feature>): String {
        val names = held.map { it.title }
        val listed = when (names.size) {
            0, 1 -> names.joinToString()
            else -> names.dropLast(1).joinToString() + " and " + names.last()
        }
        val (were, it) = if (names.size == 1) "was" to "it" else "were" to "they"
        return "$listed $were automatically disabled because ${host.title} crashed repeatedly after $it $were enabled."
    }
}
