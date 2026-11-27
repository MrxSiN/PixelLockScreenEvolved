package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Moves and turns the large clock itself in Wallpaper & style's preview while
 * the clock sheet's Position tab is open ([PickerClockState.isEditingPosition]).
 *
 * - One finger drags it up and down: the real face follows the finger one to
 *   one ([ClockLayout.dragTo]), within the band the lock screen allows,
 *   stretching a little and no further past its ends. Near an anchor
 *   ([ClockPosition.ANCHORS]: the top, the quarters, the bottom) it snaps to
 *   it, with the system's segment tick and a guide across the preview where
 *   the clock's middle lands. Not while the position is adaptive.
 * - Two fingers turn it about its middle as they twist ([ClockLayout.turnTo]),
 *   placed or adaptive. Near a detent ([ClockRotation.DETENTS]: upright, 15,
 *   30, 45 degrees and on its side, either way) it settles on it with a tick;
 *   a line through its middle shows the angle and a badge says it. Past 90
 *   degrees it stretches a little and no further. A second finger landing
 *   mid-drag ends the drag where it is and starts the turn.
 * - A double tap stands it upright again.
 *
 * Every gesture is taken anywhere in the band the preview stands in, its
 * sides included ([PickerGestureZone], which hands its touches to [onTouch]),
 * not only on the clock: the preview is small, and a turned clock smaller.
 *
 * Let go, the clock settles on the spatial spring where it was left, and the
 * position is chosen ([PickerClockState.choosePosition]) until Apply or Back.
 * TalkBack and switch access move it from anchor to anchor and turn it from
 * detent to detent, and hear where it landed.
 */
internal class PickerClockDrag(
    private val face: ClockFaceAdapter,
    private val state: PickerClockState,
) {

    private val view = face.view
    private val layout = face.clockLayout
    private val guide = Guide()
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

    private enum class Gesture { NONE, MOVE, TURN }

    private var gesture = Gesture.NONE

    // A one-finger drag.
    private var startY = 0f
    private var startTop = 0f
    private var snapped = -1

    // A two-finger turn.
    private var startAngle = 0f
    private var startTurn = 0f
    private var turnDetent = -1
    private var turned = 0f

    // A tap, for the double tap that stands the clock upright.
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var lastTapAt = 0L

    /** The preview's drawn scale against its own pixels, read as a gesture starts. */
    private var scale = 1f
    private val parentBounds = RectF()

    /** Whether the clock answers touch at all: the Position tab is open and the face can be placed. */
    private val canTouch: Boolean
        get() = state.isEditingPosition && layout.isMovable && layout.faceHeight > 0f

    /** Whether one finger moves it: not while the position is adaptive. */
    private val canMove: Boolean get() = canTouch && !state.shownPosition().adaptive

    fun install() {
        view.accessibilityDelegate = Accessibility()
        synchronized(drags) { drags[view] = this }
    }

    /** Takes a touch of the preview's band ([PickerGestureZone]); whether it is the clock's. */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!canTouch) return false
                val parent = view.parent as? View ?: return false
                ViewBounds.inWindow(parent, parentBounds)
                scale = if (parent.height > 0) (parentBounds.height() / parent.height).takeIf { it > 0f } ?: 1f else 1f
                downX = event.rawX
                downY = event.rawY
                moved = false
                if (canMove) startMove(event.rawY) else gesture = Gesture.NONE
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                if (gesture == Gesture.MOVE) finishMove()
                startTurn(event)
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) moved = true
                when (gesture) {
                    Gesture.MOVE -> layout.dragTo(follow(startTop + (event.rawY - startY) / scale))
                    Gesture.TURN -> if (event.pointerCount >= 2) layout.turnTo(followTurn(startTurn + angleChange(event)))
                    Gesture.NONE -> Unit
                }
            }
            // The turn ends with either finger; the one left does not start a drag, so the clock never jumps to it.
            MotionEvent.ACTION_POINTER_UP -> if (gesture == Gesture.TURN) finishTurn()
            MotionEvent.ACTION_UP -> {
                when (gesture) {
                    Gesture.MOVE -> finishMove()
                    Gesture.TURN -> finishTurn()
                    Gesture.NONE -> Unit
                }
                if (!moved) onTap(event.eventTime)
            }
            MotionEvent.ACTION_CANCEL -> when (gesture) {
                Gesture.MOVE -> finishMove()
                Gesture.TURN -> finishTurn()
                Gesture.NONE -> Unit
            }
        }
        return true
    }


    private fun startMove(rawY: Float) {
        gesture = Gesture.MOVE
        startY = rawY
        startTop = view.top + view.translationY
        snapped = -1
    }

    private fun finishMove() {
        gesture = Gesture.NONE
        val share = if (snapped >= 0) ClockPosition.ANCHORS[snapped] else layout.shareAt(view.top + view.translationY).coerceIn(0f, 1f)
        guide.hide()
        val before = state.shownPosition()
        state.choosePosition(before.copy(share = share, adaptive = false))
        layout.endDrag()
        if (moved) view.announceForAccessibility(ClockPosition.nameOf(share))
    }

    private fun startTurn(event: MotionEvent) {
        gesture = Gesture.TURN
        moved = true
        startAngle = angleOf(event)
        startTurn = layout.rotation()
        turned = startTurn
        turnDetent = ClockRotation.detentOf(startTurn, 0.01f)
    }

    private fun finishTurn() {
        gesture = Gesture.NONE
        guide.hide()
        val degrees = ClockRotation.clamp(if (turnDetent >= 0) ClockRotation.DETENTS[turnDetent] else Math.round(turned).toFloat())
        state.choosePosition(state.shownPosition().copy(rotation = degrees))
        layout.endTurn()
        view.announceForAccessibility(ClockRotation.nameOf(degrees))
    }

    /** A tap that follows another within the double-tap time stands the clock upright. */
    private fun onTap(at: Long) {
        if (at - lastTapAt > ViewConfiguration.getDoubleTapTimeout()) {
            lastTapAt = at
            return
        }
        lastTapAt = 0L
        if (state.shownPosition().rotation == 0f) return
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        state.choosePosition(state.shownPosition().copy(rotation = 0f))
        view.announceForAccessibility(ClockRotation.nameOf(0f))
    }

    /**
     * The angle, in degrees, of the line from the first finger to the second, on screen: the clock's own
     * coordinates turn with it, and measured there a twist came out at half its angle.
     */
    private fun angleOf(event: MotionEvent): Float =
        Math.toDegrees(atan2((event.getRawY(1) - event.getRawY(0)).toDouble(), (event.getRawX(1) - event.getRawX(0)).toDouble())).toFloat()

    /** How far the fingers have twisted since the turn began, in degrees, the shorter way round. */
    private fun angleChange(event: MotionEvent): Float {
        var change = angleOf(event) - startAngle
        while (change > HALF_TURN) change -= FULL_TURN
        while (change < -HALF_TURN) change += FULL_TURN
        return change
    }

    /** How far the face turns for fingers asking for [wanted]: settled on a detent near it, held back past 90 degrees. */
    private fun followTurn(wanted: Float): Float {
        val max = ClockRotation.MAX_DEGREES
        val detent = ClockRotation.detentOf(wanted)
        if (detent != turnDetent) {
            turnDetent = detent
            if (detent >= 0) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        turned = when {
            detent >= 0 -> ClockRotation.DETENTS[detent]
            wanted > max -> max + (wanted - max) * OVERSTRETCH
            wanted < -max -> -max + (wanted + max) * OVERSTRETCH
            else -> wanted
        }
        guide.showTurn(view.top + view.translationY + view.height / 2f, turned, snapped = detent >= 0)
        return turned
    }

    /** Where the face's top goes for a finger asking for [wanted]: snapped to an anchor near it, held back past the ends. */
    private fun follow(wanted: Float): Float {
        val top = layout.topAt(0f)
        val bottom = layout.topAt(1f)
        val snapDistance = SNAP_DP * view.resources.displayMetrics.density
        var nearest = -1
        for (i in ClockPosition.ANCHORS.indices) {
            if (abs(layout.topAt(ClockPosition.ANCHORS[i]) - wanted) < snapDistance) nearest = i
        }
        if (nearest != snapped) {
            snapped = nearest
            if (nearest >= 0) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        }
        if (nearest >= 0) {
            val anchorTop = layout.topAt(ClockPosition.ANCHORS[nearest])
            guide.showLevel(anchorTop + layout.faceHeight / 2f)
            return anchorTop
        }
        guide.hide()
        return when {
            wanted < top -> top - (top - wanted) * OVERSTRETCH
            wanted > bottom -> bottom + (wanted - bottom) * OVERSTRETCH
            else -> wanted
        }
    }

    /** Moves the clock one anchor up or down, for those who cannot drag. */
    private fun step(by: Int): Boolean {
        if (!canMove) return false
        val position = state.shownPosition()
        val anchors = ClockPosition.ANCHORS
        var index = anchors.indices.minByOrNull { abs(anchors[it] - position.share) } ?: return false
        index = (index + by).coerceIn(0, anchors.lastIndex)
        state.choosePosition(position.copy(share = anchors[index]))
        view.announceForAccessibility(ClockPosition.nameOf(anchors[index]))
        return true
    }

    /** Turns the clock to the next detent left ([by] -1) or right (1), for those who cannot twist. */
    private fun turnStep(by: Int): Boolean {
        if (!canTouch) return false
        val position = state.shownPosition()
        val detents = ClockRotation.DETENTS
        val next = if (by > 0) detents.firstOrNull { it > position.rotation + 0.5f } else detents.lastOrNull { it < position.rotation - 0.5f }
        next ?: return false
        state.choosePosition(position.copy(rotation = next))
        view.announceForAccessibility(ClockRotation.nameOf(next))
        return true
    }

    private inner class Accessibility : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (!canTouch) return
            if (canMove) {
                info.addAction(AccessibilityAction(AccessibilityAction.ACTION_SCROLL_UP.id, MOVE_UP))
                info.addAction(AccessibilityAction(AccessibilityAction.ACTION_SCROLL_DOWN.id, MOVE_DOWN))
            }
            info.addAction(AccessibilityAction(AccessibilityAction.ACTION_SCROLL_LEFT.id, TURN_LEFT))
            info.addAction(AccessibilityAction(AccessibilityAction.ACTION_SCROLL_RIGHT.id, TURN_RIGHT))
            info.stateDescription = ClockRotation.nameOf(state.shownPosition().rotation)
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean = when (action) {
            AccessibilityAction.ACTION_SCROLL_UP.id -> step(-1)
            AccessibilityAction.ACTION_SCROLL_DOWN.id -> step(1)
            AccessibilityAction.ACTION_SCROLL_LEFT.id -> turnStep(-1)
            AccessibilityAction.ACTION_SCROLL_RIGHT.id -> turnStep(1)
            else -> super.performAccessibilityAction(host, action, args)
        }
    }

    /**
     * What the preview shows over the clock while it is placed by hand, in the
     * preview's overlay, in the system's accent: a level line across the
     * preview where a dragged clock's middle stands on an anchor; or, while it
     * is turned, a line through its middle at its angle, over a faint level
     * one, and a badge with the angle, the line at full strength on a detent.
     * The overlay is drawn at the preview's scale, about half the screen's, so
     * the line and the badge are drawn that much larger, to read at their size.
     */
    private inner class Guide : Drawable() {
        private val density = view.resources.displayMetrics.density
        private val accent = view.context.getColor(android.R.color.system_accent1_300)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            strokeCap = Paint.Cap.ROUND
        }
        private val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = view.context.getColor(android.R.color.system_accent1_100) }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = view.context.getColor(android.R.color.system_accent1_900)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val textPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, BADGE_TEXT_SP, view.resources.displayMetrics)
        private val badgeBox = RectF()
        private var y = 0f
        private var degrees = 0f
        private var turning = false
        private var onDetent = false
        private var shown = false

        /** A level line where a dragged clock's middle stands, at [at]. */
        fun showLevel(at: Float) {
            y = at
            turning = false
            show()
        }

        /** The clock's angle, [degrees], through its middle at [at]; at full strength when [snapped] to a detent. */
        fun showTurn(at: Float, degrees: Float, snapped: Boolean) {
            y = at
            this.degrees = degrees
            onDetent = snapped
            turning = true
            show()
        }

        private fun show() {
            val parent = view.parent as? View ?: return
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
            // Screen pixels in the preview's: what the preview's scale takes away is given back.
            val unit = 1f / scale
            line.strokeWidth = GUIDE_DP * density * unit
            text.textSize = textPx * unit
            val inset = bounds.width() * GUIDE_INSET
            if (!turning) {
                line.alpha = LEVEL_ALPHA
                canvas.drawLine(bounds.left + inset, y, bounds.right - inset, y, line)
                return
            }
            val middleX = bounds.exactCenterX()
            val reach = bounds.width() / 2f - inset
            // The level the angle is measured from, faint.
            line.alpha = FAINT_ALPHA
            canvas.drawLine(middleX - reach, y, middleX + reach, y, line)
            val radians = Math.toRadians(degrees.toDouble())
            val dx = (cos(radians) * reach).toFloat()
            val dy = (sin(radians) * reach).toFloat()
            line.alpha = if (onDetent) DETENT_ALPHA else LEVEL_ALPHA
            canvas.drawLine(middleX - dx, y - dy, middleX + dx, y + dy, line)
            // The badge, above the clock's middle, clear of the line.
            val label = ClockRotation.label(degrees)
            val padding = BADGE_PADDING_DP * density * unit
            val width = text.measureText(label) + 2 * padding
            val height = text.textSize + padding
            val top = y - BADGE_LIFT_DP * density * unit - height
            badgeBox.set(middleX - width / 2, top, middleX + width / 2, top + height)
            badge.alpha = if (onDetent) DETENT_ALPHA else BADGE_ALPHA
            canvas.drawRoundRect(badgeBox, height / 2, height / 2, badge)
            canvas.drawText(label, middleX, badgeBox.centerY() - (text.ascent() + text.descent()) / 2, text)
        }

        override fun setAlpha(alpha: Int) = Unit

        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        /** Every preview clock's drag, by its face, without keeping either alive. */
        private val drags = java.util.WeakHashMap<View, PickerClockDrag>()

        /** The drag of the clock shown in the preview of the window whose root view is [root], if one is. */
        fun shownIn(root: View): PickerClockDrag? = synchronized(drags) {
            drags.entries.firstOrNull { (face, _) -> face.isAttachedToWindow && face.isShown && face.rootView === root }?.value
        }

        /** How near an anchor, in dp of the preview, the clock snaps to it. */
        private const val SNAP_DP = 12f

        /** How far the clock follows a finger past the band's ends or a twist past 90 degrees: a rubber band, not a wall. */
        private const val OVERSTRETCH = 0.25f

        private const val HALF_TURN = 180f
        private const val FULL_TURN = 360f

        private const val GUIDE_DP = 1.5f
        private const val GUIDE_INSET = 0.06f
        private const val LEVEL_ALPHA = 160
        private const val FAINT_ALPHA = 70
        private const val DETENT_ALPHA = 255
        private const val BADGE_ALPHA = 225
        private const val BADGE_TEXT_SP = 13f
        private const val BADGE_PADDING_DP = 10f
        private const val BADGE_LIFT_DP = 28f

        private const val MOVE_UP = "Move clock up"
        private const val MOVE_DOWN = "Move clock down"
        private const val TURN_LEFT = "Turn clock left"
        private const val TURN_RIGHT = "Turn clock right"
    }
}
