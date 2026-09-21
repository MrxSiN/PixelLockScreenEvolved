package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver

/**
 * How the always-on display draws the lock screen wallpaper: one appearance at
 * a time, as Wallpaper & style's Always-on display page picks it. [value] is
 * what is stored and what its shader is told.
 */
internal enum class AodWallpaperStyle(val value: Int, val title: String, val description: String) {
    /** The photo, dimmed. */
    COLOUR(1, "Color", "Your wallpaper, dimmed"),

    /** The photo in greys, dimmed. */
    BLACK_AND_WHITE(2, "Black & white", "Your wallpaper in greys, dimmed"),

    /**
     * The photo, or only its subject, as sparse dots on black: few pixels lit, for the least
     * power. Each dot is lit nearly as brightly as the clock: the always-on display holds greys
     * unsteadily and dimmed dots flickered.
     */
    DOTS(3, "Dots", "Sparse dots, only the subject where the depth effect found one; uses the least power"),
    ;

    companion object {
        /** The style the always-on display draws, or null while it shows no wallpaper. */
        fun chosen(resolver: ContentResolver): AodWallpaperStyle? =
            if (AodWallpaperSetting.isOn(resolver)) AodAppearanceSetting.get(resolver) else null
    }
}

/** Whether the always-on display keeps the lock screen wallpaper. */
internal object AodWallpaperSetting : SecureSwitch("pixel_lock_screen_evolved_aod_wallpaper")

/**
 * Which [AodWallpaperStyle] the wallpaper is drawn in on the always-on display.
 *
 * It replaces two switches, Dots and Black & white, that could both be on while
 * only one could show. A device that has never chosen here keeps what those
 * switches said, read once, so no one's choice is lost by the change.
 */
internal object AodAppearanceSetting {

    private val setting = SecureIntSetting("pixel_lock_screen_evolved_aod_appearance", UNCHOSEN)
    private val legacyDots = SecureSwitch("pixel_lock_screen_evolved_aod_dots")
    private val legacyBlackAndWhite = SecureSwitch("pixel_lock_screen_evolved_aod_black_and_white")

    fun get(resolver: ContentResolver): AodWallpaperStyle =
        AodWallpaperStyle.entries.firstOrNull { it.value == setting.get(resolver) } ?: when {
            legacyDots.isOn(resolver) -> AodWallpaperStyle.DOTS
            legacyBlackAndWhite.isOn(resolver) -> AodWallpaperStyle.BLACK_AND_WHITE
            else -> AodWallpaperStyle.COLOUR
        }

    fun put(resolver: ContentResolver, style: AodWallpaperStyle) = setting.put(resolver, style.value)

    fun observe(resolver: ContentResolver, onChange: () -> Unit) = setting.observe(resolver, onChange)

    private const val UNCHOSEN = 0
}
