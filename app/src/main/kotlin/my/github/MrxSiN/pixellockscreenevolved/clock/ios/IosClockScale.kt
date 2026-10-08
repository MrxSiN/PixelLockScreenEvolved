package my.github.MrxSiN.pixellockscreenevolved.clock.ios

import my.github.MrxSiN.pixellockscreenevolved.clock.parts.StretchFit

/**
 * How large the iOS time is set, and how much it is stretched.
 *
 * The time always spans the lock screen's text column, so its
 * width sets the text size; the size chosen sets only its height, from
 * numerals as tall as the Pixel's own large clock to [LARGEST_TO_STOCK] times
 * that, reached by stretching the numerals vertically. Kept free of Android so the arithmetic can be
 * unit tested.
 */
internal object IosClockScale {

    /**
     * Numeral height of the large face's smallest size, against the screen's
     * short side, as drawn: the Pixel's large clock, measured on a Pixel 8 Pro
     * (`CP3A.261005.005`: 393 px drawn on a 1344 px wide screen, matched by measuring this face there).
     */
    const val STOCK_NUMERAL_TO_SHORT_SIDE = 0.304f

    /** The largest size's numeral height, against the stock clock's. */
    const val LARGEST_TO_STOCK = 2.2f

    /**
     * The text size at which a time [inkWidthPerTextSize] wide per pixel of
     * text size spans [columnWidth], and the vertical stretch that draws its
     * numerals ([numeralPerTextSize] tall per pixel of text size) as tall as
     * [size] asks, from 0 for the stock height to 1 for [LARGEST_TO_STOCK]
     * times it.
     */
    fun spanning(size: Float, shortSide: Float, numeralPerTextSize: Float, inkWidthPerTextSize: Float, columnWidth: Float): StretchFit.Fit {
        if (numeralPerTextSize <= 0f || inkWidthPerTextSize <= 0f) return StretchFit.Fit(textSize = 0f, stretch = 1f)
        val textSize = columnWidth / inkWidthPerTextSize
        val stock = shortSide * STOCK_NUMERAL_TO_SHORT_SIDE
        val height = stock + stock * (LARGEST_TO_STOCK - 1f) * size.coerceIn(0f, 1f)
        return StretchFit.Fit(textSize = textSize, stretch = height / (numeralPerTextSize * textSize))
    }

    /** The text size that draws numerals [numeralToShortSide] of the screen's short side tall, unstretched. */
    fun unstretched(numeralToShortSide: Float, shortSide: Float, numeralPerTextSize: Float): StretchFit.Fit {
        if (numeralPerTextSize <= 0f) return StretchFit.Fit(textSize = 0f, stretch = 1f)
        return StretchFit.Fit(textSize = shortSide * numeralToShortSide / numeralPerTextSize, stretch = 1f)
    }
}
