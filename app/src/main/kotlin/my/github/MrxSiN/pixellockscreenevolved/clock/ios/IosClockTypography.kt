package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Typeface

/**
 * The type the iOS lock screen is set in, approximated with the system fonts.
 *
 * SF Pro is not on the device. The time is set in Roboto Flex, whose width
 * axis lets large sizes narrow into tall numerals the way iOS stretches its
 * clock; the date is the system sans-serif. The faces, the sizer and the
 * thumbnail all read these values, so they cannot drift apart.
 */
internal object IosClockTypography {

    const val TIME_WEIGHT = 600
    const val DATE_WEIGHT = 600

    /** Roboto Flex's own width range; 100 is its normal width. */
    const val NORMAL_WIDTH = 100f
    const val NARROWEST_WIDTH = 25f

    /** iOS tightens its large numerals; ems, as TextView and Paint take them. */
    const val TIME_LETTER_SPACING = -0.03f

    /** The date is drawn a little translucent over the wallpaper. */
    const val DATE_ALPHA = 0.85f

    /** Skeleton for "Saturday, September 13", localized by the platform. */
    const val DATE_SKELETON = "EEEEMMMMd"

    val timeTypeface: Typeface = Typeface.create("roboto-flex", Typeface.NORMAL)
    val dateTypeface: Typeface =
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), DATE_WEIGHT, false)

    /** Variation settings for the time at [width] on Roboto Flex's width axis. */
    fun timeVariation(width: Float = NORMAL_WIDTH): String =
        "'wght' $TIME_WEIGHT, 'wdth' $width"
}
