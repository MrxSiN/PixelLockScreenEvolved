package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.Display
import android.view.View

/**
 * A clock face, large or small, the photo's subject can stand in front of ([DepthLayer]): one of
 * this module's faces ([ClockFaceAdapter]), or a clock of Google's own
 * ([StockClockTarget]).
 */
internal interface DepthTarget {

    /** The clock's whole view, which is drawn as one layer while the subject is erased from it. */
    val view: View

    /** The part of [view] the subject stands in front of, and must leave readable. */
    val depthView: View

    /** How far the clock has come in as it changes size, from 0 to 1. */
    val arrival: Float

    /** How far into the always-on display, from 0 awake to 1 dozing. */
    val dozeFraction: Float

    /** Whether the time is moving to new text, so what is drawn now is not what it will show. */
    val timeChanging: Boolean

    /** Counts the changes to how the time is written, so a measure of it can tell it is out of date. */
    val timeChanges: Int

    /** The colour the time is drawn in, or null where it is not known. */
    val inkColor: Int?

    /** Whether the clock may move a little against the photo as the phone tilts ([MotionParallax]). */
    val followsMotion: Boolean
}

/**
 * One of Google's own clock faces, which SystemUI built and the module only
 * looks at: its view is all of it, its time changes every minute, it is either
 * awake or on the always-on display, and its colour is not known. It is not
 * moved: SystemUI places and animates it. Whether it dozes is read from its
 * display's state, which the framework keeps cached, since it is asked several
 * times a frame; `PowerManager.isInteractive` is a call to the system server.
 */
internal class StockClockTarget(override val view: View) : DepthTarget {

    override val depthView: View get() = view
    override val arrival: Float get() = 1f
    override val dozeFraction: Float get() = if ((view.display?.state ?: Display.STATE_ON) == Display.STATE_ON) 0f else 1f
    override val timeChanging: Boolean get() = false
    override val timeChanges: Int get() = (System.currentTimeMillis() / MINUTE_MS).toInt()
    override val inkColor: Int? get() = null
    override val followsMotion: Boolean get() = false

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
