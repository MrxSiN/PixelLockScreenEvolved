package my.github.MrxSiN.pixellockscreenevolved.clock

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.IosClockStyle

/**
 * Every clock style this module offers.
 *
 * This list is the only thing that changes when a style is added, and nothing
 * here knows how any style draws.
 */
object ClockStyles {

    val all: List<ClockStyle> = listOf(
        IosClockStyle,
    )
}
