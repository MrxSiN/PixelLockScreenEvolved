package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Field
import java.lang.reflect.Method

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Keeps this module's clocks together in Wallpaper & style, where iOS already
 * sits, and moves the picker's own divider to the end of that section.
 *
 * Android 17 partitions clocks with axis presets from clocks without them.
 * That put iOS in the first section and every fixed-width module clock among
 * the Pixel clocks. The emitted option list is reordered after that partition,
 * without inventing unused axes for fixed-width faces. The picker's existing
 * `SingleRowListItemSpacing` continues to own spacing and drawing; only its
 * divider position changes.
 */
internal class PickerClockSection(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }
    private val styleIdSet = styleIds.toSet()

    override fun install(classLoader: ClassLoader) {
        val api = try {
            Api(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock list section not found; the module's clocks remain among the Pixel clocks", error)
            return
        }

        api.buildOptions?.let { method ->
            hooks.replaceResult(method) { _, _, result -> reorder(api, result) }
        } ?: logger.warn("Clock option ordering not found; the module's clocks may remain split between sections")

        hooks.before(api.getItemOffsets) { decoration, args ->
            alignDivider(api, requireNotNull(decoration), args[2] as View)
        }
        hooks.before(api.onDrawOver) { decoration, args ->
            alignDivider(api, requireNotNull(decoration), args[1] as View)
        }
    }

    /** Reorders only the clock-option result; coroutine suspension and other results pass through. */
    private fun reorder(api: Api, result: Any?): Any? {
        val items = result as? List<*> ?: return result
        if (items.any { it == null || !api.isOption(it) }) return result
        @Suppress("UNCHECKED_CAST")
        return PickerClockSectionOrder.group(items as List<Any>, styleIds, api::itemKey)
    }

    /** Gives the picker's divider the index of this module's now-contiguous last clock. */
    private fun alignDivider(api: Api, decoration: Any, list: View) {
        val keys = api.itemKeys(list) ?: return
        val index = PickerClockSectionOrder.dividerIndex(keys, styleIdSet) ?: return
        if (api.dividerIndex.getInt(decoration) != index) api.dividerIndex.setInt(decoration, index)
    }

    /** Picker option construction, RecyclerView contents and spacing reached through its class loader. */
    private class Api(classLoader: ClassLoader) {
        private val spacingType = Class.forName(SPACING, false, classLoader)
        private val recyclerType = Class.forName(RECYCLER, false, classLoader)
        private val optionType = Class.forName(OPTION, false, classLoader)

        val buildOptions: Method? = runCatching {
            Class.forName(OPTION_BUILDER, false, classLoader)
                .getDeclaredMethod("invokeSuspend", Object::class.java)
                .apply { isAccessible = true }
        }.getOrNull()

        val getItemOffsets: Method = spacingType.declaredMethods.first { it.name == "getItemOffsets" && it.parameterTypes.size == 3 }
        val onDrawOver: Method = spacingType.declaredMethods.first { it.name == "onDrawOver" && it.parameterTypes.size == 2 }
        val dividerIndex: Field = spacingType.field("dividerIndex")

        private val adapter: Field = recyclerType.field("mAdapter")
        private val key: Field = optionType.field("key")

        fun isOption(value: Any): Boolean = optionType.isInstance(value)

        fun itemKey(option: Any): String? = stateValue(key.get(option)) as? String

        /** The keys, clock ids here, of the options [list] shows; null when it shows something else. */
        fun itemKeys(list: View): List<String?>? {
            val options = adapter.get(list)?.let { found ->
                generateSequence(found.javaClass as Class<*>?) { it.superclass }
                    .flatMap { it.declaredFields.asSequence() }
                    .firstOrNull { it.name == "items" }
                    ?.apply { isAccessible = true }
                    ?.get(found) as? List<*>
            } ?: return null
            if (options.any { it == null || !optionType.isInstance(it) }) return null
            return options.map { option -> itemKey(requireNotNull(option)) }
        }

        /** The value of a flow the picker carries, read by name since it owns the coroutines class. */
        private fun stateValue(flow: Any?): Any? = flow?.javaClass?.methods
            ?.firstOrNull { it.name == "getValue" && it.parameterTypes.isEmpty() }
            ?.invoke(flow)
    }

    private companion object {
        const val SPACING = "com.android.customization.picker.common.ui.view.SingleRowListItemSpacing"
        const val RECYCLER = "androidx.recyclerview.widget.RecyclerView"
        const val OPTION = "com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2"
        const val OPTION_BUILDER = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel\$clockStyleOptions\$1"
    }
}
