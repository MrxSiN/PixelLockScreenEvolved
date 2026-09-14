package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import java.lang.reflect.Field
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

    /** Runs after the clock section in every keyguard layout pass. */
    val applyConstraints: Method = sectionType.getDeclaredMethod(
        "applyConstraints",
        load("androidx.constraintlayout.widget.ConstraintSet", classLoader),
    )

    private val previewBinderType = load(PREVIEW_BINDER, classLoader)

    /** Shows or hides the preview's date rows whenever the preview clock size or smartspace changes. */
    val previewVisibility: Method = previewBinderType.declaredMethods
        .firstOrNull { it.name == "emit" && it.parameterCount == 2 }
        ?: throw NoSuchMethodException("$PREVIEW_BINDER.emit")

    private val sectionDateView = sectionType.field("dateView")
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
    fun dateRow(section: Any): View? = sectionDateView.get(section) as View?

    /** The preview's two date rows, for the large and the small clock. */
    fun previewDateRows(binder: Any): List<View> =
        listOfNotNull(previewLargeDateView.get(binder) as View?, previewSmallDateView.get(binder) as View?)

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
        val size = emission.javaClass.getMethod("getFirst").invoke(emission) as Enum<*>?
        return size?.name != SMALL_CLOCK_SIZE
    }

    private fun clockId(clockViewModel: Any?): String? =
        clockViewModel?.let(clockViewModels::currentClock)?.let(clocks::controllerClockId)

    private companion object {
        const val PREVIEW_BINDER =
            "com.android.systemui.keyguard.ui.binder.KeyguardPreviewSmartspaceViewBinder\$bind\$1\$1\$1\$4"
        const val SMALL_CLOCK_SIZE = "SMALL"

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
