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
 *
 * Each option's click remembers the position it was built at, and the picker
 * centres the list on that position when the option is chosen. Built before the
 * reorder, the position named another clock: choosing Hyper scrolled the list
 * two clocks past it. A click is given its clock's position in the shown list
 * as it runs.
 */
internal class PickerClockSection(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }
    private val styleIdSet = styleIds.toSet()

    /** The clock ids in the order the picker last showed them. */
    @Volatile private var shownKeys: List<String?> = emptyList()

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

        api.clicked?.let { method ->
            hooks.before(method) { click, _ -> api.reposition(requireNotNull(click), shownKeys) }
        } ?: logger.warn("Clock option clicks not found; choosing a clock may centre the list on another")

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
            .also { shown -> shownKeys = shown.map(api::itemKey) }
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
            classLoader.hostClass(OPTION_BUILDER)
                .getDeclaredMethod("invokeSuspend", Object::class.java)
                .apply { isAccessible = true }
        }.getOrNull()

        /** An option's click, R8-merged with the colour options' (`ClockPickerViewModel$toOption$2$1`). */
        private val clickType: Class<*>? = runCatching { classLoader.hostClass(OPTION_CLICK) }.getOrNull()
        val clicked: Method? = clickType?.let { type -> runCatching { type.getDeclaredMethod("invoke").apply { isAccessible = true } }.getOrNull() }
        private val clickIndex: Field? = clickType?.let { type -> runCatching { type.field("\$index") }.getOrNull() }
        private val clickModel: Field? = clickType?.let { type -> runCatching { type.field("\$this_toOption") }.getOrNull() }

        /** Sets a clock option's [click] to the position of its clock in [keys]; a colour option's is left alone. */
        fun reposition(click: Any, keys: List<String?>) {
            val index = clickIndex ?: return
            val model = clickModel?.get(click) ?: return
            val id = runCatching { model.javaClass.field("clockId").get(model) as? String }.getOrNull() ?: return
            val shown = keys.indexOf(id)
            if (shown >= 0 && index.getInt(click) != shown) index.setInt(click, shown)
        }

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
        const val OPTION_CLICK = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel\$toOption\$2\$1"
        const val OPTION_BUILDER = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel\$clockStyleOptions\$1"
    }
}
