package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.Context

/**
 * Wallpaper & style's Always-on display page: whether the always-on display
 * keeps the lock screen wallpaper, and in which one appearance
 * ([AodWallpaperStyle]), chosen like any single choice, so no two can be on at
 * once.
 */
internal class PickerAlwaysOnDisplayPage(private val rows: PickerRows) {

    /** Opens the page; [unavailable] says why SystemUI cannot take the wallpaper now, or is null when it can. */
    fun show(context: Context, unavailable: String?, onClosed: () -> Unit) {
        val resolver = context.contentResolver
        val page = PickerPage(context, rows, TITLE, onClosed)

        val main = rows.group(context)
        val wallpaper = rows.row(main, WALLPAPER_TITLE, unavailable ?: WALLPAPER_DESCRIPTION) ?: return
        main.addView(wallpaper.view)
        rows.shape(main)
        page.content.addView(main)

        val appearance = rows.group(context)
        val choices = AodWallpaperStyle.entries.mapNotNull { style ->
            rows.row(appearance, style.title, style.description)?.also { appearance.addView(it.view) }?.let { style to it }
        }
        rows.shape(appearance)
        page.content.addView(rows.sectionTitle(context, APPEARANCE_TITLE))
        page.content.addView(appearance)

        val radios = choices.associate { (style, row) ->
            style to rows.radioAtEnd(row, false) {
                AodAppearanceSetting.put(resolver, style)
            }
        }
        val toggle = rows.switchAtEnd(wallpaper, AodWallpaperSetting.isOn(resolver)) { AodWallpaperSetting.set(resolver, it) }
        fun render() {
            val chosen = AodAppearanceSetting.get(resolver)
            val shown = AodWallpaperSetting.isOn(resolver)
            if (toggle.isChecked != shown) toggle.isChecked = shown
            radios.forEach { (style, radio) -> radio.isChecked = style == chosen }
            choices.forEach { (_, row) -> rows.setEnabled(row, unavailable == null && shown) }
        }

        rows.setEnabled(wallpaper, unavailable == null)
        page.keep(AodWallpaperSetting.observe(resolver, ::render))
        page.keep(AodAppearanceSetting.observe(resolver, ::render))
        page.show()
    }

    companion object {
        const val TITLE = "Always-on display"
        private const val WALLPAPER_TITLE = "Show lock screen wallpaper"
        private const val WALLPAPER_DESCRIPTION = "Keep your wallpaper, dimmed, when the screen is off"
        private const val APPEARANCE_TITLE = "Appearance"

        /** What the Lock screen list says of the always-on display: "Wallpaper · Dots", or that it shows none. */
        fun summary(resolver: ContentResolver): String =
            AodWallpaperStyle.chosen(resolver)?.let { "Wallpaper · ${it.title}" } ?: "Wallpaper off"
    }
}
