package my.github.MrxSiN.pixellockscreenevolved.host

import android.transition.Transition
import android.view.View
import android.view.ViewGroup

import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Makes the lock screen's change between this module's small and large clock,
 * as the last notification goes or the first arrives, one smooth motion.
 *
 * SystemUI fades one face out and only then the other in, so the time blinked
 * out in one place and back in at another; [ClockSizeMorph] carries it across
 * instead, and SystemUI's own fades are kept off this module's faces.
 *
 * SystemUI's smartspace binders also hide the date row below the small clock a
 * frame before the change starts, so the row vanished and the card beneath it
 * jumped up before gliding down to the large clock. While the small clock is
 * still shown the row is kept, and SystemUI's transition fades it out and moves
 * the card from where it was.
 */
internal class KeyguardClockSizeMotion(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val clocks = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()))
    private val morph = ClockSizeMorph()

    /** Remembers a clock SystemUI built, so its faces can be told apart from Google's. */
    fun track(clock: ClockControllerAdapter) {
        clocks.add(clock)
    }

    override fun install(classLoader: ClassLoader) {
        val (sizes, viewModels) = try {
            KeyguardClockSizeApi(classLoader) to KeyguardClockViewModelApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Clock size transition not found; the clock changes size as SystemUI animates it", error)
            return
        }

        sizes.dateRowHidden.forEach { method ->
            hooks.after(method) { binder, _ ->
                val root = sizes.keyguardRoot(requireNotNull(binder)) ?: return@after
                if (showsOurSmallClock(root)) keepDateRow(root)
            }
        }

        hooks.after(sizes.faceTransitionTargets) { transition, _ ->
            val faces = transition as Transition
            val viewModel = sizes.clockViewModel(faces) ?: return@after
            val controller = viewModels.currentClock(viewModel) ?: return@after
            val clock = synchronized(clocks) { clocks.firstOrNull { it.controller === controller } } ?: return@after
            listOf(clock.largeFace, clock.smallFace).forEach { faces.excludeTarget(it.view, true) }
            if (!sizes.bringsFaceIn(faces)) return@after

            val toLarge = viewModels.isLargeClockVisible(viewModel)
            val (from, to) = if (toLarge) clock.smallFace to clock.largeFace else clock.largeFace to clock.smallFace
            if (from.view.isShown && !to.view.isShown) morph.start(from, to)
        }
    }

    /** Whether one of this module's small clocks is on show in [root]. */
    private fun showsOurSmallClock(root: ViewGroup): Boolean =
        synchronized(clocks) { clocks.toList() }.any { it.smallFace.view.parent === root && it.smallFace.view.isShown }

    private fun keepDateRow(root: ViewGroup) {
        val id = root.resources.getIdentifier(DATE_ROW_ID, "id", root.context.packageName)
        val row = root.findViewById<View>(id) ?: return
        if (row.visibility == View.GONE) row.visibility = View.VISIBLE
    }

    private companion object {
        /** The date and weather row SystemUI shows below this module's small clocks. */
        const val DATE_ROW_ID = "date_smartspace_view"
    }
}
