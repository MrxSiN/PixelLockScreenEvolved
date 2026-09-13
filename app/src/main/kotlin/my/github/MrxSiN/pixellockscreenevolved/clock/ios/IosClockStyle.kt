package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.content.Context
import android.graphics.drawable.Drawable

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FontSource

/** The iOS lock screen clock: the date over a large, centred time. */
class IosClockStyle(private val fontSource: FontSource) : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_IOS"
    override val name: String = "iOS"
    override val description: String = "iOS style clock with the date above the time"
    override val isResizable: Boolean = true
    override val fontNames: List<String> = listOf("Roboto Flex", "Inter")

    /** Loaded once, on the first face; falls back to Roboto Flex if the asset cannot be read. */
    private var inter: IosNumeralFont? = null

    /**
     * The large face writes the date above the time. The small face leaves it
     * out: SystemUI always shows its own date and weather line under a small
     * clock, and the date would be written twice.
     */
    override fun createFace(context: Context, size: FaceSize): ClockFace =
        IosClockFace(context, fontsFor(context), showsDate = size == FaceSize.LARGE)

    override fun createThumbnail(context: Context): Drawable = IosClockThumbnail(context)

    private fun fontsFor(context: Context): List<IosNumeralFont> {
        val second = inter
            ?: (fontSource.load(context, InterNumerals.ASSET_PATH)?.let(::InterNumerals) ?: RobotoFlexNumerals)
                .also { inter = it }
        return listOf(RobotoFlexNumerals, second)
    }
}
