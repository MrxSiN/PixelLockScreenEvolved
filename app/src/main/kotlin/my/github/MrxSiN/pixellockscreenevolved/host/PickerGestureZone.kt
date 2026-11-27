package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout

/**
 * Where the preview's clock is moved and turned by hand while the clock
 * sheet's Position tab is open: the whole band of the screen the lock screen
 * preview stands in, its empty sides included, not just the clock.
 *
 * The preview is drawn at about half the screen's size, and a turned clock
 * takes touches only inside its turned outline, so two fingers had to land on
 * a small, slanting clock to twist it, which they rarely did. The zone is a
 * plain transparent view over that band, added to the activity's content as
 * the drag hint is ([PickerDragHint]); it hands every touch to the drag of the
 * clock shown in the preview ([PickerClockDrag.shownIn]): one finger anywhere
 * moves the clock, two anywhere turn it, a double tap anywhere stands it
 * upright. Both fingers land in the one view, so the pointers of a twist are
 * never split between views. Outside the Position tab it is gone, and the
 * preview takes its touches as the picker made it.
 */
internal class PickerGestureZone(
    private val sheet: View,
    private val state: PickerClockState,
) {

    private val context = sheet.context
    private var root: FrameLayout? = null
    private var card: View? = null
    private val cardBounds = RectF()
    private var placedTop = Float.NaN
    private var placedHeight = -1

    @SuppressLint("ClickableViewAccessibility")
    private val zone = View(context).apply {
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setOnTouchListener { view, event ->
            val drag = root?.let { PickerClockDrag.shownIn(it.rootView) } ?: return@setOnTouchListener false
            val taken = drag.onTouch(event)
            if (taken) view.parent?.requestDisallowInterceptTouchEvent(true)
            taken
        }
    }

    private val placing = ViewTreeObserver.OnPreDrawListener { place(); true }

    init {
        sheet.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = attach()

            override fun onViewDetachedFromWindow(view: View) = detach()
        })
        if (sheet.isAttachedToWindow) attach()
    }

    private fun attach() {
        if (root != null) return
        val root = sheet.rootView.findViewById<View>(android.R.id.content) as? FrameLayout ?: return
        card = sheet.rootView.findViewById(PickerRows.resource(context, "id", PREVIEW_CARD)) ?: return
        this.root = root
        (zone.parent as? ViewGroup)?.removeView(zone)
        root.addView(zone, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        root.viewTreeObserver.addOnPreDrawListener(placing)
    }

    private fun detach() {
        val root = root ?: return
        root.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(placing)
        root.removeView(zone)
        this.root = null
        card = null
        placedTop = Float.NaN
        placedHeight = -1
    }

    /** Lays the zone over the preview's band, full width, while the Position tab is open; otherwise takes it away. */
    private fun place() {
        val root = root ?: return
        val card = card ?: return
        val open = state.isEditingPosition && sheet.isShown && card.isShown
        val wanted = if (open) View.VISIBLE else View.GONE
        if (zone.visibility != wanted) zone.visibility = wanted
        if (!open) return
        ViewBounds.inAncestor(root, card, cardBounds)
        val height = cardBounds.height().toInt()
        if (cardBounds.top == placedTop && height == placedHeight) return
        placedTop = cardBounds.top
        placedHeight = height
        zone.translationY = cardBounds.top
        if (zone.layoutParams.height != height) zone.layoutParams = zone.layoutParams.apply { this.height = height }
    }

    private companion object {
        const val PREVIEW_CARD = "lock_preview_card"
    }
}
