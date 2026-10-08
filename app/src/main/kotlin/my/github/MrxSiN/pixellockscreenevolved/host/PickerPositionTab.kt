package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks

/**
 * A Position tab in Wallpaper & style's clock sheet, last, after Size, for
 * this module's clocks that can be moved ([ClockStyle.isMovable]).
 *
 * The sheet's tabs are a fixed enum (Style, Color, Size), each owning one
 * content view and one recorded height. The Position tab rides on the Size
 * tab's slot: choosing it selects Size in the picker, so the sheet animates to
 * that content as it would, and the content shows the position controls
 * instead of the size controls ([PickerSizeTab], [PickerPositionRows]). The
 * tab list the sheet's adapter is handed is rewritten on its way in
 * (`FloatingToolbarTabAdapter.submitList`): a Position tab is added, built as
 * the picker builds its own, and while it is open it is the selected one and
 * Size is not. The tab is one object for as long as the sheet lives, so the
 * adapter's diff never sees it change but for its selection. Choosing Style, Color or Size runs the picker's own tab click
 * (`ClockPickerViewModel$tabs$1$1.invoke`), which closes Position; Size then
 * shows the size controls again.
 *
 * While the tab is open, the preview's clock can be dragged ([PickerClockDrag]).
 *
 * The sheet's tab animator (`TabItemAnimator`) animates a tab being selected,
 * and nothing else: a tab added or removed through it, as this one is when
 * the clock previewed turns movable or not, left the removed tab's view
 * behind, drawn over its neighbours and taking their taps (the Size tab that
 * could not be tapped). Once a list with another number of tabs has been laid
 * out, the animator's work is ended, so the tab comes or goes at once.
 */
internal class PickerPositionTab(
    classLoader: ClassLoader,
    private val state: PickerClockState,
    private val logger: Logger,
) {

    private val api = Api(classLoader)

    private var adapter = WeakReference<Any>(null)
    private var viewModel = WeakReference<Any>(null)
    private var context = WeakReference<Context>(null)
    private var sheet = WeakReference<View>(null)
    private var tabList = WeakReference<View>(null)
    private var original: List<*>? = null

    /** Every click the picker's own tabs carried, compared by identity, so a view bound to an older list is still known. */
    private val pickerClicks: MutableSet<Any> = Collections.newSetFromMap(WeakHashMap())
    private var shownCount = -1
    private var tabs: Pair<Any, Any>? = null

    /** Whether the clock previewed can take a position; the tab is listed only then. */
    var isAvailable = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) state.isEditingPosition = false
            resubmit()
        }

    fun install(hooks: Hooks) {
        hooks.replaceArguments(api.submitList) { target, args ->
            if (target == null || target !== adapter.get()) return@replaceArguments args
            val tabs = args.firstOrNull() as? List<*> ?: return@replaceArguments args
            original = tabs
            tabs.forEach { tab -> tab?.let { api.onClick.get(it) }?.let(pickerClicks::add) }
            val rewritten = rewrite(tabs)
            if (shownCount >= 0 && rewritten.size != shownCount) settleWithoutMotion(rewritten.size)
            shownCount = rewritten.size
            listOf(rewritten)
        }
        // The sheet's adapter compares tabs by what they show, not by their clicks, so the Size tab's view
        // keeps the click of whichever picker list bound it, from any earlier list, while Position is open.
        // That click selects Size, which is selected already, so nothing would change: it closes Position here.
        // Choosing Style or Color changes the selection, and the list that follows closes Position ([rewrite]).
        // R8 merged the click with other lambdas, so only a click a picker tab held counts.
        api.tabClicked?.let { clicked ->
            hooks.after(clicked) { lambda, _ ->
                if (state.isEditingPosition && lambda in pickerClicks && selectedTabName() == SIZE) close()
            }
        }
    }

    /** Follows the clock sheet [binding] binds into [sheet]: its tab adapter, the list showing it, and its clock picker. */
    fun bind(binding: Any, sheet: View) {
        val adapter = api.tabAdapter.get(binding)
        if (adapter !== this.adapter.get()) {
            shownCount = -1
            tabs = null
        }
        this.adapter = WeakReference(adapter)
        viewModel = WeakReference(api.clockViewModel.get(binding))
        context = WeakReference(sheet.context)
        this.sheet = WeakReference(sheet)
        tabList.clear()
    }

    /** The tab list showing the adapter, looked for once the sheet is in its window. */
    private fun tabList(motion: Motion): View? = tabList.get() ?: sheet.get()?.takeIf { it.isAttachedToWindow }?.let { sheet ->
        findList(motion, sheet.rootView, PickerRows.resource(sheet.context, "id", TAB_LIST), adapter.get())?.also { tabList = WeakReference(it) }
    }

    /** The tab list among [view] and its descendants that shows [adapter]. */
    private fun findList(motion: Motion, view: View, listId: Int, adapter: Any?): View? {
        if (view.id == listId && motion.listAdapter.get(view) === adapter) return view
        if (view !is ViewGroup) return null
        for (i in 0 until view.childCount) findList(motion, view.getChildAt(i), listId, adapter)?.let { return it }
        return null
    }

    /**
     * Ends the tab animator's work before the first draw of the list once it
     * has laid out [count] tabs, so a tab added or removed comes or goes at
     * once instead of through an animator that only knows how to animate a
     * selection ([PickerPositionTab]). A newer change of count supersedes it.
     */
    private fun settleWithoutMotion(count: Int) {
        val motion = api.motion ?: return
        val list = tabList(motion) ?: return
        val adapter = adapter.get() ?: return
        list.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val superseded = shownCount != count
                if (!superseded && (list.isLayoutRequested || !runCatching { motion.isLaidOut(list, adapter, count) }.getOrDefault(true))) return true
                list.viewTreeObserver.removeOnPreDrawListener(this)
                if (!superseded) runCatching { motion.endAnimations(list) }.onFailure { logger.warn("The clock sheet's tab motion could not be ended", it) }
                return true
            }
        })
    }

    private fun rewrite(tabs: List<*>): List<*> {
        if (!isAvailable) return tabs
        if (selectedTabName() != SIZE) state.isEditingPosition = false
        val editing = state.isEditingPosition
        val rewritten = ArrayList<Any?>(tabs.size + 1)
        tabs.forEach { tab ->
            rewritten += if (editing && tab != null && api.isSelected.getBoolean(tab)) api.tab(api.icon.get(tab), api.text.get(tab) as String, false, sizeClick) else tab
        }
        positionTab(editing)?.let { rewritten += it }
        return rewritten
    }

    /** The Position tab, closed or open: built once per sheet, so every list hands the adapter the same two. */
    private fun positionTab(editing: Boolean): Any? {
        val built = tabs ?: run {
            val context = context.get() ?: return null
            val icon = api.loadedIcon(PositionIconDrawable(PickerRows.dp(context, ICON_DP)), TITLE) ?: return null
            (api.tab(icon, TITLE, false, function(::open)) to api.tab(icon, TITLE, true, null)).also { tabs = it }
        }
        return if (editing) built.second else built.first
    }

    /**
     * The Size tab's click while Position is open. The adapter's diff does not
     * compare clicks, so the Size tab's view can keep it after Position has
     * closed (Style tapped, say); it then selects Size, as the picker's own
     * click would. Before, it closed a Position that was no longer open, and
     * Size could not be tapped.
     */
    private val sizeClick: Any by lazy { function { if (state.isEditingPosition) close() else selectSize() } }

    /** Opens the Position tab: the picker selects Size, whose content shows the position controls. */
    private fun open() {
        state.isEditingPosition = true
        if (selectedTabName() == SIZE) resubmit() else selectSize()
    }

    /** Selects the picker's Size tab, as its own click does. */
    private fun selectSize() {
        val selected = viewModel.get()?.let { api.selectedTab.get(it) } ?: return
        val size = api.tabEnum.enumConstants.first { (it as Enum<*>).name == SIZE }
        runCatching { selected.javaClass.getMethod("setValue", Any::class.java).invoke(selected, size) }
            .onFailure { logger.warn("The clock sheet's Size tab could not be selected", it) }
    }

    /** Goes from Position back to the size controls, the picker's Size tab still selected. */
    private fun close() {
        state.isEditingPosition = false
        resubmit()
    }

    /** Hands the adapter the picker's last tab list again, rewritten as things stand now. */
    private fun resubmit() {
        val adapter = adapter.get() ?: return
        val tabs = original ?: return
        runCatching { api.submitList.invoke(adapter, tabs) }.onFailure { logger.warn("The clock sheet's tabs could not be shown again", it) }
    }

    private fun selectedTabName(): String? {
        val flow = viewModel.get()?.let { api.selectedTab.get(it) } ?: return null
        return (flow.javaClass.getMethod("getValue").invoke(flow) as? Enum<*>)?.name
    }

    /** A Kotlin `() -> Unit` of the picker's own that runs [body]. */
    private fun function(body: () -> Unit): Any =
        Proxy.newProxyInstance(api.function0.classLoader, arrayOf(api.function0)) { proxy, method, args ->
            when (method.name) {
                "invoke" -> body().let { kotlinUnit() }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                else -> null
            }
        }

    private fun kotlinUnit(): Any? = runCatching { api.unit.get(null) }.getOrNull()

    /** The picker's tab, icon and clock sheet members, reached through its class loader. HOOK_NOTES.md records them. */
    private class Api(classLoader: ClassLoader) {
        private fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        private val binding = load(SHEET_BINDING, classLoader)
        val tabAdapter: Field = binding.field("\$tabAdapter")
        val clockViewModel: Field = binding.field("\$viewModel")

        val submitList: Method = load(TAB_ADAPTER, classLoader).getDeclaredMethod("submitList", List::class.java)

        /** The picker's own tab click, one lambda class for Style, Color and Size; null where R8 moved it. */
        val tabClicked: Method? = runCatching { load("$PICKER_VIEW_MODEL\$tabs\$1\$1", classLoader).getDeclaredMethod("invoke") }.getOrNull()

        private val tabType = load(TAB_VIEW_MODEL, classLoader)
        private val iconType = load("$PICKER_COMMON.icon.ui.viewmodel.Icon", classLoader)
        private val tabConstructor: Constructor<*> =
            tabType.getConstructor(iconType, String::class.java, Boolean::class.javaPrimitiveType, load(FUNCTION0, classLoader))
        val icon: Field = tabType.field("icon")
        val text: Field = tabType.field("text")
        val isSelected: Field = tabType.field("isSelected")
        val onClick: Field = tabType.field("onClick")

        private val loadedTextType = load("$PICKER_COMMON.text.ui.viewmodel.Text\$Loaded", classLoader)
        private val loadedText = loadedTextType.getConstructor(String::class.java)
        private val loadedIcon = load("$PICKER_COMMON.icon.ui.viewmodel.Icon\$Loaded", classLoader)
            .getConstructor(Drawable::class.java, loadedTextType)

        val selectedTab: Field = load(PICKER_VIEW_MODEL, classLoader).field("_selectedTab")
        val tabEnum: Class<*> = load("$PICKER_VIEW_MODEL\$Tab", classLoader)
        val function0: Class<*> = load(FUNCTION0, classLoader)
        val unit: Field = load("kotlin.Unit", classLoader).getField("INSTANCE")

        fun tab(icon: Any?, text: String, selected: Boolean, onClick: Any?): Any = tabConstructor.newInstance(icon, text, selected, onClick)

        fun loadedIcon(drawable: Drawable, description: String): Any? = loadedIcon.newInstance(drawable, loadedText.newInstance(description))

        /** The tab list's own members, or null where R8 took them; tabs then come and go through its animator. */
        val motion: Motion? = runCatching { Motion(classLoader) }.getOrNull()
    }

    /** What the tab `RecyclerView` exposes of its adapter, its pending updates and its item animator. HOOK_NOTES.md records them. */
    private class Motion(classLoader: ClassLoader) {
        private val list = Class.forName(RECYCLER_VIEW, false, classLoader)
        val listAdapter: Field = list.field("mAdapter")
        private val animator: Field = list.field("mItemAnimator")
        private val hasPendingUpdates: Method = list.getDeclaredMethod("hasPendingAdapterUpdates")
        private val end: Method = Class.forName(ITEM_ANIMATOR, false, classLoader).getDeclaredMethod("endAnimations")

        /** Whether [list] has laid out [adapter] at its [count] items, with no update of it still to process. */
        fun isLaidOut(list: View, adapter: Any, count: Int): Boolean =
            adapter.javaClass.getMethod("getItemCount").invoke(adapter) == count && hasPendingUpdates.invoke(list) == false

        /** Ends whatever [list]'s item animator runs or is about to: a removed view goes, an added one stays. */
        fun endAnimations(list: View) {
            animator.get(list)?.let(end::invoke)
        }
    }

    private companion object {
        const val TITLE = "Position"
        const val ICON_DP = 24f
        const val SIZE = "SIZE"
        const val TAB_LIST = "tab_list"
        const val RECYCLER_VIEW = "androidx.recyclerview.widget.RecyclerView"
        const val ITEM_ANIMATOR = "androidx.recyclerview.widget.DefaultItemAnimator"
        const val SHEET_BINDING = "com.android.customization.picker.clock.ui.binder.ClockFloatingSheetBinder\$bind\$13\$1"
        const val TAB_ADAPTER = "com.android.wallpaper.picker.customization.ui.view.adapter.FloatingToolbarTabAdapter"
        const val TAB_VIEW_MODEL = "com.android.wallpaper.picker.customization.ui.viewmodel.FloatingToolbarTabViewModel"
        const val PICKER_COMMON = "com.android.wallpaper.picker.common"
        const val PICKER_VIEW_MODEL = "com.android.customization.picker.clock.ui.viewmodel.ClockPickerViewModel"
        const val FUNCTION0 = "kotlin.jvm.functions.Function0"
    }
}
