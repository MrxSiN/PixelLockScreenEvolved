package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.drawable.Drawable

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize

/** The iOS lock screen clock: the date over a large, centred time. */
object IosClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_IOS"
    override val name: String = "iOS"
    override val description: String = "iOS style clock with the date above the time"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace =
        IosClockFace(context, size)

    override fun createThumbnail(context: Context): Drawable = IosClockThumbnail(context)
}
