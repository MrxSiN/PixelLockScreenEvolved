package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks

/**
 * Lets SystemUI's smartspace card measure only as tall as what it shows, down
 * to the bottom of its last text or icon, beside a face of this module's that
 * writes its own date ([ModuleClockFaces.datedIn]), so what stacks under it,
 * the notifications, stands right under its text, as on iOS.
 *
 * The card measures itself at one fixed height whatever it is offered
 * (`enhanced_smartspace_height`, 104 dp) and centres its lines in it, so two
 * lines leave about a third of it empty at the bottom. After its own measure
 * the height is cut to its content. The card centres its pager, which would
 * then rise by half the cut, so the pager is pinned to the card's top: the
 * lines stay where they were, and the pager overhangs the card only where
 * nothing is drawn. A card showing anything beside its pager (the page dots
 * of several pages) keeps its height. Elsewhere, beside SystemUI's own
 * clocks, it is left as SystemUI measures it.
 */
internal class SmartspaceCardCrop(private val hooks: Hooks, private val logger: Logger) {

    private val cards = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val hooked = HashSet<Method>()

    /** Whether each card was last measured beside a face that writes its own date, and so cut. */
    private val cutBeside = WeakHashMap<View, Boolean>()
    private val bounds = Rect()

    /** Starts cutting [card] to its content beside a dated face, hooking its class's measure once. */
    fun follow(card: View) {
        if (!cards.add(card)) return
        if (card is ViewGroup) {
            card.clipChildren = false
            pinToTop(card)
        }
        val onMeasure = onMeasureOf(card.javaClass) ?: return
        if (!hooked.add(onMeasure)) return
        try {
            hooks.after(onMeasure) { view, _ -> if (view is ViewGroup && view in cards) cut(view) }
        } catch (error: RuntimeException) {
            logger.warn("The smartspace card's measure could not be hooked; it keeps its full height", error)
        }
    }

    /**
     * Asks [card] to measure again, before a frame, once the face beside it starts or stops writing its own date.
     * What the card shows changing measures it anew of itself, so only that is checked here, every frame, rather
     * than walking its lines.
     */
    fun check(card: View) {
        if (card.isLayoutRequested) return
        val beside = cutBeside[card] ?: return
        if (beside != (ModuleClockFaces.datedIn(card.rootView) != null)) card.requestLayout()
    }

    private fun cut(card: ViewGroup) {
        val beside = ModuleClockFaces.datedIn(card.rootView) != null
        cutBeside[card] = beside
        if (!beside) return
        // Laid out wider than the window, the scene is morphing it between the clock's sizes: hidden from this
        // frame, before it is drawn, until the placement fades it back in ([ComposedSmartspacePlacement]).
        if (card.measuredWidth > card.rootView.width) card.transitionAlpha = 0f
        if (showsBesidePager(card)) return
        val full = card.measuredHeight
        val bottom = contentBottom(card)
        if (bottom in 1 until full) SET_MEASURED_DIMENSION.invoke(card, card.measuredWidth, bottom)
    }

    /** The bottom of the lowest text or icon shown across the card, in its pixels; 0 before it is laid out. */
    private fun contentBottom(group: ViewGroup, card: ViewGroup = group): Int {
        var bottom = 0
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE || child.width == 0 || child.height == 0) continue
            if (child is ViewGroup) {
                bottom = maxOf(bottom, contentBottom(child, card))
            } else if (child is TextView || child is ImageView) {
                bounds.set(0, 0, child.width, child.height)
                card.offsetDescendantRectToMyCoords(child, bounds)
                // A page the pager holds beside the one shown lies outside the card's width.
                if (bounds.right > 0 && bounds.left < card.width) bottom = maxOf(bottom, bounds.bottom)
            }
        }
        return bottom
    }

    /** Pins each vertically centred child of [card] to its top, keeping its horizontal gravity. */
    private fun pinToTop(card: ViewGroup) {
        for (i in 0 until card.childCount) {
            val params = card.getChildAt(i).layoutParams as? FrameLayout.LayoutParams ?: continue
            if (params.gravity == FrameLayout.LayoutParams.UNSPECIFIED_GRAVITY) continue
            if (params.gravity and Gravity.VERTICAL_GRAVITY_MASK != Gravity.CENTER_VERTICAL) continue
            params.gravity = (params.gravity and Gravity.HORIZONTAL_GRAVITY_MASK) or Gravity.TOP
            card.getChildAt(i).layoutParams = params
        }
    }

    /** Whether [card] shows more than one child, its pager and something else, as the dots of several pages. */
    private fun showsBesidePager(card: ViewGroup): Boolean {
        var shown = 0
        for (i in 0 until card.childCount) if (card.getChildAt(i).visibility == View.VISIBLE) shown++
        return shown > 1
    }

    private fun onMeasureOf(type: Class<*>): Method? {
        var at: Class<*>? = type
        while (at != null && at != View::class.java) {
            runCatching { at.getDeclaredMethod(ON_MEASURE, Int::class.java, Int::class.java) }.getOrNull()?.let { return it }
            at = at.superclass
        }
        return null
    }

    private companion object {
        const val ON_MEASURE = "onMeasure"

        val SET_MEASURED_DIMENSION: Method = View::class.java
            .getDeclaredMethod("setMeasuredDimension", Int::class.java, Int::class.java)
            .apply { isAccessible = true }
    }
}
