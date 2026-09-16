package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver

/** How the always-on display draws the lock screen wallpaper; [value] is what its shader is told. */
internal enum class AodWallpaperStyle(val value: Int) {
    /** The photo, dimmed. */
    COLOUR(1),

    /** The photo in greys, dimmed. */
    BLACK_AND_WHITE(2),

    /**
     * The photo, or only its subject, as sparse dots on black: few pixels lit, for the least
     * power. Each dot is lit nearly as brightly as the clock: the always-on display holds greys
     * unsteadily and dimmed dots flickered.
     */
    DOTS(3);

    companion object {
        /** The style Wallpaper & style's switches choose, or null while the wallpaper is off. */
        fun chosen(resolver: ContentResolver): AodWallpaperStyle? = when {
            !AodWallpaperSetting.isOn(resolver) -> null
            AodDotsSetting.isOn(resolver) -> DOTS
            AodBlackAndWhiteSetting.isOn(resolver) -> BLACK_AND_WHITE
            else -> COLOUR
        }
    }
}

/** Whether the always-on display keeps the lock screen wallpaper. */
internal object AodWallpaperSetting : SecureSwitch("pixel_lock_screen_evolved_aod_wallpaper")

/** Whether the always-on wallpaper is drawn as dots rather than the whole photo dimmed. */
internal object AodDotsSetting : SecureSwitch("pixel_lock_screen_evolved_aod_dots")

/** Whether the dimmed always-on wallpaper is in greys. */
internal object AodBlackAndWhiteSetting : SecureSwitch("pixel_lock_screen_evolved_aod_black_and_white")

