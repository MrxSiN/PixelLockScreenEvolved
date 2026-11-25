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
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.Logger

/**
 * The subject's editor: the lock screen photo, the subject the effect found
 * tinted over it, and a brush that adds to the subject or takes from it. It is
 * a window of SystemUI's own, opened over Wallpaper & style when a person asks
 * for it there ([DepthEditRequest]), since the photo and its mask live in
 * SystemUI and never leave it.
 *
 * Edits are kept apart from the mask ([DepthComposite.applyEdits]), at the
 * mask's size, so they hold however the subject is laid out after (main subject
 * only, the depth map), and are forgotten with the mask when the subject is
 * found again. Saving hands them back ([onSave]); closing in any other way
 * changes nothing.
 */
@SuppressLint("ViewConstructor")
internal class DepthMaskEditor(
    private val host: Context,
    /** The photo at the mask's size. */
    private val photo: Bitmap,
    /** The subject as the effect lays it out before any edits, one value from 0 to 255 per pixel of [photo]. */
    private val found: IntArray,
    /** The edits kept so far, one [DepthComposite] mark per pixel of [photo]. */
    edits: IntArray,
    private val logger: Logger,
    private val onSave: (IntArray) -> Unit,
) {

    private val width = photo.width
    private val height = photo.height
    private val edits = edits.copyOf()
    private val tint = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val density = host.resources.displayMetrics.density
    private lateinit var context: Context
    private var windows: WindowManager? = null
    private var adding = true
    private var brushIndex = 1
    private var root: View? = null

    init {
        repaint(0, 0, width, height)
    }

    /** Opens the editor over whatever is on screen. */
    fun show() {
        val display = host.display ?: host.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
        for (type in WINDOW_TYPES) {
            // A window of each type needs a context of its own: the one SystemUI hands over is the wallpaper's.
            context = runCatching { host.createWindowContext(display, type, null) }.getOrNull() ?: continue
            val manager = context.getSystemService(WindowManager::class.java)
            val view = layout()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE,
            ).apply {
                title = "PixelLockScreenEvolvedDepthEditor"
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            val shown = runCatching { manager.addView(view, params) }
                .onFailure { logger.warn("The subject editor could not open as window type $type", it) }
                .isSuccess
            if (shown) {
                root = view
                windows = manager
                return
            }
        }
    }

    private fun close() {
        root?.let { view -> runCatching { windows?.removeView(view) } }
        root = null
        tint.recycle()
    }

    private fun layout(): View {
        val canvas = Painting()
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val mode = button("Add") {
            adding = !adding
            (it as TextView).text = if (adding) "Add" else "Erase"
        }
        val size = button("Brush M") {
            brushIndex = (brushIndex + 1) % BRUSHES.size
            (it as TextView).text = "Brush ${BRUSH_NAMES[brushIndex]}"
        }
        val reset = button("Reset") {
            edits.fill(DepthComposite.UNEDITED)
            repaint(0, 0, width, height)
            canvas.invalidate()
        }
        val cancel = button("Cancel") { close() }
        val save = button("Save") {
            val saved = edits.copyOf()
            close()
            onSave(saved)
        }
        listOf(mode, size, reset, cancel, save).forEach { bar.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
        val hint = TextView(context).apply {
            text = "Paint over the photo to add to the subject, or switch to Erase to take from it"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }
        return FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isFocusableInTouchMode = true
            addView(canvas, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                hint.setPadding(dp(16), bars.top + dp(12), dp(16), dp(12))
                bar.setPadding(dp(8), dp(12), dp(8), bars.bottom + dp(12))
                canvas.setPadding(0, bars.top + dp(56), 0, bars.bottom + dp(76))
                insets
            }
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) close()
                code == KeyEvent.KEYCODE_BACK
            }
            post { requestFocus() }
        }
    }

    private fun button(label: String, onClick: (View) -> Unit) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(4), dp(12), dp(4), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(BUTTON)
        }
        setOnClickListener(onClick)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    /** Paints the brush at ([x], [y]) in the photo's pixels, and the overlay under it. */
    private fun dab(x: Float, y: Float) {
        val radius = BRUSHES[brushIndex] * max(width, height)
        val mark = if (adding) DepthComposite.ADDED else DepthComposite.REMOVED
        val left = max(0, (x - radius).toInt())
        val top = max(0, (y - radius).toInt())
        val right = min(width, (x + radius).toInt() + 1)
        val bottom = min(height, (y + radius).toInt() + 1)
        for (py in top until bottom) for (px in left until right) {
            val dx = px - x
            val dy = py - y
            if (dx * dx + dy * dy <= radius * radius) edits[py * width + px] = mark
        }
        repaint(left, top, right, bottom)
    }

    /** Tints the overlay where the subject stands, edits included, within the box from ([left], [top]) to ([right], [bottom]). */
    private fun repaint(left: Int, top: Int, right: Int, bottom: Int) {
        if (right <= left || bottom <= top) return
        val row = IntArray(right - left)
        for (y in top until bottom) {
            for (x in left until right) {
                val i = y * width + x
                val subject = when (edits[i]) {
                    DepthComposite.ADDED -> 255
                    DepthComposite.REMOVED -> 0
                    else -> found[i]
                }
                // The subject tinted, the rest dimmed, so both read on any photo.
                row[x - left] = if (subject >= HALF) Color.argb(subject * TINT_ALPHA / 255, 0x4F, 0xC3, 0xF7) else Color.argb(DIM_ALPHA, 0, 0, 0)
            }
            tint.setPixels(row, 0, row.size, left, y, row.size, 1)
        }
    }

    /** The photo, fitted in the space between the hint and the buttons, the overlay over it, painted by touch. */
    private inner class Painting : View(context) {
        private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        private val toView = Matrix()
        private val toPhoto = Matrix()
        private val point = FloatArray(2)
        private val box = RectF()

        override fun onDraw(canvas: Canvas) {
            box.set(paddingLeft.toFloat(), paddingTop.toFloat(), (width - paddingRight).toFloat(), (height - paddingBottom).toFloat())
            toView.setRectToRect(RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()), box, Matrix.ScaleToFit.CENTER)
            toView.invert(toPhoto)
            canvas.drawBitmap(photo, toView, paint)
            canvas.drawBitmap(tint, toView, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked != MotionEvent.ACTION_DOWN && event.actionMasked != MotionEvent.ACTION_MOVE) return true
            for (h in 0..event.historySize) {
                point[0] = if (h < event.historySize) event.getHistoricalX(h) else event.x
                point[1] = if (h < event.historySize) event.getHistoricalY(h) else event.y
                toPhoto.mapPoints(point)
                dab(point[0], point[1])
            }
            invalidate()
            return true
        }
    }

    private companion object {
        /** Brush radius against the photo's long side: small, medium and large. */
        val BRUSHES = floatArrayOf(0.01f, 0.025f, 0.05f)
        val BRUSH_NAMES = listOf("S", "M", "L")

        const val HALF = 128
        const val TINT_ALPHA = 140
        const val DIM_ALPHA = 90
        val BUTTON = Color.argb(0xCC, 0x30, 0x30, 0x30)

        /** An overlay window first; SystemUI's own panel type where an overlay is refused. */
        val WINDOW_TYPES = intArrayOf(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, TYPE_STATUS_BAR_SUB_PANEL)
    }
}

/** `WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL`, the type SystemUI's own dialogs use; hidden from apps. */
private const val TYPE_STATUS_BAR_SUB_PANEL = 2017
