package my.github.MrxSiN.pixellockscreenevolved.clock.ios

/** Type values of the iOS clock, besides the numerals' own ([FlexClockNumerals]). */
internal object IosClockTypography {

    /**
     * The font's own spacing. At full weight any tighter and neighbouring
     * figures, a "55" say, run into each other.
     */
    const val TIME_LETTER_SPACING = 0f


    /** The date is drawn a little translucent over the wallpaper. */
    const val DATE_ALPHA = 0.85f

    /**
     * Room added above the large face's date row. The lock screen pins the face
     * under the status bar nearer than its rows stand to one another: 75 px
     * from the status bar's ink to the date's, against 105 px between rows, on
     * a Pixel 8 Pro (`CP3A.261005.005`); this makes up the difference.
     */
    const val TOP_EXTRA_DP = 11f

    /**
     * Space between the bottom of the date row and the top of the time view,
     * so the date's ink stands as far from the numerals as the lock screen's
     * rows stand from one another (105 px drawn on a Pixel 8 Pro,
     * `CP3A.261005.005`): the row is taller than its text by its weather icon,
     * and the time view keeps room for the always-on outline above the
     * numerals, so the margin is less than that gap.
     */
    const val DATE_TO_TIME_DP = 25f

    /**
     * Room added under the small face, so SystemUI's date row stands about as
     * far below the time as the notifications stand below the row (82 px
     * without it against about 100 px, on a Pixel 8 Pro); the band the scene
     * lays the small clock out in grows by it
     * ([my.github.MrxSiN.pixellockscreenevolved.host.KeyguardSmallClockBand]).
     */
    const val SMALL_BOTTOM_DP = 8f

    /** Skeleton for "Saturday, September 13", localized by the platform. */
    const val DATE_SKELETON = "EEEEMMMMd"
}
