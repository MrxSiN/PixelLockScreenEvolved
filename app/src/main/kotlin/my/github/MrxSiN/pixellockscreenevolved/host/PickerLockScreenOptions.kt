package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.constrain
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintParams.validate
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * This module's options at the end of Wallpaper & style's Lock screen list,
 * after Clock, Shortcuts and the rest: the depth effect's switch, and the
 * wallpaper on the always-on display's switches ([AodWallpaperStyle]). An
 * option that only matters while another is on is greyed out while it is off.
 *
 * The picker fills each list in `CustomizationPickerFragment
 * .initCustomizationOptionEntries`, giving the first and last entries rounded
 * outer corners. Each row is built from the picker's own entry layout, a switch
 * row with the picker's own switch, and joins the list as its last entry.
 */
internal class PickerLockScreenOptions(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    /**
     * One row: its [tag], its [title] and [description], the setting its switch
     * turns on and off, and whether it can be changed now, given the other options.
     */
    private class Option(
        val tag: String,
        val title: String,
        val description: String,
        val setting: SecureSwitch,
        val available: (ContentResolver) -> Boolean = { true },
    )

    private val options = listOf(
        Option(
            tag = "pixel_lock_screen_evolved_depth_effect",
            title = "Depth effect",
            description = "Show a photo's subject in front of the clock",
            setting = DepthEffectSetting,
        ),
        Option(
            tag = "pixel_lock_screen_evolved_aod_wallpaper",
            title = "Wallpaper on always-on display",
            description = "Keep your wallpaper, dimmed, when the screen is off",
            setting = AodWallpaperSetting,
        ),
        Option(
            tag = "pixel_lock_screen_evolved_aod_black_and_white",
            title = "Black & white",
            description = "Dim it in greys",
            setting = AodBlackAndWhiteSetting,
            available = { AodWallpaperSetting.isOn(it) && !AodDotsSetting.isOn(it) },
        ),
        Option(
            tag = "pixel_lock_screen_evolved_aod_dots",
            title = "Dots",
            description = "Draw it as sparse dots, only the subject where the depth effect found one, for the least power",
            setting = AodDotsSetting,
            available = AodWallpaperSetting::isOn,
        ),
    )

    override fun install(classLoader: ClassLoader) {
        val fillList = try {
            Class.forName(FRAGMENT, false, classLoader).declaredMethods
                .firstOrNull { it.name == "access\$initCustomizationOptionEntries" }
                ?: throw NoSuchMethodException("$FRAGMENT.initCustomizationOptionEntries")
        } catch (error: ReflectiveOperationException) {
            logger.warn("Wallpaper & style option list not found; the lock screen options are unavailable", error)
            return
        }

        val colors = runCatching { PickerThemeColors(classLoader) }
            .onFailure { logger.warn("Picker switch colours not found; the lock screen options use the system palette", it) }
            .getOrNull()

        hooks.after(fillList) { _, args ->
            val fragment = args.getOrNull(0) ?: return@after
            val root = args.getOrNull(2) as? View ?: return@after
            if ((args.getOrNull(3) as? Enum<*>)?.name != LOCK_SCREEN) return@after
            val list = root.findViewById<LinearLayout>(id(root, "lock_customization_option_container")) ?: return@after
            options.filter { list.findViewWithTag<View>(it.tag) == null }.forEach { addRow(list, it, fragment, colors) }
        }
    }

    private fun addRow(list: LinearLayout, option: Option, fragment: Any, colors: PickerThemeColors?) {
        val context = list.context
        val row = LayoutInflater.from(context).inflate(layout(context, "customization_option_entry_lock_screen_notifications"), list, false) as ViewGroup
        row.tag = option.tag
        val title = row.findViewById<TextView>(id(row, "option_entry_title")) ?: return
        val description = row.findViewById<TextView>(id(row, "option_entry_description")) ?: return
        title.text = option.title
        val themed = colors?.let { runCatching { it.InFragment(fragment) }.getOrNull() }
        placeAtEnd(row, addSwitch(list, row, description, option, themed), title, description)

        // The entry that was last now sits in the middle of the list, or at its top if it was the only one.
        val neighbour = list.getChildAt(list.childCount - 1)
        neighbour?.setBackgroundResource(
            drawable(context, if (list.childCount == 1) "customization_option_entry_top_background" else "customization_option_entry_background"),
        )
        row.setBackgroundResource(drawable(context, "customization_option_entry_bottom_background"))
        list.addView(row)
        themed?.let { theme ->
            // Tinted as the picker tints every entry; the neighbour's new background needs it too.
            runCatching {
                listOfNotNull(row, neighbour).forEach { entry -> theme.follow("colorSurfaceBright") { entry.background?.setTint(it) } }
                theme.follow("colorOnSurface", title::setTextColor)
                theme.follow("colorOnSurfaceVariant", description::setTextColor)
            }.onFailure { logger.warn("${option.title} entry could not follow the picker's colours", it) }
        }
        showAvailability(list)
    }

    /** Greys out, and stops, every option of this module's in [list] that cannot be changed now. */
    private fun showAvailability(list: LinearLayout) {
        val resolver = list.context.contentResolver
        options.forEach { option ->
            val row = list.findViewWithTag<ViewGroup>(option.tag) ?: return@forEach
            val available = option.available(resolver)
            row.alpha = if (available) 1f else UNAVAILABLE_ALPHA
            row.isEnabled = available
            (0 until row.childCount).forEach { row.getChildAt(it).isEnabled = available }
        }
    }

    /** Puts [control] at the end of [row], centred, with the row's text ending before it. */
    private fun placeAtEnd(row: ViewGroup, control: View, title: TextView, description: TextView) {
        val gap = (CONTROL_GAP_DP * row.resources.displayMetrics.density).toInt()
        row.addView(
            control,
            ConstraintParams.forChild(row, control.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = gap
                constrain("topToTop", ConstraintParams.PARENT)
                constrain("bottomToBottom", ConstraintParams.PARENT)
                constrain("endToEnd", ConstraintParams.PARENT)
                validate()
            },
        )
        listOf(title, description).forEach { text ->
            text.layoutParams = text.layoutParams.apply {
                constrain("endToEnd", ConstraintParams.UNSET)
                constrain("endToStart", control.id)
                validate()
            }
        }
    }

    /** The picker's switch for [option], which a tap anywhere on [row] flips. */
    private fun addSwitch(
        list: LinearLayout,
        row: ViewGroup,
        description: TextView,
        option: Option,
        themed: PickerThemeColors.InFragment?,
    ): View {
        val context = list.context
        description.text = option.description
        val toggle = pickerSwitch(list).apply {
            id = View.generateViewId()
            isChecked = option.setting.isOn(context.contentResolver)
        }
        val recolourSwitch = themed?.let { runCatching { it.follow(toggle) }.getOrNull() }
        if (recolourSwitch == null) tintFromSystemPalette(toggle)
        toggle.setOnCheckedChangeListener { _, on ->
            option.setting.set(context.contentResolver, on)
            recolourSwitch?.invoke(on)
            showAvailability(list)
        }
        row.setOnClickListener { if (row.isEnabled) toggle.toggle() }
        return toggle
    }

    /**
     * The picker's own switch, taken from the clock sheet's Size tab layout so
     * it is styled as the picker styles it; built in code it misses its style
     * and fails to measure. The platform's switch where that layout is gone.
     */
    private fun pickerSwitch(list: ViewGroup): CompoundButton {
        val context = list.context
        val sizeContent = runCatching {
            LayoutInflater.from(context).inflate(layout(context, "floating_sheet_clock_size_content"), list, false) as ViewGroup
        }.getOrNull()
        val toggle = sizeContent?.findViewById<View>(id(sizeContent, "clock_style_clock_size_switch")) as? CompoundButton
            ?: return android.widget.Switch(context)
        (toggle.parent as ViewGroup).removeView(toggle)
        return toggle
    }

    /**
     * Colours [toggle] from the system's dynamic palette, as Material 3 colours
     * a switch, where the picker's own switch colouring cannot be used; left to
     * its style the switch would be grey.
     */
    private fun tintFromSystemPalette(toggle: CompoundButton) {
        val context = toggle.context
        val dark = context.resources.configuration.isNightModeActive
        fun color(id: Int) = context.getColor(id)
        fun states(checked: Int, unchecked: Int) = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checked, unchecked),
        )
        val track = if (dark) {
            states(color(android.R.color.system_accent1_200), color(android.R.color.system_neutral1_800))
        } else {
            states(color(android.R.color.system_accent1_600), color(android.R.color.system_neutral1_100))
        }
        val thumb = if (dark) {
            states(color(android.R.color.system_accent1_800), color(android.R.color.system_neutral2_400))
        } else {
            states(color(android.R.color.system_accent1_0), color(android.R.color.system_neutral2_500))
        }
        val icon = if (dark) {
            states(color(android.R.color.system_accent1_100), color(android.R.color.system_neutral1_800))
        } else {
            states(color(android.R.color.system_accent1_900), color(android.R.color.system_neutral1_100))
        }
        listOf("setTrackTintList" to track, "setThumbTintList" to thumb, "setThumbIconTintList" to icon).forEach { (name, tint) ->
            runCatching { toggle.javaClass.getMethod(name, ColorStateList::class.java).invoke(toggle, tint) }
        }
    }

    private fun id(view: View, name: String): Int = view.resources.getIdentifier(name, "id", view.context.packageName)

    private fun layout(context: Context, name: String): Int = context.resources.getIdentifier(name, "layout", context.packageName)

    private fun drawable(context: Context, name: String): Int = context.resources.getIdentifier(name, "drawable", context.packageName)

    private companion object {
        const val FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
        const val LOCK_SCREEN = "LOCK_SCREEN"
        const val CONTROL_GAP_DP = 16f

        /** How faded an option is while it cannot be changed, as Material fades a disabled control. */
        const val UNAVAILABLE_ALPHA = 0.38f
    }
}
