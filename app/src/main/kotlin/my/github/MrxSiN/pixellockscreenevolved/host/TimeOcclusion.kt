package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.view.View

/** Whatever is drawn in front of a lock screen clock's time, such as the depth effect's subject. */
internal fun interface TimeOcclusion {

    /**
     * Erases from [picture], a drawing of [time] at its own size, the parts of it
     * hidden behind something now; false when nothing hides any of it.
     */
    fun eraseSubject(time: View, picture: Bitmap): Boolean

    companion object {
        val NONE = TimeOcclusion { _, _ -> false }
    }
}
