package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The lock screen photo on the always-on display, behind one clock's lock
 * screen: dimmed, in greys, or as dots ([AodWallpaperStyle]), of only the
 * photo's subject where the depth effect has found one.
 *
 * SystemUI blacks the photo out for the always-on display with scrims beneath
 * the keyguard, so this is a view at the back of the keyguard itself, over
 * those scrims, placed as the window manager places the wallpaper
 * ([LockWallpaperFeed]). It shows only as the display dozes, as far as it has
 * dozed, so it fades in as the lock screen goes dark and out as it wakes while
 * the real photo is revealed beneath it. One GPU shader draws every style.
 *
 * An always-on display lights the same pixels for hours, so everything moves
 * a little each minute ([burnInShift]): the photo drifts within a few pixels,
 * and the dots' grid steps through its cell, so no pixel stays lit.
 */
internal class AodWallpaperLayer(
    private val clock: ClockControllerAdapter,
    private val feed: LockWallpaperFeed,
    private val look: () -> AodWallpaperLook?,
) {

    private val anchor = clock.largeFace.view
    private val view = PhotoView(anchor.context)
    private val main = Handler(Looper.getMainLooper())
    private var attachedTree: ViewTreeObserver? = null
    private var shownMinute = -1L

    private val follow = ViewTreeObserver.OnPreDrawListener {
        place()
        true
    }

    init {
        anchor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = listen()
            override fun onViewDetachedFromWindow(view: View) = stopListening()
        })
        if (anchor.isAttachedToWindow) listen()
    }

    /** Draws again, as the photo, its placement or the look has changed. */
    fun invalidate() {
        anchor.invalidate()
        view.invalidate()
    }

    private fun listen() {
        stopListening()
        attachedTree = anchor.viewTreeObserver.also { it.addOnPreDrawListener(follow) }
    }

    /** Stops following the clock; the view is taken out once the parent has finished detaching its children. */
    private fun stopListening() {
        attachedTree?.takeIf { it.isAlive }?.removeOnPreDrawListener(follow)
        attachedTree = null
        main.post { if (attachedTree == null) (view.parent as? ViewGroup)?.removeView(view) }
    }

    private fun place() {
        val parent = anchor.parent as? ViewGroup
        val current = look()
        val doze = clock.faces.maxOf { it.dozeFraction }
        if (parent == null || current == null || !coversScreen(parent)) {
            (view.parent as? ViewGroup)?.removeView(view)
            return
        }
        if (view.parent !== parent || parent.indexOfChild(view) != 0) {
            (view.parent as? ViewGroup)?.removeView(view)
            // Sized in pixels: SystemUI's constraint sets turn a child's match-parent size without constraints into none.
            parent.addView(view, 0, ViewGroup.LayoutParams(parent.width, parent.height))
        } else if (view.layoutParams.width != parent.width || view.layoutParams.height != parent.height) {
            view.layoutParams = view.layoutParams.apply {
                width = parent.width
                height = parent.height
            }
        }
        view.look = current
        val visibility = if (doze > 0f) View.VISIBLE else View.INVISIBLE
        if (view.visibility != visibility) view.visibility = visibility
        if (view.alpha != doze) view.alpha = doze

        val minute = System.currentTimeMillis() / MS_PER_MINUTE
        if (minute != shownMinute) {
            shownMinute = minute
            view.invalidate()
        }
    }

    /** Whether [parent] is the lock screen itself, as big as the screen, rather than a scaled-down preview of it. */
    private fun coversScreen(parent: ViewGroup): Boolean {
        val screen = parent.resources.displayMetrics
        return parent.width >= screen.widthPixels * SCREEN_SHARE && parent.height >= screen.heightPixels * SCREEN_SHARE
    }

    /** The photo, drawn through [SHADER] with the current look. */
    private inner class PhotoView(context: Context) : View(context) {

        var look: AodWallpaperLook? = null
            set(value) {
                if (field != value) invalidate()
                field = value
            }

        private val shader = RuntimeShader(SHADER)
        private val paint = Paint().apply { shader = this@PhotoView.shader }
        private val cell = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOT_CELL_DP, resources.displayMetrics)
        private val drift = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRIFT_DP, resources.displayMetrics)

        init {
            // SystemUI's constraint sets need every child of the keyguard to have an id.
            id = generateViewId()
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onDraw(canvas: Canvas) {
            val current = look ?: return
            val placed = feed.place(WallpaperPlacement.Size(width, height), current.surface)
            shader.setInputShader("photo", placedShader(current.picture, placed))
            val subject = current.subject
            shader.setInputShader("subject", if (subject != null) placedShader(subject, placed) else NOTHING)
            shader.setFloatUniform("hasSubject", if (subject != null) 1f else 0f)
            val (shiftX, shiftY) = burnInShift(System.currentTimeMillis() / MS_PER_MINUTE, current.style)
            shader.setFloatUniform("shift", shiftX, shiftY)
            shader.setFloatUniform("brightness", current.brightness)
            shader.setFloatUniform("style", current.style.value.toFloat())
            shader.setFloatUniform("cell", cell)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        /** [picture], of the whole surface at any size, placed on the screen as the wallpaper is. */
        private fun placedShader(picture: Bitmap, placed: WallpaperPlacement.Placed): Shader {
            // The picture's pixels onto the screen's: stretched to the surface, then placed as the wallpaper is.
            val placing = Matrix()
            placing.setScale(placed.width / picture.width, placed.height / picture.height)
            placing.postTranslate(placed.left, placed.top)
            placing.postScale(placed.scale, placed.scale, placed.pivotX, placed.pivotY)
            return BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(placing) }
        }

        /**
         * How far everything is moved in [minute], so no pixel is lit for long. Dots
         * step their grid through a cell, a different pixel each minute, in two
         * directions at different rates; the photo drifts around a small circle.
         */
        private fun burnInShift(minute: Long, style: AodWallpaperStyle): Pair<Float, Float> =
            if (style == AodWallpaperStyle.DOTS) {
                val steps = cell.roundToInt().coerceAtLeast(1)
                ((minute * DOT_STEP_X) % steps).toFloat() to ((minute * DOT_STEP_Y) % steps).toFloat()
            } else {
                val turn = minute * DRIFT_RADIANS_PER_MINUTE
                (drift * cos(turn)).roundToInt().toFloat() to (drift * sin(turn * DRIFT_Y_RATE)).roundToInt().toFloat()
            }
    }

    private companion object {
        /** Stands in for the subject where there is none; the shader ignores it. */
        val NOTHING: Shader = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        const val SCREEN_SHARE = 0.9f
        const val MS_PER_MINUTE = 60_000L

        /** A dot's cell: dots are laid on a grid of these, sparse enough to light few pixels. */
        const val DOT_CELL_DP = 5f

        /** Pixels the dot grid steps across and down each minute; prime to the cell, so every offset comes round. */
        const val DOT_STEP_X = 7
        const val DOT_STEP_Y = 4

        /** Farthest the photo drifts from its place, and how fast. */
        const val DRIFT_DP = 3f
        const val DRIFT_RADIANS_PER_MINUTE = 0.4
        const val DRIFT_Y_RATE = 1.3

        /**
         * Every style of the always-on wallpaper. The photo's colours are multiplied
         * by the brightness, or made grey first; dots sample the photo at each cell's
         * middle and light a disc there that grows with the photo's lightness.
         */
        val SHADER = """
            uniform shader photo;
            uniform shader subject;
            uniform float hasSubject;
            uniform float brightness;
            uniform float style;
            uniform float cell;
            uniform float2 shift;

            half lightness(half3 c) { return dot(c, half3(0.2126, 0.7152, 0.0722)); }

            half4 main(float2 at) {
                if (style > 2.5) {
                    float2 centre = (floor((at - shift) / cell) + 0.5) * cell + shift;
                    // Contrast is stretched and the dot's width follows lightness, so its area follows its square:
                    // mid-greys give small dots and dark parts none, keeping few pixels lit. Where the photo
                    // has a subject, only the subject is dotted.
                    float light = smoothstep(0.3, 0.95, float(lightness(photo.eval(centre).rgb)));
                    if (hasSubject > 0.5) light *= float(subject.eval(centre).a);
                    float radius = light * cell * 0.36;
                    half lit = half(clamp(radius - distance(at, centre) + 0.5, 0.0, 1.0)) * half(brightness);
                    return half4(lit, lit, lit, 1.0);
                }
                half3 c = photo.eval(at - shift).rgb;
                if (style > 1.5) c = half3(lightness(c));
                return half4(c * half(brightness), 1.0);
            }
        """.trimIndent()
    }
}

/**
 * What the always-on wallpaper shows: the [picture] of the photo stretched to [surface], in [style], at
 * [brightness], and, as dots, only the photo's [subject] where there is one (a cut-out of the same surface).
 */
internal data class AodWallpaperLook(
    val picture: Bitmap,
    val surface: WallpaperPlacement.Size,
    val style: AodWallpaperStyle,
    val brightness: Float,
    val subject: Bitmap?,
)
