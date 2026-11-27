package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The photo in the subject editor ([DepthMaskEditor]), painted by one finger and moved by two:
 * a pinch zooms about the fingers, two fingers drag it, and a quick two-finger tap undoes. A
 * stroke a second finger joins soon after it began is taken back, so starting a pinch never
 * paints. While a finger paints, a loupe shows what it covers. It opens fitted to [frame], the
 * part of the photo the lock screen shows, when that is known. Held [comparing], it shows the
 * subject as found, before any edits, so a person can see what they changed.
 */
@SuppressLint("ViewConstructor")
internal class DepthEditCanvas(
    context: Context,
    private val photo: Bitmap,
    private val edits: SubjectEdits,
    /** The subject as found, before any edits, one value from 0 to 255 per pixel of [photo]. */
    found: IntArray,
    /** The part of [photo] the lock screen shows, in its pixels, or null where it is not known. */
    private val frame: RectF?,
    private val palette: EditorPalette,
) : View(context) {

    /** How the subject is shown over the photo. */
    enum class Look { SUBJECT, OVERLAY, PHOTO }

    var look = Look.SUBJECT
        set(value) {
            field = value
            invalidate()
        }

    /** The mark a stroke leaves ([DepthComposite]). */
    var mark = DepthComposite.ADDED

    /** The colour of the brush's ring, so the tool in hand reads at a glance. */
    var brushColor = Color.WHITE
        set(value) {
            field = value
            ringLight.color = value
            invalidate()
        }

    /** Shows the subject as found instead of as edited, while a person holds the compare button. */
    var comparing = false
        set(value) {
            field = value
            invalidate()
        }

    /** The brush's radius on screen, in pixels. */
    var brushRadius = 0f
        set(value) {
            field = value
            invalidate()
        }

    /** Shows the brush's size in the middle of the photo, as its slider is dragged. */
    var showsBrush = false
        set(value) {
            field = value
            invalidate()
        }

    /** Hears each change of zoom, as the zoom against the fitted view, 1 there. */
    var onZoom: (Float) -> Unit = {}

    /** Hears a quick two-finger tap. */
    var onTwoFingerTap: () -> Unit = {}

    /** Hears each stroke ending, so undo and redo can be offered. */
    var onStroke: () -> Unit = {}

    private val density = resources.displayMetrics.density
    private val mask = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ALPHA_8)
    private val original = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ALPHA_8).apply {
        setPixels(IntArray(found.size) { found[it] shl 24 }, 0, photo.width, 0, 0, photo.width, photo.height)
    }
    private val row = IntArray(photo.width)

    // Where the photo is: its pixels scaled by [scale], its top left corner at ([tx], [ty]).
    private var scale = 1f
    private var tx = 0f
    private var ty = 0f
    private var moved = false
    private val box = RectF()
    private val toView = Matrix()
    private val loupeMatrix = Matrix()
    private val photoBounds = RectF()

    private var gesture = Gesture.NONE
    private var strokeAt = 0L
    private var fingerX = 0f
    private var fingerY = 0f
    private var startDistance = 1f
    private var startMidX = 0f
    private var startMidY = 0f
    private var startScale = 1f
    private var startTx = 0f
    private var startTy = 0f
    private var tapAt = 0L
    private var tapMoved = false
    private var settling: ValueAnimator? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private enum class Gesture { NONE, PAINT, TRANSFORM, DONE }

    private val plain = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dimmed = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setSaturation(DIM_SATURATION)
            postConcat(ColorMatrix().apply { setScale(DIM_LIGHT, DIM_LIGHT, DIM_LIGHT, 1f) })
        })
    }
    private val keepSubject = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val tint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = palette.overlay; alpha = OVERLAY_ALPHA }
    private val outside = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(OUTSIDE_ALPHA, 0, 0, 0) }
    private val frameLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(FRAME_ALPHA, 255, 255, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(FRAME_ALPHA, 255, 255, 255)
        textSize = 12f * density
        typeface = palette.medium
        letterSpacing = 0.04f
    }
    private val ringLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
    }
    private val ringDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = Color.argb(0x66, 0, 0, 0)
    }
    private val loupeRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.WHITE
        setShadowLayer(8f * density, 0f, 2f * density, Color.argb(0x80, 0, 0, 0))
    }
    private val loupeBack = Paint().apply { color = Color.BLACK }
    private val clip = Path()
    private val outsidePath = Path()
    private val ease = PathInterpolator(0.2f, 0f, 0f, 1f)

    init {
        refresh(0, 0, photo.width, photo.height)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        contentDescription = "Photo with its subject; paint to change the subject, pinch to zoom"
    }

    /** Sets the room the photo is shown in, between the editor's bars; the photo is fitted to it until a person moves it. */
    fun setBox(left: Float, top: Float, right: Float, bottom: Float) {
        if (box.left == left && box.top == top && box.right == right && box.bottom == bottom) return
        box.set(left, top, right, bottom)
        // Fitted again only until a person has moved the photo: the room changes as the window's insets arrive.
        if (!moved) home(animate = false) else settle()
    }

    /** Redraws the subject within the box from ([left], [top]) to ([right], [bottom]) of the photo, as [edits] changed it. */
    fun refresh(left: Int, top: Int, right: Int, bottom: Int) {
        val width = photo.width
        for (y in top until bottom) {
            for (x in left until right) row[x - left] = edits.alphaAt(y * width + x) shl 24
            mask.setPixels(row, 0, right - left, left, y, right - left, 1)
        }
        invalidate()
    }

    /** Fits the lock screen's part of the photo, or the whole photo, back into the room. */
    fun home(animate: Boolean = true) {
        if (box.isEmpty) return
        val target = frame ?: RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
        val margin = HOME_MARGIN * density
        val s = min((box.width() - 2 * margin) / target.width(), (box.height() - 2 * margin) / target.height())
        val x = box.centerX() - target.centerX() * s
        val y = box.centerY() - target.centerY() * s
        if (animate) settleTo(s, x, y) else place(s, x, y)
    }

    fun release() {
        settling?.cancel()
        mask.recycle()
        original.recycle()
    }

    private val homeScale: Float
        get() {
            val target = frame ?: RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
            val margin = HOME_MARGIN * density
            return min((box.width() - 2 * margin) / target.width(), (box.height() - 2 * margin) / target.height())
        }

    private val fitScale: Float get() = min(box.width() / photo.width, box.height() / photo.height)
    private val leastScale: Float get() = min(fitScale, homeScale)
    private val mostScale: Float get() = fitScale * MOST_ZOOM

    private fun place(s: Float, x: Float, y: Float) {
        scale = s
        tx = x
        ty = y
        onZoom(scale / homeScale)
        invalidate()
    }

    /** Eases the photo to a place inside the room: zoomed no further out than fitted, nor in than [MOST_ZOOM], and never pulled off its room. */
    private fun settle() {
        val s = scale.coerceIn(leastScale, mostScale)
        // Kept about the room's middle as the zoom comes back into range.
        var x = box.centerX() - (box.centerX() - tx) * s / scale
        var y = box.centerY() - (box.centerY() - ty) * s / scale
        x = clampEdge(x, photo.width * s, box.left, box.right)
        y = clampEdge(y, photo.height * s, box.top, box.bottom)
        if (s != scale || x != tx || y != ty) settleTo(s, x, y)
    }

    /** Where an edge at [at] of a span [size] long may be in a room from [from] to [to]: centred if it fits, otherwise covering it. */
    private fun clampEdge(at: Float, size: Float, from: Float, to: Float): Float =
        if (size <= to - from) from + (to - from - size) / 2f else at.coerceIn(to - size, from)

    private fun settleTo(s: Float, x: Float, y: Float) {
        settling?.cancel()
        val fromS = scale
        val fromX = tx
        val fromY = ty
        settling = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SETTLE_MS
            interpolator = ease
            addUpdateListener {
                val t = it.animatedValue as Float
                place(fromS + (s - fromS) * t, fromX + (x - fromX) * t, fromY + (y - fromY) * t)
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        toView.setScale(scale, scale)
        toView.postTranslate(tx, ty)
        drawPhoto(canvas, toView)
        frame?.let { drawFrame(canvas, it) }
        when {
            gesture == Gesture.PAINT -> {
                drawRing(canvas, fingerX, fingerY, brushRadius)
                drawLoupe(canvas)
            }
            showsBrush -> drawRing(canvas, box.centerX(), box.centerY(), brushRadius)
        }
    }

    /** The photo through [matrix], with the subject shown as [look] asks. */
    private fun drawPhoto(canvas: Canvas, matrix: Matrix) {
        val mask = if (comparing) original else mask
        when (look) {
            Look.PHOTO -> canvas.drawBitmap(photo, matrix, plain)
            Look.OVERLAY -> {
                canvas.drawBitmap(photo, matrix, plain)
                canvas.drawBitmap(mask, matrix, tint)
            }
            Look.SUBJECT -> {
                canvas.drawBitmap(photo, matrix, dimmed)
                photoBounds.set(0f, 0f, photo.width.toFloat(), photo.height.toFloat())
                matrix.mapRect(photoBounds)
                val saved = canvas.saveLayer(photoBounds, null)
                canvas.drawBitmap(photo, matrix, plain)
                canvas.drawBitmap(mask, matrix, keepSubject)
                canvas.restoreToCount(saved)
            }
        }
    }

    /** The lock screen's part of the photo: what lies outside it darkened, its edge dashed, and named. */
    private fun drawFrame(canvas: Canvas, frame: RectF) {
        photoBounds.set(frame)
        toView.mapRect(photoBounds)
        val corner = FRAME_CORNER * density * scale / homeScale
        outsidePath.rewind()
        outsidePath.fillType = Path.FillType.EVEN_ODD
        outsidePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        outsidePath.addRoundRect(photoBounds, corner, corner, Path.Direction.CW)
        canvas.drawPath(outsidePath, outside)
        canvas.drawRoundRect(photoBounds, corner, corner, frameLine)
        val text = "LOCK SCREEN"
        val y = photoBounds.top - 8f * density
        if (y > box.top + label.textSize) canvas.drawText(text, photoBounds.left + 4f * density, y, label)
    }

    private fun drawRing(canvas: Canvas, x: Float, y: Float, radius: Float) {
        canvas.drawCircle(x, y, radius, ringDark)
        canvas.drawCircle(x, y, radius, ringLight)
    }

    /** A circle in a top corner, away from the finger, showing what lies under it [LOUPE_ZOOM] times nearer. */
    private fun drawLoupe(canvas: Canvas) {
        val radius = LOUPE_RADIUS * density
        val margin = 16f * density
        val onLeft = !(fingerX < box.centerX() && fingerY < box.top + 2 * radius + 2 * margin)
        val cx = if (onLeft) box.left + margin + radius else box.right - margin - radius
        val cy = box.top + margin + radius
        // The photo's point under the finger, at the loupe's middle.
        val px = (fingerX - tx) / scale
        val py = (fingerY - ty) / scale
        val s = scale * LOUPE_ZOOM
        loupeMatrix.setScale(s, s)
        loupeMatrix.postTranslate(cx - px * s, cy - py * s)
        val saved = canvas.save()
        clip.rewind()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawCircle(cx, cy, radius, loupeBack)
        drawPhoto(canvas, loupeMatrix)
        drawRing(canvas, cx, cy, brushRadius * LOUPE_ZOOM)
        canvas.restoreToCount(saved)
        canvas.drawCircle(cx, cy, radius, loupeRim)
    }

    private fun photoRadius(): Float = brushRadius / scale

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                settling?.cancel()
                parent?.requestDisallowInterceptTouchEvent(true)
                gesture = Gesture.PAINT
                strokeAt = SystemClock.uptimeMillis()
                fingerX = event.x
                fingerY = event.y
                edits.begin(mark, (event.x - tx) / scale, (event.y - ty) / scale, photoRadius())
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gesture == Gesture.PAINT) {
                    // A finger joining soon after the first is a pinch: what the first painted is taken back.
                    if (SystemClock.uptimeMillis() - strokeAt < PINCH_GRACE_MS) edits.cancel() else edits.end()
                    onStroke()
                }
                if (gesture == Gesture.PAINT || gesture == Gesture.NONE) {
                    gesture = Gesture.TRANSFORM
                    moved = true
                    tapAt = SystemClock.uptimeMillis()
                    tapMoved = false
                }
                if (gesture == Gesture.TRANSFORM) startTransform(event)
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.PAINT -> {
                    for (h in 0..event.historySize) {
                        val x = if (h < event.historySize) event.getHistoricalX(h) else event.x
                        val y = if (h < event.historySize) event.getHistoricalY(h) else event.y
                        edits.moveTo((x - tx) / scale, (y - ty) / scale, photoRadius())
                    }
                    fingerX = event.x
                    fingerY = event.y
                    invalidate()
                }
                Gesture.TRANSFORM -> if (event.pointerCount >= 2) transform(event)
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> if (gesture == Gesture.TRANSFORM) {
                if (!tapMoved && SystemClock.uptimeMillis() - tapAt < TAP_MS) {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onTwoFingerTap()
                }
                // The finger left on the photo does nothing until all are lifted.
                gesture = Gesture.DONE
                settle()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gesture == Gesture.PAINT) {
                    edits.end()
                    onStroke()
                }
                if (gesture == Gesture.TRANSFORM || gesture == Gesture.DONE) settle()
                gesture = Gesture.NONE
                invalidate()
            }
        }
        return true
    }

    private fun startTransform(event: MotionEvent) {
        startDistance = max(1f, hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)))
        startMidX = (event.getX(0) + event.getX(1)) / 2f
        startMidY = (event.getY(0) + event.getY(1)) / 2f
        startScale = scale
        startTx = tx
        startTy = ty
    }

    private fun transform(event: MotionEvent) {
        val distance = max(1f, hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0)))
        val midX = (event.getX(0) + event.getX(1)) / 2f
        val midY = (event.getY(0) + event.getY(1)) / 2f
        if (abs(distance - startDistance) > slop || hypot(midX - startMidX, midY - startMidY) > slop) tapMoved = true
        // Past its range the zoom gives, as a rubber band does, and settles back once the fingers lift.
        val wanted = startScale * distance / startDistance
        val s = when {
            wanted < leastScale -> leastScale * (wanted / leastScale).let { it + (1f - it) * RUBBER }
            wanted > mostScale -> mostScale * (wanted / mostScale).let { 1f + (it - 1f) * (1f - RUBBER) }
            else -> wanted
        }
        // The photo's point that was under the fingers' middle stays under it.
        val px = (startMidX - startTx) / startScale
        val py = (startMidY - startTy) / startScale
        place(s, midX - px * s, midY - py * s)
    }

    private companion object {
        /** How much darker and greyer the photo is around the subject, as 1 is unchanged. */
        const val DIM_SATURATION = 0.15f
        const val DIM_LIGHT = 0.32f

        const val OVERLAY_ALPHA = 120
        const val OUTSIDE_ALPHA = 110
        const val FRAME_ALPHA = 200
        const val FRAME_CORNER = 28f

        const val HOME_MARGIN = 20f

        /** The nearest the photo comes, against fitting it whole. */
        const val MOST_ZOOM = 16f

        /** How firmly a pinch past the zoom's range is held back, from 0 not at all to 1 completely. */
        const val RUBBER = 0.6f

        const val SETTLE_MS = 320L
        const val PINCH_GRACE_MS = 220L
        const val TAP_MS = 250L

        const val LOUPE_RADIUS = 64f
        const val LOUPE_ZOOM = 2f
    }
}
