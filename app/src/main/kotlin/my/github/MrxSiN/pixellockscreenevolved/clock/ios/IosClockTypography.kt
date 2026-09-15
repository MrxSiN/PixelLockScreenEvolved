package my.github.MrxSiN.pixellockscreenevolved.clock.ios

/**
 * Type values shared by every font the iOS clock can be set in. The fonts
 * themselves, and how each grows with size, are [IosNumeralFont]s.
 */
internal object IosClockTypography {

    /**
     * The font's own spacing. At full weight any tighter and neighbouring
     * figures, a "55" say, run into each other.
     */
    const val TIME_LETTER_SPACING = 0f

    /**
     * Width of the line the time is traced in on the always-on display: thin,
     * so few pixels stay lit, yet solid enough to read at the display's low
     * brightness.
     */
    const val TIME_OUTLINE_DP = 1.5f

    /** The date is drawn a little translucent over the wallpaper. */
    const val DATE_ALPHA = 0.85f

    /**
     * Space between the date's baseline and the top of the time: the space the
     * lock screen keeps between each of its rows, so the date sits no closer to
     * the time than the time to the smartspace card below it.
     */
    const val DATE_TO_TIME_GAP_DP = 40f

    /** Skeleton for "Saturday, September 13", localized by the platform. */
    const val DATE_SKELETON = "EEEEMMMMd"
}
