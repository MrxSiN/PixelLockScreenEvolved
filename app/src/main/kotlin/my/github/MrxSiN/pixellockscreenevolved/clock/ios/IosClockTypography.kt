package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import android.graphics.Typeface

/**
 * The type the iOS lock screen is set in, approximated with the system fonts.
 *
 * SF Pro is not on the device. The time is set in Roboto Flex, whose
 * parametric axes let large sizes turn into the tall, thin numerals of the
 * largest iOS clock (see [IosClockScale]); the date is the system sans-serif.
 * The face and the thumbnail both read these values, so they cannot drift apart.
 */
internal object IosClockTypography {

    private const val DATE_WEIGHT = 600

    /** iOS tightens its large numerals; ems, as TextView and Paint take them. */
    const val TIME_LETTER_SPACING = -0.03f

    /** The date is drawn a little translucent over the wallpaper. */
    const val DATE_ALPHA = 0.85f

    /** Skeleton for "Saturday, September 13", localized by the platform. */
    const val DATE_SKELETON = "EEEEMMMMd"

    val timeTypeface: Typeface = Typeface.create("roboto-flex", Typeface.NORMAL)
    val dateTypeface: Typeface =
        Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), DATE_WEIGHT, false)
}
