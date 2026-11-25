package my.github.MrxSiN.pixellockscreenevolved.clock

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.IosClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.CinemaClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.DotClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.HeadlineClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.HorizonClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.HyperClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.OneClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.PosterClockStyle

/**
 * Every clock style this module offers.
 *
 * This list is the only thing that changes when a style is added, and nothing
 * here knows how any style draws.
 */
fun clockStyles(): List<ClockStyle> = listOf(
    IosClockStyle(),
    HyperClockStyle(),
    HeadlineClockStyle(),
    PosterClockStyle(),
    OneClockStyle(),
    HorizonClockStyle(),
    DotClockStyle(),
    CinemaClockStyle(),
)
