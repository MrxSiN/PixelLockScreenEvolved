package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.ColorStateList
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView

/**
 * "Drag the clock to reposition it", said beside Wallpaper & style's lock
 * screen preview while the clock sheet's Position tab is open and the
 * position is not adaptive: the only times the preview's clock can be dragged
 * ([PickerClockDrag]).
 *
 * The hint stands in the empty column left of the preview when that column is
 * wide enough to read it, and otherwise in the strip above the preview, so it
 * never covers the wallpaper, the clock or a control. It is a plain label
 * added to the activity's content: it takes no touches, so nothing under it
 * stops answering. It follows the preview as the sheet resizes it, before each
 * draw, and fades in and out as the sheet's tabs do; it is removed with the
 * sheet.
 */
internal class PickerDragHint(
    private val sheet: View,
    private val state: PickerClockState,
    colors: PickerThemeColors.InFragment?,
) {

    private val context = sheet.context

    /** The activity's content and the lock screen preview, while the sheet is in a window. */
    private var root: FrameLayout? = null
    private var card: View? = null

    private val icon = PositionIconDrawable(PickerRows.dp(context, ICON_DP), PositionIconDrawable.Glyph.DRAG)
    private val hint = TextView(context).apply {
        text = TEXT
        gravity = Gravity.CENTER
        compoundDrawablePadding = PickerRows.dp(context, ICON_GAP_DP)
        PickerRows.resource(context, "attr", TEXT_APPEARANCE).takeIf { it != 0 }?.let { attr ->
            val value = TypedValue()
            if (context.theme.resolveAttribute(attr, value, true)) setTextAppearance(value.resourceId)
        }
        alpha = 0f
        visibility = View.INVISIBLE
        isClickable = false
        isFocusable = false
    }

    private val cardBounds = RectF()
    private val placedBounds = RectF()
    private var placedWidth = 0
    private var placedHeight = 0
    private var beside: Boolean? = null
    private var wanted = false

    private val placing = ViewTreeObserver.OnPreDrawListener { place(); true }

    init {
        paint(hint.currentTextColor)
        colors?.let { runCatching { it.follow(COLOUR, ::paint) } }
        // The sheet is bound before it joins the window, and may leave and join it again.
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
        (hint.parent as? ViewGroup)?.removeView(hint)
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.viewTreeObserver.addOnPreDrawListener(placing)
    }

    private fun detach() {
        val root = root ?: return
        root.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(placing)
        root.removeView(hint)
        this.root = null
        card = null
        beside = null
        wanted = false
        hint.animate().cancel()
        hint.alpha = 0f
        hint.visibility = View.INVISIBLE
    }

    private fun paint(colour: Int) {
        hint.setTextColor(colour)
        icon.setTintList(ColorStateList.valueOf(colour))
    }

    /** Shows or hides the hint, and keeps it beside the preview where the preview is drawn now. */
    private fun place() {
        val card = card ?: return
        val root = root ?: return
        // Cheapest first: the stored position is read through the picker's registry.
        if (!state.isEditingPosition || !sheet.isShown || !card.isShown || state.shownPosition().adaptive) {
            show(false)
            return
        }

        ViewBounds.inAncestor(root, card, cardBounds)
        val margin = PickerRows.dp(context, MARGIN_DP).toFloat()
        val column = cardBounds.left - 2 * margin
        val side = column >= PickerRows.dp(context, MIN_COLUMN_DP)
        if (side != beside) {
            beside = side
            placedBounds.setEmpty()
            hint.layoutParams = hint.layoutParams.apply { width = if (side) column.toInt() else ViewGroup.LayoutParams.WRAP_CONTENT }
            if (side) hint.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon, null, null)
            else hint.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            // Shown once laid out at its new width and placed, never where it stood before.
            return
        }
        if (hint.width == 0 || hint.height == 0 || hint.isLayoutRequested) return
        if (cardBounds != placedBounds || hint.width != placedWidth || hint.height != placedHeight) {
            placedBounds.set(cardBounds)
            placedWidth = hint.width
            placedHeight = hint.height
            if (side) {
                hint.translationX = margin
                hint.translationY = cardBounds.centerY() - hint.height / 2f
            } else {
                hint.translationX = cardBounds.centerX() - hint.width / 2f
                hint.translationY = cardBounds.top - margin - hint.height
            }
        }
        show(true)
    }


    private fun show(show: Boolean) {
        if (show == wanted) return
        wanted = show
        if (show) hint.visibility = View.VISIBLE
        hint.animate().cancel()
        hint.animate().alpha(if (show) 1f else 0f).setDuration(FADE_MS)
            .withEndAction { if (!wanted) hint.visibility = View.INVISIBLE }
            .start()
    }

    private companion object {
        const val TEXT = "Drag the clock to reposition it"
        const val PREVIEW_CARD = "lock_preview_card"
        const val TEXT_APPEARANCE = "textAppearanceBodySmall"
        const val COLOUR = "colorOnSurfaceVariant"
        const val ICON_DP = 18f
        const val ICON_GAP_DP = 6f
        const val MARGIN_DP = 12f

        /** Narrower than this, the hint would break into a word a line, so it goes above the preview instead. */
        const val MIN_COLUMN_DP = 96f

        /** As the clock sheet fades its tab content. */
        const val FADE_MS = 150L
    }
}
