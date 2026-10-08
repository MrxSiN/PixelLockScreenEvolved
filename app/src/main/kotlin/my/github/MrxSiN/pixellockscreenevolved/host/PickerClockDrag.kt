package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

import kotlin.math.abs

/**
 * Drags the large clock itself in Wallpaper & style's preview while the clock
 * sheet's Position tab is open ([PickerClockState.isEditingPosition]): the
 * real face follows the finger one to one ([ClockLayout.dragTo]), within the
 * band the lock screen allows, stretching a little and no further past its
 * ends. Near an anchor ([ClockPosition.ANCHORS]: the top, the quarters, the
 * bottom) it snaps to it, with the system's segment tick and a guide across
 * the preview where the clock's middle lands. Let go, it settles on the
 * spatial spring where it was left, and the position is chosen
 * ([PickerClockState.choosePosition]) until Apply or Back.
 *
 * Nothing can be dragged while the position is adaptive. TalkBack and switch
 * access move the clock from anchor to anchor with Move up and Move down.
 */
internal class PickerClockDrag(
    private val face: ClockFaceAdapter,
    private val state: PickerClockState,
) {

    private val view = face.view
    private val layout = face.clockLayout
    private val guide = Guide()

    private var startY = 0f
    private var startTop = 0f
    private var scale = 1f
    private var snapped = -1
    private var dragging = false

    // Scratch for the preview's drawn size, read as a drag starts.
    private val parentBounds = RectF()

    private val canDrag: Boolean
        get() = state.isEditingPosition && layout.isMovable && !state.shownPosition().adaptive && layout.faceHeight > 0f

    @SuppressLint("ClickableViewAccessibility")
    fun install() {
        view.setOnTouchListener { _, event -> onTouch(event) }
        view.accessibilityDelegate = Accessibility()
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!canDrag) return false
                val parent = view.parent as? View ?: return false
                parent.parent?.requestDisallowInterceptTouchEvent(true)
                ViewBounds.inWindow(parent, parentBounds)
                scale = if (parent.height > 0) (parentBounds.height() / parent.height).takeIf { it > 0f } ?: 1f else 1f
                startY = event.rawY
                startTop = view.top + view.translationY
                snapped = -1
                dragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging) layout.dragTo(follow(startTop + (event.rawY - startY) / scale))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                dragging = false
                val share = if (snapped >= 0) ClockPosition.ANCHORS[snapped] else shareOf(view.top + view.translationY).coerceIn(0f, 1f)
                guide.hide()
                state.choosePosition(ClockPosition(share))
                layout.endDrag()
                view.announceForAccessibility(ClockPosition.nameOf(share))
            }
        }
        return dragging
    }

    /** Where the face's top goes for a finger asking for [wanted]: snapped to an anchor near it, held back past the ends. */
    private fun follow(wanted: Float): Float {
        val top = topOf(0f)
        val bottom = topOf(1f)
        val snapDistance = SNAP_DP * view.resources.displayMetrics.density
        var nearest = -1
        for (i in ClockPosition.ANCHORS.indices) {
            if (abs(topOf(ClockPosition.ANCHORS[i]) - wanted) < snapDistance) nearest = i
        }
        if (nearest != snapped) {
            snapped = nearest
            if (nearest >= 0) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        if (nearest >= 0) {
            val anchorTop = topOf(ClockPosition.ANCHORS[nearest])
            guide.show(anchorTop + layout.faceHeight / 2f)
            return anchorTop
        }
        guide.hide()
        return when {
            wanted < top -> top - (top - wanted) * OVERSTRETCH
            wanted > bottom -> bottom + (wanted - bottom) * OVERSTRETCH
            else -> wanted
        }
    }

    private fun topOf(share: Float): Float = ClockPosition.topAt(share, layout.bandTop, layout.bandBottom, layout.faceHeight)

    private fun shareOf(top: Float): Float = ClockPosition.shareAt(top, layout.bandTop, layout.bandBottom, layout.faceHeight)

    /** Moves the clock one anchor up or down, for those who cannot drag. */
    private fun step(by: Int): Boolean {
        if (!canDrag) return false
        val share = state.shownPosition().share
        val anchors = ClockPosition.ANCHORS
        var index = anchors.indices.minByOrNull { abs(anchors[it] - share) } ?: return false
        index = (index + by).coerceIn(0, anchors.lastIndex)
        state.choosePosition(ClockPosition(anchors[index]))
        view.announceForAccessibility(ClockPosition.nameOf(anchors[index]))
        return true
    }

    private inner class Accessibility : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (!canDrag) return
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, MOVE_UP))
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id, MOVE_DOWN))
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id -> step(-1)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id -> step(1)
            else -> super.performAccessibilityAction(host, action, args)
        }
    }

    /**
     * A line across the preview where the clock's middle stands on an anchor,
     * in the system's accent at the strength Material gives a divider, drawn
     * in the preview's overlay only while the clock is snapped.
     */
    private inner class Guide : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.context.getColor(android.R.color.system_accent1_300)
            alpha = GUIDE_ALPHA
            strokeWidth = GUIDE_DP * view.resources.displayMetrics.density
        }
        private var y = 0f
        private var shown = false

        fun show(at: Float) {
            val parent = view.parent as? View ?: return
            y = at
            if (!shown) {
                shown = true
                setBounds(0, 0, parent.width, parent.height)
                parent.overlay.add(this)
            }
            invalidateSelf()
        }

        fun hide() {
            if (!shown) return
            shown = false
            (view.parent as? View)?.overlay?.remove(this)
        }

        override fun draw(canvas: Canvas) {
            val inset = bounds.width() * GUIDE_INSET
            canvas.drawLine(bounds.left + inset, y, bounds.right - inset, y, paint)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    private companion object {
        /** How near an anchor, in dp of the preview, the clock snaps to it. */
        const val SNAP_DP = 12f

        /** How far the clock follows a finger past the band's ends: a rubber band, not a wall. */
        const val OVERSTRETCH = 0.25f

        const val GUIDE_DP = 1.5f
        const val GUIDE_ALPHA = 160
        const val GUIDE_INSET = 0.06f
        const val MOVE_UP = "Move clock up"
        const val MOVE_DOWN = "Move clock down"
    }
}
