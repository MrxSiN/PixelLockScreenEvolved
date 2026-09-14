package my.github.MrxSiN.pixellockscreenevolved.host

import android.transition.Transition
import android.view.ViewGroup

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * How SystemUI changes the lock screen clock between its small and large size,
 * reached through its class loader. HOOK_NOTES.md records the shapes.
 *
 * The change is a keyguard blueprint transition holding a fade out of one clock
 * face, a fade in of the other after it, and a move of the smartspace. A moment
 * before it, the smartspace binders hide the date row beside or below the small
 * clock on their own.
 */
internal class KeyguardClockSizeApi(classLoader: ClassLoader) {

    private val faceTransitionType = load("$TRANSITIONS.ClockSizeTransition\$ClockFaceTransition", classLoader)
    private val faceInTransitionType = load("$TRANSITIONS.ClockSizeTransition\$ClockFaceInTransition", classLoader)
    private val faceTransitionViewModel = faceTransitionType.field("viewModel")

    /** Adds a clock face transition's targets, as it is built for a change of clock size. */
    val faceTransitionTargets: Method = faceTransitionType.getDeclaredMethod("addTargets")

    /**
     * The smartspace binder collectors that hide the date row the moment the
     * large clock is chosen, ahead of the transition; each holds the keyguard
     * root view.
     */
    val dateRowHidden: List<Method> = listOf(DATE_BINDER, DATE_BURN_IN_BINDER).map { name ->
        load(name, classLoader).declaredMethods.firstOrNull { it.name == "emit" && it.parameterCount == 2 }
            ?: throw NoSuchMethodException("$name.emit")
    }

    private val binderRoots: Map<Class<*>, Field> = dateRowHidden.associate { it.declaringClass to it.declaringClass.field("\$keyguardRootView") }

    /** Whether a clock face transition brings a face in, rather than taking one out. */
    fun bringsFaceIn(transition: Transition): Boolean = faceInTransitionType.isInstance(transition)

    /** The clock view model a clock face transition reads. */
    fun clockViewModel(transition: Transition): Any? = faceTransitionViewModel.get(transition)

    /** The keyguard root view one of the [dateRowHidden] collectors lays out. */
    fun keyguardRoot(binder: Any): ViewGroup? = binderRoots[binder.javaClass]?.get(binder) as ViewGroup?

    private companion object {
        const val TRANSITIONS = "com.android.systemui.keyguard.ui.view.layout.sections.transitions"
        const val DATE_BINDER = "com.android.systemui.keyguard.ui.binder.KeyguardSmartspaceViewBinder\$bind\$1\$1\$2\$1"
        const val DATE_BURN_IN_BINDER = "com.android.systemui.keyguard.ui.binder.KeyguardSmartspaceViewBinder\$bind\$1\$1\$4\$4"

        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
