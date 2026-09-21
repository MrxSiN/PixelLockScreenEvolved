package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.FeatureAvailability
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.HostStartupReport
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SnapshotStore
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * This module's entries at the end of Wallpaper & style's Lock screen list,
 * after Clock, Shortcuts and the rest:
 *
 * - Depth effect, with its switch beside it, opening its page
 *   ([PickerDepthEffectPage]) for how it stands and to find the subject again;
 * - Always-on display, saying what it shows, opening its page
 *   ([PickerAlwaysOnDisplayPage]) for the wallpaper and its appearance;
 * - Compatibility & diagnostics ([PickerDiagnosticsDialog]).
 *
 * An entry SystemUI cannot take now, its internals changed or its feature
 * held off or stopped, is greyed out and says why in place of its description
 * ([FeatureAvailability]).
 *
 * The picker fills each list in `CustomizationPickerFragment
 * .initCustomizationOptionEntries`, giving the first and last entries rounded
 * outer corners. Each entry is built from the picker's own ([PickerRows]) and
 * joins the list as its last. How much taller that makes the list
 * ([addedHeight]) is left out of how far the page collapses its previews
 * ([PickerPreviewCollapse]).
 */
internal class PickerLockScreenOptions(
    private val hooks: Hooks,
    private val diagnostics: PickerDiagnosticsDialog,
    private val logger: Logger,
) : HostPatch {

    private val followedLists = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val tags = listOf(DEPTH_TAG, AOD_TAG, DIAGNOSTICS_TAG)

    override fun install(classLoader: ClassLoader) {
        val fillList = try {
            // Reached through a synthetic accessor before Android 17 QPR1, and
            // as a method of the fragment itself since.
            Class.forName(FRAGMENT, false, classLoader).declaredMethods
                .firstOrNull { it.name == "access\$initCustomizationOptionEntries" || it.name == "initCustomizationOptionEntries" }
                ?: throw NoSuchMethodException("$FRAGMENT.initCustomizationOptionEntries")
        } catch (error: ReflectiveOperationException) {
            logger.warn("Wallpaper & style option list not found; the lock screen options are unavailable", error)
            return
        }

        val colors = runCatching { PickerThemeColors(classLoader) }
            .onFailure { logger.warn("Picker switch colours not found; the lock screen options use the system palette", it) }
            .getOrNull()

        // The fragment is the call's own target where it has one, and its first
        // argument through the synthetic accessor older builds went through; the
        // root and the screen are named by what they are rather than by where
        // they sit, so the same body reads either shape.
        hooks.after(fillList) { target, args ->
            val fragment = target ?: args.firstOrNull() ?: return@after
            val root = args.filterIsInstance<View>().firstOrNull() ?: return@after
            if (args.filterIsInstance<Enum<*>>().firstOrNull()?.name != LOCK_SCREEN) return@after
            val list = root.findViewById<LinearLayout>(PickerRows.resource(root.context, "id", LIST)) ?: return@after
            if (list.findViewWithTag<View>(DEPTH_TAG) != null) return@after
            Entries(list, PickerRows(fragment, colors, logger)).add()
            keepRowsWithList(list)
        }
    }

    /** This module's entries in one Lock screen [list], built with [rows]. */
    private inner class Entries(private val list: LinearLayout, private val rows: PickerRows) {

        private val context = list.context
        private val resolver = context.contentResolver
        private lateinit var depth: PickerRows.Row
        private lateinit var depthSwitch: android.widget.CompoundButton
        private lateinit var aod: PickerRows.Row

        fun add() {
            depth = append(DEPTH_TAG, PickerDepthEffectPage.TITLE, PickerDepthEffectPage.DESCRIPTION) ?: return
            depthSwitch = rows.switchAtEnd(depth, DepthEffectSetting.isOn(resolver), opensPage = true) { DepthEffectSetting.set(resolver, it) }
            depth.view.setOnClickListener {
                open { PickerDepthEffectPage(rows).show(context, availability().unavailableReason(Feature.DEPTH_EFFECT), ::refresh) }
            }

            aod = append(AOD_TAG, PickerAlwaysOnDisplayPage.TITLE, "") ?: return
            aod.view.setOnClickListener {
                open { PickerAlwaysOnDisplayPage(rows).show(context, availability().unavailableReason(Feature.AOD_WALLPAPER), ::refresh) }
            }

            val diagnosticsRow = append(DIAGNOSTICS_TAG, DIAGNOSTICS_TITLE, DIAGNOSTICS_DESCRIPTION) ?: return
            diagnosticsRow.view.setOnClickListener { open { diagnostics.show(context) } }
            refresh()
        }

        /** Shows what each entry says and whether it can be used, as the settings and SystemUI stand now. */
        fun refresh() {
            val features = availability()
            features.unavailableReason(Feature.DEPTH_EFFECT).let { reason ->
                rows.describe(depth, reason ?: PickerDepthEffectPage.DESCRIPTION)
                rows.setEnabled(depth, reason == null)
            }
            depthSwitch.isChecked = DepthEffectSetting.isOn(resolver)
            features.unavailableReason(Feature.AOD_WALLPAPER).let { reason ->
                rows.describe(aod, reason ?: PickerAlwaysOnDisplayPage.summary(resolver))
                rows.setEnabled(aod, reason == null)
            }
        }

        private fun availability() = FeatureAvailability.current(SnapshotStore(resolver), HostStartupReport.bootedAt())

        private fun open(page: () -> Unit) {
            runCatching(page).onFailure { logger.warn("A lock screen option page could not be shown", it) }
        }

        /** Adds one of the picker's entries as the list's last; the one that was last now sits in the middle. */
        private fun append(tag: String, title: String, description: String): PickerRows.Row? {
            val row = rows.row(list, title, description, tag) ?: return null
            list.getChildAt(list.childCount - 1)?.let { neighbour ->
                rows.setEntryBackground(
                    neighbour,
                    if (list.childCount == 1) "customization_option_entry_top_background" else "customization_option_entry_background",
                )
            }
            rows.setEntryBackground(row.view, "customization_option_entry_bottom_background")
            list.addView(row.view)
            return row
        }
    }

    /**
     * Takes this module's rows out of [list]'s height while the picker hides the list, and puts them back as
     * it shows it. The Lock screen and Home screen lists share one scroll view, and the picker hides the one
     * not in view as invisible rather than gone, so it still counts toward how far the page scrolls; with the
     * added rows, the Home screen page scrolled on past its last row.
     */
    private fun keepRowsWithList(list: LinearLayout) {
        if (!followedLists.add(list)) return
        BeforeEachDraw.follow(list) {
            val rows = if (list.visibility == View.VISIBLE) View.VISIBLE else View.GONE
            tags.mapNotNull { list.findViewWithTag<View>(it) }
                .filter { it.visibility != rows }
                .forEach { it.visibility = rows }
        }
    }

    /** How much taller this module's rows make the Lock screen list in [page], in pixels; 0 while that list is hidden. */
    fun addedHeight(page: View): Int {
        val list = page.findViewById<LinearLayout>(PickerRows.resource(page.context, "id", LIST))
            ?.takeIf { it.visibility == View.VISIBLE } ?: return 0
        return tags
            .mapNotNull { list.findViewWithTag<View>(it) }
            .filter { it.visibility != View.GONE }
            .sumOf { row ->
                val margins = row.layoutParams as? ViewGroup.MarginLayoutParams
                row.height + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
            }
    }

    private companion object {
        const val FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
        const val LOCK_SCREEN = "LOCK_SCREEN"
        const val LIST = "lock_customization_option_container"
        const val DEPTH_TAG = "pixel_lock_screen_evolved_depth_effect"
        const val AOD_TAG = "pixel_lock_screen_evolved_always_on_display"
        const val DIAGNOSTICS_TAG = "pixel_lock_screen_evolved_diagnostics"
        const val DIAGNOSTICS_TITLE = "Compatibility & diagnostics"
        const val DIAGNOSTICS_DESCRIPTION = "Feature compatibility, hooks and Safe Mode"
    }
}
