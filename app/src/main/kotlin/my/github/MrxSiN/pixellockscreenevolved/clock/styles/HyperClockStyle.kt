package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout

import java.util.Calendar
import java.util.Locale

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FillingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralView
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Hyper, after HyperOS's magazine lock screen: the hour over the minute in
 * giant, condensed, outlined figures that fill the lock screen from under the
 * status bar to just above the fingerprint sensor ([FillingClockFace]), the
 * hour in the wallpaper's accent colour, lightly tinted like glass, and the
 * minute in the clock's own, with the date beside them as a narrow column and
 * the weekday turned on its side in an accent pill. The photo shows through the
 * figures, and its subject can stand in front of them.
 *
 * It moves: appearing, the two lines close in on each other from either side
 * while the date slides in; plugging in the charger, a wash of light runs
 * through the hour and then the minute. On the always-on display the figures
 * keep their cut and are traced in a thin line, and the pill becomes an outline.
 */
class HyperClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_HYPER"
    override val name: String = "Hyper"
    override val description: String = "Outlined hour over minute down the whole screen, after HyperOS"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        FaceSize.SMALL -> SmallFace(context)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = box.height() * THUMBNAIL_STROKE
        paint.letterSpacing = TRACKING
        ClockThumbnail.stacked(
            canvas, box, paint, CUT, listOf(ClockThumbnail.SAMPLE_HOUR.padStart(2, '0'), ClockThumbnail.SAMPLE_MINUTE),
            heightShare = 0.92f, widthShare = 0.5f, lineGap = LINE_GAP,
        )
        // The weekday pill beside them, filled, as the face draws it.
        paint.style = Paint.Style.FILL
        val pillWidth = box.width() * 0.09f
        val left = box.right - box.width() * 0.16f
        val pill = RectF(left, box.top + box.height() * 0.3f, left + pillWidth, box.top + box.height() * 0.48f)
        canvas.drawRoundRect(pill, pillWidth / 2, pillWidth / 2, paint)
    }

    /**
     * What both faces share, so the lock screen reads as one design with and
     * without notifications: the hour in the accent, tinted like glass, and the
     * minute in the clock's own colour, both outlined in the condensed cut and
     * traced thin on the always-on display; appearing, the two close in on each
     * other along [axis]; charging, a wash of light runs through each in turn.
     */
    private abstract class HyperFace(context: Context, outlineDp: Float, private val axis: Int) : NumeralClockFace(context, date = null) {

        protected val hour = line(context).apply { setInk(fill = HOUR_FILL, outline = 1f, outlineDp = outlineDp) }
        protected val minute = line(context).apply { setInk(fill = 0f, outline = 1f, outlineDp = outlineDp) }
        protected val accent = HyperColors.accent(context)
        private var color = Color.WHITE
        private var doze = 0f
        private val washes = ArrayList<Animator>(2)

        final override val numerals: List<NumeralView> = listOf(hour, minute)

        /** Views of the face's own that move with it as it appears, after the two lines. */
        protected open val followers: List<View> get() = emptyList()

        override fun write(parts: TimeParts.Parts) {
            hour.setText(parts.hour.padStart(2, '0'))
            minute.setText(parts.minute)
        }

        override fun setColor(color: Int) {
            this.color = color
            super.setColor(color)
            recolor()
        }

        override fun setDoze(fraction: Float) {
            doze = fraction
            super.setDoze(fraction)
            recolor()
        }

        /** The hour in the accent, easing to the clock's own colour on the always-on display. */
        private fun recolor() {
            val accented = HyperColors.accented(color, accent, doze)
            hour.setColor(accented)
            onRecolor(color, accented)
        }

        /** Colours the face's other parts: [plain] the clock's own colour, [accented] the hour's. */
        protected open fun onRecolor(plain: Int, accented: Int) = Unit

        /**
         * The lines close in on each other, growing into place, and the date follows. Not the shared thinning of the
         * strokes ([NumeralView.playEnter]): figures this narrow, thinned on their wide advances, stand apart as hairlines.
         */
        override fun enter() {
            if (!view.isAttachedToWindow || !view.isShown) return
            glide(hour, -ENTER_TRAVEL, delay = 0L)
            glide(minute, ENTER_TRAVEL, delay = STAGGER_MS)
            followers.forEachIndexed { index, it -> glide(it, FOLLOWER_TRAVEL, delay = (index + 2) * STAGGER_MS, across = true) }
        }

        /** A wash of light through each line in turn, again without thinning the strokes. */
        override fun charge() {
            settleWashes()
            if (!view.isAttachedToWindow || !view.isShown) return
            wash(hour, HOUR_FILL, delay = 0L)
            wash(minute, 0f, delay = WASH_STAGGER_MS)
        }

        /** Stops the face's own motion whenever [root] leaves the screen. */
        protected fun settlesWith(root: View) = root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = settle()
        })

        /** Ends every motion of the face's own at once. */
        private fun settle() {
            settleWashes()
            (numerals + followers).forEach {
                it.animate().cancel()
                it.translationX = 0f
                it.translationY = 0f
                it.scaleX = 1f
                it.scaleY = 1f
            }
        }

        /** Slides [target] in from [travel] of its own size away, along [axis], or across it, growing into place. */
        private fun glide(target: View, travel: Float, delay: Long, across: Boolean = false) {
            val vertical = (axis == LinearLayout.VERTICAL) != across
            target.animate().cancel()
            target.translationX = if (vertical) 0f else target.width * travel
            target.translationY = if (vertical) target.height * travel else 0f
            target.scaleX = ENTER_SCALE
            target.scaleY = ENTER_SCALE
            target.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(ENTER_MS).setInterpolator(EMPHASIZED_DECELERATE).start()
        }

        private fun wash(line: NumeralView, rest: Float, delay: Long) {
            washes += ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = delay
                duration = WASH_MS
                interpolator = LinearInterpolator()
                // Up to the peak and back, read without boxing: the fraction folds at the middle.
                addUpdateListener {
                    val fold = 1f - abs(2f * it.animatedFraction - 1f)
                    line.setFill(rest + (WASH_PEAK - rest) * STANDARD.getInterpolation(fold))
                }
                start()
            }
        }

        private fun settleWashes() {
            washes.forEach(Animator::cancel)
            washes.clear()
            hour.setFill(HOUR_FILL)
            minute.setFill(0f)
        }
    }

    /**
     * The large face: the hour over the minute down the lock screen, the date
     * column hanging beside them. The time, not the whole group, stands on the
     * screen's centre line: a blank as wide as the date column balances it on
     * the other side.
     */
    private class Face(context: Context) : HyperFace(context, OUTLINE_DP, LinearLayout.VERTICAL), ResizableClockFace, FillingClockFace {

        private val time = column(Gravity.CENTER_HORIZONTAL, hour, minute)
        private val dateColumn = DateColumn(context, shortSide, accent, HyperColors.onAccent(context))
        private var size = 0f

        /** The room last reported to any face in this process, so a new face starts at it rather than jumping to it. */
        // ponytail: a share of the screen until the host first reports the real room (legacy, non-Compose lock screens never do).
        private var room = knownRoom.takeIf { it > 0f } ?: (screenHeight * FALLBACK_ROOM)

        override val followers: List<View> = listOf(dateColumn)
        override val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = android.view.ViewGroup.LayoutParams(WRAP, WRAP)
            val gap = (shortSide * DATE_GAP).toInt()
            addView(Balance(context, dateColumn), LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = gap })
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(dateColumn, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.TOP
                marginStart = gap
            })
            settlesWith(this)
        }
        override val timeView: View get() = time

        /** The face writes the date itself, in [dateColumn]. */
        override val drawsDate: Boolean = true

        init {
            resize()
            refresh()
        }

        override fun write(parts: TimeParts.Parts) {
            super.write(parts)
            dateColumn.refresh()
        }

        override fun setDoze(fraction: Float) {
            super.setDoze(fraction)
            dateColumn.setDoze(fraction)
        }

        override fun onRecolor(plain: Int, accented: Int) = dateColumn.setColors(plain, accented)

        override fun setSize(size: Float) {
            this.size = size
            resize()
        }

        override fun setRoom(height: Float) {
            room = height
            knownRoom = height
            resize()
        }

        /** Both lines and the gap between them stand [room] tall at the largest size, a little less at the smallest. */
        private fun resize() {
            val line = room * lerp(SMALLEST, LARGEST, size) / (2 + LINE_GAP)
            numerals.forEach { it.fit(line, maxWidth = shortSide * MAX_WIDTH, maxStretch = MAX_STRETCH) }
            spaceAbove(minute, line * LINE_GAP)
        }
    }

    /**
     * The small face, beside notifications: the same hour and minute side by
     * side, in the same cut, colours and outline, filling the band SystemUI
     * gives a small clock (the view's minimum height, which the host sets once
     * the face exists), with the date beside them as the large face has it, so
     * SystemUI leaves out its own date row and the room it took.
     */
    private class SmallFace(context: Context) : HyperFace(context, SMALL_OUTLINE_DP, LinearLayout.HORIZONTAL) {

        private val time = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(hour, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(minute, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = (shortSide * SMALL_GAP).toInt() })
        }
        private val dateBlock = DateColumn(context, shortSide, accent, HyperColors.onAccent(context), upright = true)
        override val followers: List<View> = listOf(dateBlock)

        /** The band the numerals were last fitted to, or -1 before they have been. */
        private var fittedBand = -1

        override val view: View = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                fitTo(minimumHeight)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = android.view.ViewGroup.LayoutParams(WRAP, WRAP)
            addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_VERTICAL })
            addView(dateBlock, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                gravity = Gravity.CENTER_VERTICAL
                marginStart = (shortSide * SMALL_DATE_GAP).toInt()
            })
            settlesWith(this)
        }
        override val timeView: View get() = time

        /** The face writes the date itself, in [dateBlock]. */
        override val drawsDate: Boolean = true

        override fun write(parts: TimeParts.Parts) {
            super.write(parts)
            dateBlock.refresh()
        }

        override fun setDoze(fraction: Float) {
            super.setDoze(fraction)
            dateBlock.setDoze(fraction)
        }

        override fun onRecolor(plain: Int, accented: Int) = dateBlock.setColors(plain, accented)

        init {
            fitTo(0)
            refresh()
        }

        /** Fits the numerals to [band] pixels, or to a Pixel small clock's height where the host gives none. */
        private fun fitTo(band: Int) {
            if (band == fittedBand) return
            fittedBand = band
            val height = if (band > 0) band * SMALL_FILL else shortSide * OneLineSmallFace.PIXEL_NUMERAL_TO_SHORT_SIDE
            numerals.forEach { it.fit(height, maxWidth = shortSide * SMALL_MAX_WIDTH, maxStretch = SMALL_STRETCH) }
        }
    }

    /** A blank as wide as [mirror], so what stands between the two is centred in the row. */
    private class Balance(context: Context, private val mirror: View) : View(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            mirror.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            setMeasuredDimension(mirror.measuredWidth, 0)
        }
    }

    /**
     * The date beside the time. [upright] false: a narrow column for the large face, the month over a slash over
     * the day, in the locale's order, and the weekday turned on its side in a filled pill under them. [upright]
     * true: a compact block for the small face, the weekday pill lying flat over the date on one line. The pill is
     * traced in outline on the always-on display.
     */
    private class DateColumn(
        context: Context,
        shortSide: Float,
        accent: Int,
        private val onAccent: Int,
        private val upright: Boolean = false,
    ) : View(context) {

        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), DATE_WEIGHT, false)
            textSize = shortSide * DATE_SIZE
            textAlign = Paint.Align.CENTER
        }
        private val pillText = Paint(text).apply {
            typeface = Typeface.create(Typeface.create("sans-serif", Typeface.NORMAL), PILL_WEIGHT, false)
            letterSpacing = PILL_TRACKING
        }
        private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        private val pillLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DozeInk.OUTLINE_DP, context.resources.displayMetrics)
        }
        private val pillBox = RectF()
        private var lines = listOf<String>()
        private var weekday = ""
        private var plain = Color.WHITE
        private var doze = 0f

        fun refresh() {
            val now = Calendar.getInstance()
            val locale = Locale.getDefault()
            val month = String.format(locale, "%d", now[Calendar.MONTH] + 1)
            val day = String.format(locale, "%d", now[Calendar.DAY_OF_MONTH])
            val dayFirst = DateFormat.getBestDateTimePattern(locale, "Md").let { it.indexOf('d') in 0 until it.indexOf('M') }
            val parts = if (dayFirst) listOf(day, "/", month) else listOf(month, "/", day)
            val next = if (upright) listOf(parts.joinToString("")) else parts
            val nextWeekday = DateFormat.format("EEE", now).toString().uppercase(locale)
            if (next == lines && nextWeekday == weekday) return
            lines = next
            weekday = nextWeekday
            contentDescription = DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), now)
            requestLayout()
            invalidate()
        }

        /** The date in [plain], the pill in [accent]. */
        fun setColors(plain: Int, accent: Int) {
            this.plain = plain
            text.color = plain
            pill.color = accent
            pillLine.color = accent
            invalidate()
        }

        /** Empties the pill to an outline, and turns its weekday from dark on the accent to the date's own colour. */
        fun setDoze(fraction: Float) {
            doze = fraction.coerceIn(0f, 1f)
            invalidate()
        }

        private fun lineHeight(): Float = text.fontSpacing * LINE_SPACING

        /** The pill's short side, across the weekday, and its long side, along it. */
        private fun pillThickness(): Float = text.textSize * PILL_WIDTH
        private fun pillLength(): Float = pillText.measureText(weekday) + pillThickness() * PILL_ENDS

        private fun textWidth(): Float = lines.maxOfOrNull(text::measureText) ?: 0f

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val stroke = pillLine.strokeWidth
            val width: Float
            val height: Float
            if (upright) {
                width = max(textWidth(), pillLength()) + stroke
                height = pillThickness() + lineHeight() * PILL_GAP + lineHeight() + stroke
            } else {
                width = max(textWidth(), pillThickness()) + stroke
                height = lineHeight() * lines.size + lineHeight() * PILL_GAP + pillLength() + stroke
            }
            setMeasuredDimension(resolveSize(ceil(width).toInt(), widthMeasureSpec), resolveSize(ceil(height).toInt(), heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val centre = width / 2f
            val inset = pillLine.strokeWidth / 2
            val textTop: Float
            if (upright) {
                // The pill on top, the date under it.
                pillBox.set(centre - pillLength() / 2 + inset, inset, centre + pillLength() / 2 - inset, pillThickness() - inset)
                textTop = pillThickness() + lineHeight() * PILL_GAP
            } else {
                // The date on top, the pill standing under it.
                val top = lineHeight() * lines.size + lineHeight() * PILL_GAP
                pillBox.set(centre - pillThickness() / 2 + inset, top + inset, centre + pillThickness() / 2 - inset, top + pillLength() - inset)
                textTop = 0f
            }
            var baseline = textTop - text.ascent()
            lines.forEach {
                canvas.drawText(it, centre, baseline, text)
                baseline += lineHeight()
            }
            val radius = minOf(pillBox.width(), pillBox.height()) / 2
            if (doze < 1f) {
                pill.alpha = ((1f - doze) * OPAQUE).toInt()
                canvas.drawRoundRect(pillBox, radius, radius, pill)
            }
            if (doze > 0f) {
                pillLine.alpha = (doze * OPAQUE).toInt()
                canvas.drawRoundRect(pillBox, radius, radius, pillLine)
            }
            pillText.color = HyperColors.accented(plain, onAccent, doze)
            canvas.save()
            // Standing, the weekday reads downwards, turned a quarter clockwise, as HyperOS sets it.
            if (!upright) canvas.rotate(90f, pillBox.centerX(), pillBox.centerY())
            canvas.drawText(weekday, pillBox.centerX(), pillBox.centerY() - (pillText.ascent() + pillText.descent()) / 2, pillText)
            canvas.restore()
        }
    }

    private companion object {
        /** The room the host last reported in this process ([Face.setRoom]), or 0 before it has. */
        var knownRoom = 0f

        /** Heavy and as narrow as Roboto Flex goes, so two figures fit across while standing most of the screen tall. */
        val CUT = FlexCut(weight = 900f, width = 25f)

        /** How much of the room the face reports ([FillingClockFace]) the two lines fill, at the smallest and largest size. */
        const val SMALLEST = 0.9f
        const val LARGEST = 1f

        /** The room assumed until the host reports it, against the screen's long side: about what a Pixel leaves above its sensor. */
        const val FALLBACK_ROOM = 0.62f

        /**
         * How wide the two figures may be, against the short side, and how far they may be stretched to stand that tall.
         * Past about twice, the cross strokes of the 4, 7 and 1 thin to hairlines while the stems stay heavy.
         */
        const val MAX_WIDTH = 0.6f
        const val MAX_STRETCH = 2.2f

        /** Space between the hour and the minute, against the line height. */
        const val LINE_GAP = 0.05f

        /** The outline, awake, in dp: bold enough to carry figures this large. */
        const val OUTLINE_DP = 3f

        /** The hour's glassy tint: how opaque its accent fill is, awake. */
        const val HOUR_FILL = 0.16f

        const val DATE_GAP = 0.035f
        const val DATE_SIZE = 0.05f
        const val DATE_WEIGHT = 500
        const val PILL_WEIGHT = 700
        const val PILL_TRACKING = 0.08f
        const val LINE_SPACING = 0.95f
        const val PILL_GAP = 0.4f
        const val PILL_WIDTH = 1.7f

        /** Room at the pill's two round ends, against its width. */
        const val PILL_ENDS = 1.1f
        const val OPAQUE = 255
        const val THUMBNAIL_STROKE = 0.025f

        /**
         * Space between the figures, in ems ([NumeralView.setTracking]). Roboto Flex's heaviest, narrowest figures
         * fill their advance, so this is what stands them apart.
         */
        const val TRACKING = 0.05f

        /**
         * The small face: how tall its figures stand against its band, a little past it so they carry beside
         * notifications; the widest each line may be, against the short side; the stretch it may take to reach
         * that height; the space between the hour and the minute, wider than between figures so the two read as
         * a pair; and its outline, in dp.
         */
        const val SMALL_FILL = 1.2f
        const val SMALL_MAX_WIDTH = 0.27f
        const val SMALL_STRETCH = 2.2f
        const val SMALL_GAP = 0.04f
        const val SMALL_DATE_GAP = 0.05f
        const val SMALL_OUTLINE_DP = 2f

        /** Appearing: how far each line travels, against its height, how small it starts, and the date against its width. */
        const val ENTER_TRAVEL = 0.08f
        const val ENTER_SCALE = 0.94f
        const val FOLLOWER_TRAVEL = 0.6f
        const val ENTER_MS = 833L
        const val STAGGER_MS = 70L

        /** Charging: how bright the wash of light gets, and how it runs. */
        const val WASH_PEAK = 0.7f
        const val WASH_MS = 1200L
        const val WASH_STAGGER_MS = 220L

        val EMPHASIZED_DECELERATE = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
        val STANDARD = PathInterpolator(0.2f, 0f, 0f, 1f)

        fun line(context: Context) = NumeralView(context, CUT).apply {
            dozesAsOutline = true
            setTracking(TRACKING)
        }
    }
}
