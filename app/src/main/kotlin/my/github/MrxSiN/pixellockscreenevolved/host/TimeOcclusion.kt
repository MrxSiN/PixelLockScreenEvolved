package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Bitmap
import android.view.View

/** Whatever is drawn in front of a lock screen clock's time, such as the depth effect's subject. */
internal interface TimeOcclusion {

    /**
     * Erases from [picture], a drawing of [time] at its own size, the parts of it
     * hidden behind something now; false when nothing hides any of it.
     */
    fun eraseSubject(time: View, picture: Bitmap): Boolean

    /**
     * A value that stays equal for as long as [eraseSubject] would erase the same
     * from a drawing of [time], so a drawing made earlier can be told apart from
     * one that no longer matches.
     */
    fun stateOf(time: View): Any?
}
