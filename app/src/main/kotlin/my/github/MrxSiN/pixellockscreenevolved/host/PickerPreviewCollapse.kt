package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Keeps Wallpaper & style's previews collapsing only as far as they do on a
 * stock picker, whatever this module adds to the option list.
 *
 * As the option list scrolls, the page shrinks its preview header to the height
 * `CustomizationPickerFragment.updateHeaderHeightConstraints` works out: the
 * page's height less the option list's, so the whole list just fits, and no
 * less than a floor. The rows this module adds made the list so much taller
 * that the header shrank to that floor, small thumbnails with their labels cut
 * to an ellipsis. The list's height is handed over without them
 * ([addedHeight]), so the header collapses to the height it has on a stock
 * picker, and the added rows scroll on under it.
 */
internal class PickerPreviewCollapse(
    private val hooks: Hooks,
    private val logger: Logger,
    /** How much taller the module makes the option list on a page, in pixels. */
    private val addedHeight: (page: View) -> Int,
) : HostPatch {

    override fun install(classLoader: ClassLoader) {
        val update = try {
            Class.forName(FRAGMENT, false, classLoader).declaredMethods
                .firstOrNull { it.name == UPDATE_HEADER }
                ?: throw NoSuchMethodException("$FRAGMENT.$UPDATE_HEADER")
        } catch (error: ReflectiveOperationException) {
            logger.warn("Wallpaper & style header sizing not found; its previews collapse further for the added options", error)
            return
        }

        // (MotionLayout page, WallpaperPickerEntry, label height, option list height, PackThemeSuggestedChip, bottom inset)
        hooks.replaceArguments(update) { _, args ->
            val page = args[PAGE] as View
            val listHeight = args[LIST_HEIGHT] as Int
            args.toMutableList().apply { set(LIST_HEIGHT, listHeight - addedHeight(page)) }
        }
    }

    private companion object {
        const val FRAGMENT = "com.android.wallpaper.picker.customization.ui.CustomizationPickerFragment"
        const val UPDATE_HEADER = "updateHeaderHeightConstraints"
        const val PAGE = 0
        const val LIST_HEIGHT = 3
    }
}
