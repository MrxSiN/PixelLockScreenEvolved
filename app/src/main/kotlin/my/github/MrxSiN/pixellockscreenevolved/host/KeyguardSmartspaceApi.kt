package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Method

/**
 * SystemUI's keyguard date and weather row, live and in the picker preview,
 * reached through its class loader.
 *
 * Only what the date row patch reads: where each row is laid out or shown, the
 * rows' views, and the clock view model that says which clock is showing and
 * at which size. HOOK_NOTES.md records the shapes.
 */
internal class KeyguardSmartspaceApi(
    classLoader: ClassLoader,
    private val clocks: ClockPluginApi,
) {

    private val sectionType = load("com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection", classLoader)

    /**
     * Runs after the clock section in every keyguard layout pass, or null on a
     * build whose keyguard no longer constrains its smartspace. Android 17 QPR1
     * left the section without `applyConstraints` or its `dateView`, having
     * moved the live row into the scene of composed elements; the preview below
     * still lays its rows out as before.
     */
    val applyConstraints: Method? = runCatching {
        sectionType.getDeclaredMethod(
            "applyConstraints",
            load("androidx.constraintlayout.widget.ConstraintSet", classLoader),
        )
    }.getOrNull()

    private val previewBinderType = load(PREVIEW_BINDER, classLoader)

    private val composedRowType = runCatching {
        load("$SMARTSPACE_ELEMENTS\$DWARowElement", classLoader)
    }.getOrNull()
    private val composedColumnType = runCatching {
        load("$SMARTSPACE_ELEMENTS\$DWAColumnElement", classLoader)
    }.getOrNull()

    /** Draws live date and weather variants in Android 17's composed lock screen. */
    val composedRows: List<Method> = listOfNotNull(composedRowType, composedColumnType).mapNotNull(::composable)

    /** Draws the live smartspace card, weather, events and the like, in Android 17's composed lock screen; null before it. */
    val composedCards: Method? = runCatching { load("$SMARTSPACE_ELEMENTS\$CardsElement", classLoader) }.getOrNull()?.let(::composable)

    /** Shows or hides the preview's date rows whenever the preview clock size or smartspace changes. */
    val previewVisibility: Method = previewBinderType.declaredMethods
        .firstOrNull { it.name == "emit" && it.parameterCount == 2 }
        ?: throw NoSuchMethodException("$PREVIEW_BINDER.emit")

    private val sectionDateView = runCatching { sectionType.field("dateView") }.getOrNull()
    private val sectionClockViewModel = sectionType.field("keyguardClockViewModel")

    private val previewLargeDateView = previewBinderType.field("\$largeDateView")
    private val previewSmallDateView = previewBinderType.field("\$smallDateView")
    private val previewSmartspaceViewModel = previewBinderType.field("\$viewModel")
    private val previewClockViewModel =
        load("com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewSmartspaceViewModel", classLoader)
            .field("clockViewModel")
    private val previewKeyguardClockViewModel =
        load("com.android.systemui.keyguard.ui.viewmodel.KeyguardPreviewClockViewModel", classLoader)
            .field("keyguardClockViewModel")


    private val clockViewModels = KeyguardClockViewModelApi(classLoader)

    /** The live date and weather row, or null before the section has built it. */
    fun dateRow(section: Any): View? = sectionDateView?.get(section) as View?

    /** The preview's two date rows, for the large and the small clock. */
    fun previewDateRows(binder: Any): List<View> =
        listOfNotNull(previewLargeDateView.get(binder) as View?, previewSmallDateView.get(binder) as View?)

    /** The Android view hosted by a composed smartspace element: a date and weather row, or the card. */
    fun composedView(element: Any): View? {
        val lazy = element.javaClass.field("view\$delegate").get(element)
        return lazy.javaClass.getMethod("getValue").invoke(lazy) as? View
    }

    /** Id of the clock the live section shows now, or null while none is. */
    fun sectionClockId(section: Any): String? = clockId(sectionClockViewModel.get(section))

    fun isLargeClockVisible(section: Any): Boolean =
        sectionClockViewModel.get(section)?.let(clockViewModels::isLargeClockVisible) ?: true

    /** Id of the clock the preview shows, or null while none is. */
    fun previewClockId(binder: Any): String? {
        val smartspace = previewSmartspaceViewModel.get(binder) ?: return null
        val preview = previewClockViewModel.get(smartspace) ?: return null
        return clockId(previewKeyguardClockViewModel.get(preview))
    }

    /** Whether a preview emission, a (clock size, show smartspace) pair, asks for the large clock. */
    fun previewShowsLargeClock(emission: Any): Boolean {
        val size = firstOfPair(emission) as Enum<*>?
        return size?.name != SMALL_CLOCK_SIZE
    }

    /**
     * What a `kotlin.Pair` holds first. The picker of Android 17 QPR1 ships
     * Kotlin shrunk far enough to have dropped the getter, leaving the field it
     * read.
     */
    private fun firstOfPair(pair: Any): Any? {
        val type = pair.javaClass
        val getter = runCatching { type.getMethod("getFirst") }.getOrNull()
        return getter?.invoke(pair) ?: type.field("first").get(pair)
    }

    private fun composable(type: Class<*>): Method? =
        type.declaredMethods
            .firstOrNull { it.name == "LockscreenElement" && it.parameterCount == 3 }
            ?.apply { isAccessible = true }

    private fun clockId(clockViewModel: Any?): String? =
        clockViewModel?.let(clockViewModels::currentClock)?.let(clocks::controllerClockId)

    private companion object {
        const val PREVIEW_BINDER =
            "com.android.systemui.keyguard.ui.binder.KeyguardPreviewSmartspaceViewBinder\$bind\$1\$1\$1\$4"
        const val SMALL_CLOCK_SIZE = "SMALL"
        const val SMARTSPACE_ELEMENTS = "com.android.systemui.keyguard.ui.composable.elements.SmartspaceElementProvider"

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)
    }
}
