package my.github.MrxSiN.pixellockscreenevolved.clock

import my.github.MrxSiN.pixellockscreenevolved.clock.ios.IosClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.AnalogClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.NumeralsClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.SplitClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.styles.StackedClockStyle

/**
 * Every clock style this module offers.
 *
 * This list is the only thing that changes when a style is added, and nothing
 * here knows how any style draws.
 */
object ClockStyles {

    fun all(): List<ClockStyle> = listOf(
        IosClockStyle(),
        StackedClockStyle(),
        NumeralsClockStyle(),
        AnalogClockStyle(),
        SplitClockStyle(),
    )
}
