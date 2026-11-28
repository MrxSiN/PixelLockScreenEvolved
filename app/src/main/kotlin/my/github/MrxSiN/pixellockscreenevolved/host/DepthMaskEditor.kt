package my.github.MrxSiN.pixellockscreenevolved.host

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.max
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The subject's editor: the lock screen photo with the subject the effect found, and a brush
 * that adds to the subject, takes from it, or gives a part back to what was found. It is a
 * window of SystemUI's own, opened over Wallpaper & style when a person asks for it there
 * ([DepthEditRequest]), since the photo and its mask live in SystemUI and never leave it.
 *
 * The photo opens fitted to the part the lock screen shows ([frame]) and can be pinched,
 * dragged and painted ([DepthEditCanvas]); every stroke can be undone and redone
 * ([SubjectEdits]). The photo itself, blurred, is the editor's backdrop, and its colours are
 * the system's own (Material You), so it looks like a part of the phone.
 *
 * Edits are kept apart from the mask ([DepthComposite.applyEdits]), at the mask's size, so
 * they hold however the subject is laid out after (main subject only, the depth map), and are
 * forgotten with the mask when the subject is found again. Saving hands them back
 * ([onSave]); closing in any other way changes nothing, and asks first if anything was edited.
 */
internal class DepthMaskEditor(
    private val host: Context,
    /** The photo at the mask's size. */
    private val photo: Bitmap,
    /** The subject as the effect lays it out before any edits, one value from 0 to 255 per pixel of [photo]. */
    private val found: IntArray,
    /** The edits kept so far, one [DepthComposite] mark per pixel of [photo]. */
    kept: IntArray,
    /** The part of [photo] the lock screen shows, in its pixels, or null where it is not known. */
    private val frame: RectF?,
    private val logger: Logger,
    private val onSave: (IntArray) -> Unit,
) {

    private val density = host.resources.displayMetrics.density
    private lateinit var context: Context
    private lateinit var palette: EditorPalette
    private lateinit var canvas: DepthEditCanvas
    private val edits = SubjectEdits(photo.width, photo.height, found, kept) { l, t, r, b -> canvas.refresh(l, t, r, b) }
    private var windows: WindowManager? = null
    private var root: FrameLayout? = null
    private var closing = false
    private val emphasized = PathInterpolator(0.2f, 0f, 0f, 1f)
    private val accelerated = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    private lateinit var undo: View
    private lateinit var redo: View
    private lateinit var zoomChip: TextView
    private lateinit var hint: TextView
    private lateinit var badge: TextView
    private lateinit var header: View
    private lateinit var dock: View
    private lateinit var backdrop: View
    private var confirm: View? = null

    /** Opens the editor over whatever is on screen. */
    fun show() {
        val display = host.display ?: host.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        for (type in WINDOW_TYPES) {
            // A window of each type needs a context of its own: the one SystemUI hands over is the wallpaper's.
            context = runCatching { host.createWindowContext(display, type, null) }.getOrNull() ?: continue
            palette = EditorPalette(context)
            val manager = context.getSystemService(WindowManager::class.java)
            val view = layout()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                // Translucent, so the editor's entrance and exit show Wallpaper & style beneath, not black.
                PixelFormat.TRANSLUCENT,
            ).apply {
                title = "PixelLockScreenEvolvedDepthEditor"
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                // Under the navigation bar too: the window is translucent, so a strip left out showed what is behind it.
                fitInsetsTypes = 0
            }
            val shown = runCatching { manager.addView(view, params) }
                .onFailure { logger.warn("The subject editor could not open as window type $type", it) }
                .isSuccess
            if (shown) {
                root = view
                windows = manager
                enter(view)
                return
            }
            canvas.release()
        }
    }

    /**
     * Brings the editor in: the backdrop fades up, the photo grows into place, then the bar drops
     * in from the top and the tools rise from the bottom, a beat apart, on the system's emphasized
     * curve. [close] plays it back the other way.
     */
    private fun enter(view: FrameLayout) {
        backdrop.alpha = 0f
        backdrop.animate().alpha(1f).setDuration(ENTER_MS).setInterpolator(emphasized).start()
        canvas.alpha = 0f
        canvas.scaleX = ENTER_SCALE
        canvas.scaleY = ENTER_SCALE
        canvas.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ENTER_MS).setInterpolator(emphasized).start()
        slideIn(header, -dp(SLIDE_DP).toFloat(), STAGGER_MS)
        slideIn(dock, dp(SLIDE_DP * 3).toFloat(), STAGGER_MS * 2)
        view.postDelayed({ if (!closing) hint.animate().alpha(0f).setDuration(FADE_MS).start() }, HINT_MS)
    }

    private fun slideIn(view: View, from: Float, delay: Long) {
        view.alpha = 0f
        view.translationY = from
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(ENTER_MS).setInterpolator(emphasized).start()
    }

    /** Takes the editor away as [enter] brought it, reversed and quicker, then removes its window. */
    private fun close() {
        if (closing) return
        closing = true
        val view = root ?: return
        canvas.isEnabled = false
        confirm?.animate()?.alpha(0f)?.setDuration(FADE_MS)?.start()
        header.animate().alpha(0f).translationY(-dp(SLIDE_DP).toFloat()).setStartDelay(0).setDuration(EXIT_MS).setInterpolator(accelerated).start()
        dock.animate().alpha(0f).translationY(dock.height.toFloat() + dp(SLIDE_DP)).setStartDelay(0).setDuration(EXIT_MS).setInterpolator(accelerated).start()
        canvas.animate().alpha(0f).scaleX(EXIT_SCALE).scaleY(EXIT_SCALE).setDuration(EXIT_MS).setInterpolator(accelerated).start()
        backdrop.animate().alpha(0f).setStartDelay(EXIT_MS / 3).setDuration(EXIT_MS).setInterpolator(accelerated).withEndAction {
            runCatching { windows?.removeView(view) }
            root = null
            canvas.release()
        }.start()
    }

    /** Closes, asking first when there are edits that would be lost. */
    private fun leave() {
        if (edits.isDirty) askToDiscard() else close()
    }

    private fun save() {
        root?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!edits.isDirty) return close()
        val saved = edits.snapshot()
        close()
        onSave(saved)
    }

    private fun layout(): FrameLayout {
        canvas = DepthEditCanvas(context, photo, edits, found, frame, palette).apply {
            brushRadius = brushOf(DEFAULT_BRUSH)
            brushColor = toolColor(0)
            onTwoFingerTap = { this@DepthMaskEditor.edits.undo(); historyChanged() }
            onStroke = ::historyChanged
            onZoom = ::zoomChanged
        }

        val top = topBar()
        val looks = segmented(
            listOf("Subject", "Overlay", "Photo"), icons = null, selected = 0, compact = true,
        ) { canvas.look = DepthEditCanvas.Look.entries[it] }
        hint = pill("Pinch to zoom  ·  two fingers to move  ·  two-finger tap to undo")
        badge = pill("Showing the subject as found").apply {
            setTextColor(palette.onAccent)
            background = rounded(palette.accent, dp(20).toFloat())
            alpha = 0f
        }
        zoomChip = pill("").apply {
            setCompoundDrawablesRelative(icon(EditorIcon.Glyph.FIT, palette.onSurface, 18), null, null, null)
            compoundDrawablePadding = dp(8)
            alpha = 0f
            visibility = View.GONE
            setOnClickListener { canvas.home() }
            contentDescription = "Fit the lock screen"
        }
        dock = dock()

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(looks, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(FrameLayout(context).apply {
                addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
                addView(badge, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
            // The photo under the bars is not painted through them, and darkened so they read over it.
            isClickable = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(BAR_SCRIM, 0, 0, 0), Color.TRANSPARENT))
        }
        header = column
        backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(Backdrop(context), match())
            addView(View(context).apply { setBackgroundColor(Color.argb(BACKDROP_SCRIM, 0, 0, 0)) }, match())
        }

        return FrameLayout(context).apply {
            isFocusableInTouchMode = true
            addView(backdrop, match())
            addView(canvas, match())
            addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(zoomChip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
            addView(dock, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                column.setPadding(bars.left, bars.top, bars.right, dp(16))
                (dock.layoutParams as FrameLayout.LayoutParams).setMargins(bars.left + dp(12), 0, bars.right + dp(12), bars.bottom + dp(12))
                dock.requestLayout()
                insets
            }
            addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                (zoomChip.layoutParams as FrameLayout.LayoutParams).bottomMargin = height - dock.top + dp(12)
                canvas.setBox(0f, (column.top + looks.bottom + dp(8)).toFloat(), width.toFloat(), (dock.top - dp(8)).toFloat())
            }
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (confirm != null) dismissConfirm() else leave()
                }
                code == KeyEvent.KEYCODE_BACK
            }
            post { requestFocus() }
            historyChanged()
        }
    }

    private fun topBar(): View {
        val close = iconButton(EditorIcon.Glyph.CLOSE, "Close") { leave() }.apply {
            background = rounded(palette.glass, dp(24).toFloat())
        }
        val titles = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Edit subject", 20f, palette.onSurface, palette.title))
            addView(text("Paint what stands in front", 13f, palette.onSurfaceVariant, palette.regular))
        }
        undo = iconButton(EditorIcon.Glyph.UNDO, "Undo") { edits.undo(); historyChanged() }
        redo = iconButton(EditorIcon.Glyph.REDO, "Redo") { edits.redo(); historyChanged() }
        val history = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(palette.glass, dp(24).toFloat())
            addView(undo)
            addView(redo)
        }
        val save = text("Done", 15f, palette.onAccent, palette.medium).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), 0, dp(18), 0)
            minHeight = dp(44)
            setCompoundDrawablesRelative(icon(EditorIcon.Glyph.CHECK, palette.onAccent, 18), null, null, null)
            compoundDrawablePadding = dp(6)
            background = rounded(palette.accent, dp(22).toFloat())
            contentDescription = "Save and close"
            setOnClickListener { save() }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
            addView(close)
            addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
            addView(history)
            addView(save, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply { marginStart = dp(8) })
        }
    }

    /** The tools, the brush's size, and starting over, on one card along the bottom. */
    private fun dock(): View {
        val tools = segmented(
            listOf("Add", "Erase", "Restore"),
            icons = listOf(EditorIcon.Glyph.ADD, EditorIcon.Glyph.ERASE, EditorIcon.Glyph.RESTORE),
            selected = 0, compact = false,
        ) {
            canvas.mark = TOOL_MARKS[it]
            canvas.brushColor = toolColor(it)
        }
        val slider = BrushSlider(context, palette, DEFAULT_BRUSH).apply {
            onChange = { canvas.brushRadius = brushOf(it) }
            onHold = { canvas.showsBrush = it }
            contentDescription = "Brush size"
        }
        val compare = iconButton(EditorIcon.Glyph.COMPARE, "Hold to see the subject as found") { comparing(!canvas.comparing) }.apply {
            background = rounded(palette.surfaceHighest, dp(24).toFloat())
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        comparing(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> comparing(false)
                }
                true
            }
        }
        val reset = iconButton(EditorIcon.Glyph.RESET, "Reset all edits") {
            edits.reset()
            historyChanged()
            flash("Edits cleared  ·  Undo brings them back")
        }.apply { background = rounded(palette.surfaceHighest, dp(24).toFloat()) }
        val sizeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(Dot(context, dp(3).toFloat(), palette.onSurfaceVariant), LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(slider, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(Dot(context, dp(8).toFloat(), palette.onSurfaceVariant), LinearLayout.LayoutParams(dp(20), dp(20)))
            addView(compare, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
            addView(reset, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(8))
            background = rounded(palette.glass, dp(32).toFloat())
            elevation = dp(6).toFloat()
            addView(tools, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(sizeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                marginStart = dp(4)
            })
            // The photo under the card is not painted through it.
            isClickable = true
        }
    }

    private fun historyChanged() {
        undo.isEnabled = edits.canUndo
        undo.alpha = if (edits.canUndo) 1f else DISABLED_ALPHA
        redo.isEnabled = edits.canRedo
        redo.alpha = if (edits.canRedo) 1f else DISABLED_ALPHA
    }

    /** Shows the subject as found, before any edits, while [on], and says so. */
    private fun comparing(on: Boolean) {
        canvas.comparing = on
        badge.animate().alpha(if (on) 1f else 0f).setDuration(FADE_MS).start()
        if (on) hint.animate().alpha(0f).setStartDelay(0).setDuration(FADE_MS).start()
    }

    /** Says [message] for a moment where the gesture hint shows. */
    private fun flash(message: String) {
        hint.text = message
        hint.animate().cancel()
        hint.alpha = 1f
        hint.animate().alpha(0f).setStartDelay(FLASH_MS).setDuration(FADE_MS).start()
    }

    /** The brush's colour for the tool at [index]: Add in the accent, Erase in the error colour, Restore plain. */
    private fun toolColor(index: Int): Int = when (TOOL_MARKS[index]) {
        DepthComposite.ADDED -> palette.accent
        DepthComposite.REMOVED -> palette.error
        else -> palette.onSurface
    }

    private fun zoomChanged(zoom: Float) {
        val away = zoom !in 0.97f..1.03f
        zoomChip.text = String.format(java.util.Locale.ROOT, "%.1f×", zoom)
        if (away && zoomChip.visibility != View.VISIBLE) {
            zoomChip.visibility = View.VISIBLE
            zoomChip.animate().alpha(1f).setDuration(FADE_MS).start()
        } else if (!away && zoomChip.visibility == View.VISIBLE && zoomChip.alpha == 1f) {
            zoomChip.animate().alpha(0f).setDuration(FADE_MS).withEndAction { zoomChip.visibility = View.GONE }.start()
        }
    }

    /** A card over a scrim: lose the edits, or go back to them. */
    private fun askToDiscard() {
        val view = root ?: return
        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(text("Keep editing", 14f, palette.accent, palette.medium).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                minHeight = dp(40)
                setOnClickListener { dismissConfirm() }
            })
            addView(text("Discard", 14f, palette.onAccent, palette.medium).apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), 0, dp(20), 0)
                minHeight = dp(40)
                background = rounded(palette.accent, dp(20).toFloat())
                setOnClickListener { close() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { marginStart = dp(8) })
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            background = rounded(palette.surfaceHigh, dp(28).toFloat())
            isClickable = true
            addView(text("Discard your edits?", 22f, palette.onSurface, palette.title))
            addView(text("The subject goes back to how it was when you opened the editor.", 14f, palette.onSurfaceVariant, palette.regular),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
        }
        val scrim = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(CONFIRM_SCRIM, 0, 0, 0))
            setOnClickListener { dismissConfirm() }
            addView(card, FrameLayout.LayoutParams(dp(312), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            alpha = 0f
        }
        view.addView(scrim, match())
        scrim.animate().alpha(1f).setDuration(FADE_MS).start()
        card.scaleX = ENTER_SCALE
        card.scaleY = ENTER_SCALE
        card.animate().scaleX(1f).scaleY(1f).setDuration(ENTER_MS).setInterpolator(emphasized).start()
        confirm = scrim
    }

    private fun dismissConfirm() {
        val scrim = confirm ?: return
        confirm = null
        scrim.animate().alpha(0f).setDuration(FADE_MS).withEndAction { root?.removeView(scrim) }.start()
    }

    /**
     * A row of choices, one of them filled, that slides its fill to the one tapped: with [icons]
     * a tall row of tools, [compact] a slim one of looks.
     */
    private fun segmented(labels: List<String>, icons: List<EditorIcon.Glyph>?, selected: Int, compact: Boolean, onSelect: (Int) -> Unit): View {
        val height = if (compact) 36 else 48
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(if (compact) palette.surfaceHigh else palette.surfaceHighest, dp(height / 2 + 4).toFloat())
        }
        val labelViews = ArrayList<TextView>()
        val iconViews = ArrayList<android.widget.ImageView?>()
        val items = labels.mapIndexed { index, label ->
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(if (compact) 14 else 8), 0, dp(if (compact) 14 else 8), 0)
                contentDescription = label
                isClickable = true
                val glyph = icons?.get(index)
                iconViews += glyph?.let {
                    android.widget.ImageView(context).apply { setImageDrawable(EditorIcon(it, palette.onSurface)) }
                        .also { image -> addView(image, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(8) }) }
                }
                labelViews += text(label, if (compact) 13f else 14f, palette.onSurface, palette.medium).also { addView(it) }
            }
        }
        fun select(index: Int) {
            items.forEachIndexed { i, item ->
                val on = i == index
                val ink = if (on) palette.onAccentContainer else palette.onSurfaceVariant
                item.background = if (on) rounded(palette.accentContainer, dp(height / 2).toFloat()) else null
                labelViews[i].setTextColor(ink)
                iconViews[i]?.drawable?.setTint(ink)
                item.isSelected = on
            }
        }
        items.forEachIndexed { index, item ->
            row.addView(item, if (compact) LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(height))
            else LinearLayout.LayoutParams(0, dp(height), 1f))
            item.setOnClickListener {
                if (item.isSelected) return@setOnClickListener
                select(index)
                it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                it.scaleX = PRESS_SCALE
                it.scaleY = PRESS_SCALE
                it.animate().scaleX(1f).scaleY(1f).setDuration(ENTER_MS).setInterpolator(emphasized).start()
                onSelect(index)
            }
        }
        select(selected)
        return row
    }

    private fun iconButton(glyph: EditorIcon.Glyph, label: String, onClick: () -> Unit) = android.widget.ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setImageDrawable(EditorIcon(glyph, palette.onSurface))
        contentDescription = label
        isClickable = true
        isFocusable = true
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        }
    }

    private fun pill(label: String) = text(label, 13f, palette.onSurface, palette.medium).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8))
        background = rounded(palette.surfaceHigh, dp(20).toFloat())
    }

    private fun text(value: String, sp: Float, color: Int, face: Typeface) = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = face
    }

    private fun icon(glyph: EditorIcon.Glyph, color: Int, sizeDp: Int): Drawable =
        EditorIcon(glyph, color).apply { setBounds(0, 0, dp(sizeDp), dp(sizeDp)) }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
    }

    private fun match() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun dp(value: Int): Int = (value * density).roundToInt()

    /** The brush's radius on screen, in pixels, for a slider at [value] from 0 to 1: finer steps among the small sizes. */
    private fun brushOf(value: Float): Float = (SMALLEST_BRUSH + value * value * (LARGEST_BRUSH - SMALLEST_BRUSH)) * density

    /** The photo filling the window, blurred, under everything. */
    private inner class Backdrop(context: Context) : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val fill = Matrix()

        init {
            setRenderEffect(RenderEffect.createBlurEffect(BACKDROP_BLUR * density, BACKDROP_BLUR * density, Shader.TileMode.CLAMP))
        }

        override fun onDraw(canvas: Canvas) {
            val s = max(width.toFloat() / photo.width, height.toFloat() / photo.height)
            fill.setScale(s, s)
            fill.postTranslate((width - photo.width * s) / 2f, (height - photo.height * s) / 2f)
            canvas.drawBitmap(photo, fill, paint)
        }
    }

    /** A dot showing a brush size at one end of its slider. */
    private class Dot(context: Context, private val radius: Float, color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        override fun onDraw(canvas: Canvas) = canvas.drawCircle(width / 2f, height / 2f, radius, paint)
    }

    /** A slim slider for the brush's size, from 0 to 1; [onHold] hears whether a finger is on it. */
    @SuppressLint("ViewConstructor")
    private class BrushSlider(context: Context, palette: EditorPalette, private var value: Float) : View(context) {
        var onChange: (Float) -> Unit = {}
        var onHold: (Boolean) -> Unit = {}
        private val density = resources.displayMetrics.density
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surfaceHighest
            strokeWidth = 6f * density
            strokeCap = Paint.Cap.ROUND
        }
        private val active = Paint(track).apply { color = palette.accent }
        private val thumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent }
        private val thumbRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surfaceHigh }
        private var held = false
        private var lastTick = -1

        private val start get() = paddingStart + 12f * density
        private val end get() = width - paddingEnd - 12f * density

        override fun onDraw(canvas: Canvas) {
            val y = height / 2f
            val x = start + (end - start) * value
            canvas.drawLine(start, y, end, y, track)
            canvas.drawLine(start, y, x, y, active)
            val r = (if (held) 12f else 10f) * density
            canvas.drawCircle(x, y, r + 3f * density, thumbRing)
            canvas.drawCircle(x, y, r, thumb)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (!held) {
                        held = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onHold(true)
                    }
                    value = ((event.x - start) / (end - start)).coerceIn(0f, 1f)
                    val tick = (value * TICKS).toInt()
                    if (tick != lastTick) {
                        if (lastTick >= 0) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        lastTick = tick
                    }
                    onChange(value)
                    invalidate()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    held = false
                    lastTick = -1
                    onHold(false)
                    invalidate()
                }
            }
            return true
        }

        private companion object {
            const val TICKS = 10
        }
    }

    private companion object {
        /** The brush's radius on screen at the slider's ends, in dp, and where it starts. */
        const val SMALLEST_BRUSH = 4f
        const val LARGEST_BRUSH = 80f
        const val DEFAULT_BRUSH = 0.4f

        val TOOL_MARKS = intArrayOf(DepthComposite.ADDED, DepthComposite.REMOVED, DepthComposite.UNEDITED)

        const val BACKDROP_BLUR = 48f
        const val BACKDROP_SCRIM = 150
        const val CONFIRM_SCRIM = 150
        const val BAR_SCRIM = 200
        const val DISABLED_ALPHA = 0.38f
        const val ENTER_SCALE = 0.94f
        const val PRESS_SCALE = 0.92f
        const val EXIT_SCALE = 0.96f
        const val ENTER_MS = 400L
        const val EXIT_MS = 220L
        const val STAGGER_MS = 50L
        const val SLIDE_DP = 32
        const val FADE_MS = 180L
        const val HINT_MS = 4_000L
        const val FLASH_MS = 2_200L

        /** An overlay window first; SystemUI's own panel type where an overlay is refused. */
        val WINDOW_TYPES = intArrayOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, TYPE_STATUS_BAR_SUB_PANEL)
    }
}

/** The editor's colours, the system's own dark Material You palette, and its type. */
internal class EditorPalette(context: Context) {
    val accent = context.getColor(android.R.color.system_accent1_200)
    val onAccent = context.getColor(android.R.color.system_accent1_800)
    val accentContainer = context.getColor(android.R.color.system_accent1_700)
    val onAccentContainer = context.getColor(android.R.color.system_accent1_100)
    val overlay = context.getColor(android.R.color.system_accent3_500)
    val surfaceHigh = context.getColor(android.R.color.system_neutral1_800)
    val surfaceHighest = context.getColor(android.R.color.system_neutral1_700)

    /** The bars' and the dock's surface: the high surface, a little see-through over the photo. */
    val glass = surfaceHigh and 0x00FFFFFF or (GLASS_ALPHA shl 24)
    val error = context.getColor(android.R.color.system_error_200)
    val onSurface = context.getColor(android.R.color.system_neutral1_50)
    val onSurfaceVariant = context.getColor(android.R.color.system_neutral2_200)
    val outline = context.getColor(android.R.color.system_neutral2_500)

    val regular: Typeface = Typeface.create(TEXT_FAMILY, Typeface.NORMAL)
    val medium: Typeface = Typeface.create(regular, MEDIUM, false)
    val title: Typeface = Typeface.create(Typeface.create(TITLE_FAMILY, Typeface.NORMAL), MEDIUM, false)

    private companion object {
        /** Pixel's system fonts; Android falls back to its default sans where a build lacks them. */
        const val TEXT_FAMILY = "google-sans-text"
        const val TITLE_FAMILY = "google-sans"
        const val MEDIUM = 500
        const val GLASS_ALPHA = 0xE6
    }
}

/** `WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL`, the type SystemUI's own dialogs use; hidden from apps. */
private const val TYPE_STATUS_BAR_SUB_PANEL = 2017
