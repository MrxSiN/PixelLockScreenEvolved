package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Always-on display page's Dot size ([AodDotDensity]): the person's own wallpaper drawn as the
 * always-on display will draw it, at the dot size chosen and at the size it will be on screen, over
 * a stepped slider that previews each stop as it is dragged across and keeps it once let go. Shown
 * only while the wallpaper is drawn as dots.
 */
internal class PickerDotDensity(private val rows: PickerRows) {

    /** The section's views, to show or hide as the appearance changes. */
    class Section(private val views: List<View>) {
        fun show(shown: Boolean) {
            val visibility = if (shown) View.VISIBLE else View.GONE
            views.forEach { it.visibility = visibility }
        }
    }

    /** Adds the section to [content] and keeps it in step with the setting while [page] is open. */
    fun add(context: Context, page: PickerPage, content: ViewGroup): Section {
        val resolver = context.contentResolver
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).roundToInt()

        val preview = DotPreview(context)
        val value = text(context, "", VALUE_SP, MEDIUM).apply { gravity = Gravity.END }
        val title = text(context, TITLE, TITLE_SP, MEDIUM)
        val heading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(value)
        }
        val slider = StepSlider(context, AodDotDensity.STOPS, AodDotDensity.get(resolver)).apply {
            contentDescription = TITLE
        }
        val ends = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(text(context, FINE_END, END_SP, REGULAR), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(text(context, BOLD_END, END_SP, REGULAR).apply { gravity = Gravity.END })
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP - 4))
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(PREVIEW_HEIGHT_DP)))
            addView(heading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18f)
                marginStart = dp(4f)
                marginEnd = dp(4f)
            })
            addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(SLIDER_HEIGHT_DP)).apply { topMargin = dp(6f) })
            addView(ends, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(4f)
                marginEnd = dp(4f)
            })
        }
        rows.setEntryBackground(card, SINGLE_BACKGROUND)
        rows.paint(title, "colorOnSurface", title::setTextColor)
        rows.paint(value, "colorPrimary", value::setTextColor)
        for (i in 0 until ends.childCount) (ends.getChildAt(i) as TextView).let { end -> rows.paint(end, "colorOnSurfaceVariant", end::setTextColor) }
        rows.paint(slider, "colorPrimary") { slider.active = it }
        rows.paint(slider, "colorSecondaryContainer") { slider.inactive = it }
        rows.paint(slider, "colorOnPrimary") { slider.onActive = it }
        rows.paint(slider, "colorOnSecondaryContainer") { slider.onInactive = it }

        fun show(stop: Int, animate: Boolean) {
            value.text = AodDotDensity.labels[stop]
            slider.stateDescription = AodDotDensity.labels[stop]
            preview.showCell(AodDotDensity.cellDp(stop), animate)
        }
        // The preview follows every stop the finger crosses; the setting, and so the always-on display, the one let go on.
        slider.onStop = { stop -> show(stop, animate = true) }
        slider.onChosen = { stop -> AodDotDensity.put(resolver, stop) }
        show(slider.stop, animate = false)
        page.keep(AodDotDensity.observe(resolver) {
            val stored = AodDotDensity.get(resolver)
            if (!slider.isDragging && stored != slider.stop) {
                slider.moveTo(stored, animate = true)
                show(stored, animate = true)
            }
        })
        loadWallpaper(context, preview)

        val sectionTitle = rows.sectionTitle(context, SECTION_TITLE)
        content.addView(sectionTitle)
        content.addView(card)
        return Section(listOf(sectionTitle, card))
    }

    /** Hands [preview] the lock screen's wallpaper, or the home screen's where the lock screen has none of its own, read off the main thread. */
    private fun loadWallpaper(context: Context, preview: DotPreview) {
        Thread {
            val photo = runCatching {
                val wallpapers = WallpaperManager.getInstance(context)
                val drawable = wallpapers.getDrawable(WallpaperManager.FLAG_LOCK) ?: wallpapers.getDrawable(WallpaperManager.FLAG_SYSTEM)
                drawable?.let { small(it, context.resources.displayMetrics.widthPixels) }
            }.getOrNull()
            if (photo != null) preview.post { preview.photo = photo }
        }.apply { isDaemon = true }.start()
    }

    /** [drawable] as a bitmap [width] wide, its own proportions kept. */
    private fun small(drawable: Drawable, width: Int): Bitmap? {
        val w = drawable.intrinsicWidth
        val h = drawable.intrinsicHeight
        if (w <= 0 || h <= 0) return null
        val height = (h.toFloat() * width / w).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
        }
    }

    private fun text(context: Context, value: String, sp: Float, weight: Int) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(Typeface.create(FONT, Typeface.NORMAL), weight, false)
    }

    /**
     * The always-on display's dots over the wallpaper, in a rounded black frame as the screen is at
     * night: the photo spread across the frame's width as it spreads across the screen, so the dots
     * are drawn at the size they will be, a little above its middle, where faces usually are. Before
     * the wallpaper is read, and where it cannot be, a soft glow stands in for it.
     */
    @SuppressLint("ViewConstructor")
    private class DotPreview(context: Context) : View(context) {
        private val renderer = AodWallpaperPaint(context.resources)
        private val placing = Matrix()
        private var cellAnimator: ValueAnimator? = null
        private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

        var photo: Bitmap? = null
            set(value) {
                field = value
                placedFor = 0f
                alpha = 0.4f
                animate().alpha(1f).setDuration(FADE_MS).start()
                invalidate()
            }

        init {
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, CORNER_DP * resources.displayMetrics.density)
            }
            clipToOutline = true
            setBackgroundColor(Color.BLACK)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        /** Draws the dots at [cellDp], easing their size there where [animate]. */
        fun showCell(cellDp: Float, animate: Boolean) {
            cellAnimator?.cancel()
            if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
                renderer.cellDp = cellDp
                invalidate()
                return
            }
            cellAnimator = ValueAnimator.ofFloat(renderer.cellDp, cellDp).apply {
                duration = RESIZE_MS
                interpolator = ease
                addUpdateListener {
                    renderer.cellDp = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            renderer.preview(canvas, source(w, h), w, h)
        }

        /** The photo placed for the frame, kept while the frame keeps its size, so a frame of the resizing allocates none. */
        private var placed: Shader? = null
        private var placedFor = 0f

        private fun source(w: Float, h: Float): Shader {
            if (placedFor == w * h) placed?.let { return it }
            placedFor = w * h
            return place(w, h).also { placed = it }
        }

        private fun place(w: Float, h: Float): Shader {
            val picture = photo ?: return RadialGradient(w / 2f, h / 2f, max(w, h) * 0.6f, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
            val scale = max(w / picture.width, h / picture.height)
            placing.setScale(scale, scale)
            val top = (picture.height * scale * FOCUS - h / 2f).coerceIn(0f, max(0f, picture.height * scale - h))
            placing.postTranslate((w - picture.width * scale) / 2f, -top)
            return BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(placing) }
        }

        private companion object {
            const val CORNER_DP = 20f
            const val FADE_MS = 300L
            const val RESIZE_MS = 260L

            /** Where in the photo, from its top, the frame is centred. */
            const val FOCUS = 0.4f
        }
    }

    /**
     * A slider of a few stops, in Material 3's expressive shape: a thick rounded track, filled to a
     * slim upright handle with a gap either side, a mark at each stop. Dragging moves the handle with
     * the finger and ticks as it crosses a stop; letting go, or a tap, settles it on the nearest.
     * Read by TalkBack as a range, and moved by its scroll actions and the arrow keys.
     */
    @SuppressLint("ViewConstructor")
    private class StepSlider(context: Context, private val stops: Int, initial: Int) : View(context) {
        var onStop: (Int) -> Unit = {}
        var onChosen: (Int) -> Unit = {}

        var active = Color.WHITE
            set(value) { field = value; invalidate() }
        var inactive = Color.DKGRAY
            set(value) { field = value; invalidate() }
        var onActive = Color.BLACK
            set(value) { field = value; invalidate() }
        var onInactive = Color.GRAY
            set(value) { field = value; invalidate() }

        /** The stop the handle is on, or nearest while dragged. */
        var stop = initial.coerceIn(0, stops - 1)
            private set

        var isDragging = false
            private set

        private val density = resources.displayMetrics.density
        private var position = stop.toFloat()
        private var pressed = 0f
        private var settling: ValueAnimator? = null
        private var pressing: ValueAnimator? = null
        private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bar = RectF()

        init {
            isFocusable = true
            isClickable = true
        }

        private val start get() = paddingStart + HANDLE_ROOM_DP * density
        private val end get() = width - paddingEnd - HANDLE_ROOM_DP * density
        private val isRtl get() = layoutDirection == LAYOUT_DIRECTION_RTL

        /** Where [at], a position from 0 to the last stop, lies across the view. */
        private fun xOf(at: Float): Float {
            val share = at / (stops - 1)
            return if (isRtl) end - (end - start) * share else start + (end - start) * share
        }

        private fun positionAt(x: Float): Float {
            val share = ((x - start) / (end - start)).coerceIn(0f, 1f)
            return (if (isRtl) 1f - share else share) * (stops - 1)
        }

        override fun onDraw(canvas: Canvas) {
            val cy = height / 2f
            val track = TRACK_DP * density
            val radius = track / 2f
            val gap = GAP_DP * density
            val handleX = xOf(position)
            val left = start - radius
            val right = end + radius
            val (filledFrom, filledTo) = if (isRtl) handleX + gap to right else left to handleX - gap
            val (emptyFrom, emptyTo) = if (isRtl) left to handleX - gap else handleX + gap to right

            paint.color = inactive
            if (emptyTo > emptyFrom) {
                bar.set(emptyFrom, cy - radius, emptyTo, cy + radius)
                canvas.drawRoundRect(bar, radius, radius, paint)
            }
            paint.color = active
            if (filledTo > filledFrom) {
                bar.set(filledFrom, cy - radius, filledTo, cy + radius)
                canvas.drawRoundRect(bar, radius, radius, paint)
            }
            // A mark at each stop, but none under or beside the handle.
            val mark = MARK_DP * density / 2f
            for (i in 0 until stops) {
                val x = xOf(i.toFloat())
                if (abs(x - handleX) < gap + mark * 2) continue
                val filled = if (isRtl) x > handleX else x < handleX
                paint.color = if (filled) onActive else onInactive
                canvas.drawCircle(x, cy, mark, paint)
            }
            // The handle narrows while held, as Material's expressive slider does.
            val handleWidth = (HANDLE_WIDTH_DP - (HANDLE_WIDTH_DP - HANDLE_PRESSED_WIDTH_DP) * pressed) * density
            val handleHeight = HANDLE_HEIGHT_DP * density
            paint.color = active
            bar.set(handleX - handleWidth / 2f, cy - handleHeight / 2f, handleX + handleWidth / 2f, cy + handleHeight / 2f)
            canvas.drawRoundRect(bar, handleWidth / 2f, handleWidth / 2f, paint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    settling?.cancel()
                    isDragging = true
                    press(true)
                    follow(event.x)
                }
                MotionEvent.ACTION_MOVE -> follow(event.x)
                MotionEvent.ACTION_UP -> {
                    follow(event.x)
                    release()
                    performClick()
                }
                MotionEvent.ACTION_CANCEL -> release()
            }
            return true
        }

        override fun performClick(): Boolean = super.performClick()

        private fun follow(x: Float) {
            position = positionAt(x)
            reach(position.roundToInt())
            invalidate()
        }

        private fun release() {
            isDragging = false
            press(false)
            settleTo(stop)
            onChosen(stop)
        }

        /** Tells of [next] as the nearest stop, with a tick, when it is a new one. */
        private fun reach(next: Int) {
            if (next == stop) return
            stop = next
            performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            onStop(stop)
        }

        /** Puts the handle on [target], as the stored setting changed elsewhere. */
        fun moveTo(target: Int, animate: Boolean) {
            stop = target.coerceIn(0, stops - 1)
            if (animate) settleTo(stop) else {
                position = stop.toFloat()
                invalidate()
            }
        }

        private fun settleTo(target: Int) {
            settling?.cancel()
            settling = ValueAnimator.ofFloat(position, target.toFloat()).apply {
                duration = SETTLE_MS
                interpolator = ease
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        private fun press(down: Boolean) {
            pressing?.cancel()
            pressing = ValueAnimator.ofFloat(pressed, if (down) 1f else 0f).apply {
                duration = PRESS_MS
                addUpdateListener {
                    pressed = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        /** Steps by [delta] stops and keeps it, as a key or TalkBack asks. */
        private fun step(delta: Int): Boolean {
            val next = (stop + delta).coerceIn(0, stops - 1)
            if (next == stop) return false
            reach(next)
            settleTo(next)
            onChosen(next)
            return true
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            val forward = if (isRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
            val back = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            return when (keyCode) {
                forward -> step(1)
                back -> step(-1)
                else -> super.onKeyDown(keyCode, event)
            }
        }

        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.SeekBar"
            info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, (stops - 1).toFloat(), stop.toFloat())
            if (stop < stops - 1) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            if (stop > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        }

        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> step(1)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> step(-1)
            android.R.id.accessibilityActionSetProgress -> {
                val target = arguments?.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)?.roundToInt()
                target != null && step(target - stop)
            }
            else -> super.performAccessibilityAction(action, arguments)
        }

        private companion object {
            const val TRACK_DP = 16f
            const val GAP_DP = 6f
            const val MARK_DP = 4f
            const val HANDLE_WIDTH_DP = 4f
            const val HANDLE_PRESSED_WIDTH_DP = 2f
            const val HANDLE_HEIGHT_DP = 44f

            /** Room kept at each end so the handle and the track's round ends stay inside the view. */
            const val HANDLE_ROOM_DP = 10f
            const val SETTLE_MS = 240L
            const val PRESS_MS = 120L
        }
    }

    private companion object {
        const val SECTION_TITLE = "Dots"
        const val TITLE = "Dot size"
        const val FINE_END = "More detail"
        const val BOLD_END = "Bolder dots"
        const val SINGLE_BACKGROUND = "customization_option_entry_singleton_background"
        const val FONT = "google-sans-text"
        const val MEDIUM = 500
        const val REGULAR = 400
        const val TITLE_SP = 16f
        const val VALUE_SP = 14f
        const val END_SP = 12f
        const val CARD_PADDING_DP = 16f
        const val PREVIEW_HEIGHT_DP = 168f
        const val SLIDER_HEIGHT_DP = 52f
    }
}
