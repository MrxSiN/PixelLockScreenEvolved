package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

import java.util.Calendar

import kotlin.math.min

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockWeather
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ExpressiveClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.WeatherClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DozeInk
import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring

/**
 * Analog: a dial with rounded hands, in the lock screen's own colour, the date
 * under it. Like SystemUI's own faces it takes its colour from the Color tab,
 * ticks once a minute, and on the always-on display keeps only the hands, in
 * outline. Its hands move on Material 3 Expressive springs: each minute they
 * step on with a little bounce, as the lock screen appears they sweep into
 * place, and as the charger is plugged in the minute hand gives a nudge.
 */
class AnalogClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_ANALOG"
    override val name: String = "Analog"
    override val description: String = "A dial with rounded hands, the date below"
    override val isResizable: Boolean = true

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> LargeFace(context)
        FaceSize.SMALL -> Face(context, date = null, SMALL_FACE)
    }

    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        val side = min(box.width(), box.height()) * THUMBNAIL_SHARE
        val dial = RectF(box.exactCenterX() - side / 2, box.exactCenterY() - side / 2, box.exactCenterX() + side / 2, box.exactCenterY() + side / 2)
        Dial.draw(canvas, dial, paint, Dial.hourAngle(9, 41), Dial.minuteAngle(41), ink = null)
    }

    /** The dial and, on the large face, the date under it. */
    private open class Face(
        context: Context,
        private val date: DateLine?,
        diameterToShortSide: Float,
    ) : ClockFace, DozingClockFace, ExpressiveClockFace, WeatherClockFace {

        protected val shortSide = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }.toFloat()
        protected val dial = DialView(context).apply { diameter = shortSide * diameterToShortSide }

        override val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(WRAP, WRAP)
            addView(dial, LinearLayout.LayoutParams(WRAP, WRAP))
            date?.let { line ->
                addView(line.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (shortSide * DATE_GAP).toInt() })
            }
        }

        override val timeView: View get() = dial
        override val drawsDate: Boolean get() = date != null

        init {
            refresh()
        }

        override fun refresh() {
            dial.showNow()
            date?.refresh()
        }

        override fun enter() = dial.sweepIn()

        override fun charge() = dial.nudge()

        override fun setColor(color: Int) {
            dial.ink.color = color
            dial.invalidate()
            date?.setColor(color)
        }

        override fun setDoze(fraction: Float) {
            dial.ink.outline = fraction
            dial.invalidate()
        }

        override fun setWeather(weather: ClockWeather?) {
            date?.setWeather(weather)
        }
    }

    /** The large face, whose dial grows with the size chosen. */
    private class LargeFace(context: Context) : Face(context, DateLine(context, DATE), SMALLEST), ResizableClockFace {
        override fun setSize(size: Float) {
            dial.diameter = shortSide * (SMALLEST + (LARGEST - SMALLEST) * size.coerceIn(0f, 1f))
        }
    }

    /** The dial, drawn at [diameter], its hands where they are on their way to the time. */
    private class DialView(context: Context) : View(context) {

        val ink = DozeInk(context)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val box = RectF()
        private var hourAngle = 0f
        private var minuteAngle = 0f
        private var placed = false
        private var motion: ValueAnimator? = null

        var diameter: Float = 0f
            set(value) {
                field = value
                requestLayout()
                invalidate()
            }

        /** Moves the hands to the time now, stepping on with a little bounce where the dial is on screen. */
        fun showNow() {
            val now = Calendar.getInstance()
            val hour = Dial.hourAngle(now[Calendar.HOUR], now[Calendar.MINUTE])
            val minute = Dial.minuteAngle(now[Calendar.MINUTE])
            if (!placed || !isAttachedToWindow || !isShown) {
                placed = true
                place(hour, minute)
                return
            }
            // Past twelve the hands keep turning forward rather than sweeping back round.
            spring(ExpressiveSpring.FAST_SPATIAL, hourAngle, forward(hourAngle, hour), minuteAngle, forward(minuteAngle, minute))
        }

        /** Sweeps the hands into place from a little behind, as the lock screen appears. */
        fun sweepIn() {
            if (!isAttachedToWindow || !isShown) return
            spring(ExpressiveSpring.DEFAULT_SPATIAL, hourAngle - SWEEP_DEGREES, hourAngle, minuteAngle - SWEEP_DEGREES, minuteAngle)
        }

        /** Nudges the minute hand on and back, once. */
        fun nudge() {
            if (!isAttachedToWindow || !isShown) return
            val hour = hourAngle
            val minute = minuteAngle
            motion?.cancel()
            motion = ExpressiveSpring.DEFAULT_SPATIAL.animator(
                update = { seconds -> place(hour, minute + NUDGE_DEGREES * ExpressiveSpring.DEFAULT_SPATIAL.kickAt(seconds)) },
                end = { place(hour, minute) },
            ).apply { start() }
        }

        private fun spring(spring: ExpressiveSpring, hourFrom: Float, hourTo: Float, minuteFrom: Float, minuteTo: Float) {
            motion?.cancel()
            motion = spring.animator(
                update = { seconds ->
                    val t = spring.valueAt(seconds)
                    place(hourFrom + (hourTo - hourFrom) * t, minuteFrom + (minuteTo - minuteFrom) * t)
                },
                end = { place(hourTo % FULL_TURN, minuteTo % FULL_TURN) },
            ).apply { start() }
        }

        private fun place(hour: Float, minute: Float) {
            hourAngle = hour
            minuteAngle = minute
            invalidate()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            motion?.end()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val side = diameter.toInt() + 2 * ink.inset
            setMeasuredDimension(resolveSize(side, widthMeasureSpec), resolveSize(side, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val side = min(width, height) - 2f * ink.inset
            box.set((width - side) / 2, (height - side) / 2, (width + side) / 2, (height + side) / 2)
            paint.color = ink.color
            Dial.draw(canvas, box, paint, hourAngle, minuteAngle, ink)
        }

        private companion object {
            const val FULL_TURN = 360f
            const val SWEEP_DEGREES = 30f
            const val NUDGE_DEGREES = 8f

            /** [to], turned on by whole turns until it is at or ahead of [from]. */
            fun forward(from: Float, to: Float): Float = generateSequence(to) { it + FULL_TURN }.first { it >= from }
        }
    }

    /** How the dial is drawn, by the face and by its thumbnail alike. */
    private object Dial {

        private val hand = Path()
        private val turn = Matrix()

        /**
         * Draws the dial in [box], its hands at [hourAngle] and [minuteAngle] degrees from twelve: its ticks with [paint] at a
         * lower alpha, fading as [ink] goes into outline, and its hands through [ink],
         * or with [paint] where there is none.
         */
        fun hourAngle(hour: Int, minute: Int): Float = (hour % 12) * 30f + minute / 2f

        fun minuteAngle(minute: Int): Float = minute * 6f

        fun draw(canvas: Canvas, box: RectF, paint: Paint, hourAngle: Float, minuteAngle: Float, ink: DozeInk?) {
            val radius = box.width() / 2
            val cx = box.centerX()
            val cy = box.centerY()
            val tickAlpha = TICK_ALPHA * (1f - (ink?.outline ?: 0f))
            if (tickAlpha > 0f) {
                val color = paint.color
                paint.color = DozeInk.withAlpha(color, tickAlpha)
                paint.style = Paint.Style.FILL
                repeat(TICKS) { index ->
                    val major = index % (TICKS / 4) == 0
                    val length = radius * if (major) MAJOR_TICK else MINOR_TICK
                    val width = radius * TICK_WIDTH
                    canvas.save()
                    canvas.rotate(index * 360f / TICKS, cx, cy)
                    canvas.drawRoundRect(cx - width / 2, box.top, cx + width / 2, box.top + length, width / 2, width / 2, paint)
                    canvas.restore()
                }
                paint.color = color
            }
            drawHand(canvas, cx, cy, radius * HOUR_LENGTH, radius * HOUR_WIDTH, hourAngle, paint, ink)
            drawHand(canvas, cx, cy, radius * MINUTE_LENGTH, radius * MINUTE_WIDTH, minuteAngle, paint, ink)
            hand.reset()
            hand.addCircle(cx, cy, radius * HUB, Path.Direction.CW)
            ink?.draw(canvas, hand) ?: canvas.drawPath(hand, paint)
        }

        private fun drawHand(canvas: Canvas, cx: Float, cy: Float, length: Float, width: Float, angle: Float, paint: Paint, ink: DozeInk?) {
            hand.reset()
            hand.addRoundRect(cx - width / 2, cy - length, cx + width / 2, cy + width / 2, width / 2, width / 2, Path.Direction.CW)
            turn.setRotate(angle, cx, cy)
            hand.transform(turn)
            ink?.draw(canvas, hand) ?: canvas.drawPath(hand, paint)
        }

        const val TICKS = 12
        const val TICK_ALPHA = 0.5f
        const val MAJOR_TICK = 0.12f
        const val MINOR_TICK = 0.06f
        const val TICK_WIDTH = 0.035f
        const val HOUR_LENGTH = 0.52f
        const val HOUR_WIDTH = 0.085f
        const val MINUTE_LENGTH = 0.84f
        const val MINUTE_WIDTH = 0.055f
        const val HUB = 0.06f
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.042f, weight = 600)

        /** The dial's diameter, against the screen's short side. */
        const val SMALLEST = 0.56f
        const val LARGEST = 0.84f
        const val SMALL_FACE = 0.2f
        const val DATE_GAP = 0.05f
        const val THUMBNAIL_SHARE = 0.8f
    }
}
