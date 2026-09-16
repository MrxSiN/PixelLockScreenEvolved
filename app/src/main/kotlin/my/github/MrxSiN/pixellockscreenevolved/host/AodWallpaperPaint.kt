package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.TypedValue

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * How the always-on display draws the lock screen photo: dimmed, in greys, or as
 * sparse dots ([AodWallpaperStyle]), through one GPU shader.
 *
 * Kept apart from the view that shows it ([AodWallpaperLayer]) because the depth
 * effect's subject is drawn in front of the clock, over that view, and has to lay
 * the same look over itself ([DepthLayer]); drawn in only one of the two the
 * always-on wallpaper stopped at the subject's edges, and a box showed around the
 * time as the display woke.
 *
 * An always-on display lights the same pixels for hours, so everything moves a
 * little each minute ([burnInShift]): the photo drifts within a few pixels, and
 * the dots' grid steps through its cell, so no pixel stays lit.
 */
internal class AodWallpaperPaint(resources: Resources) {

    private val shader = RuntimeShader(SHADER)
    private val whole = Paint().apply { shader = this@AodWallpaperPaint.shader }
    private val atop = Paint().apply {
        shader = this@AodWallpaperPaint.shader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val cell = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOT_CELL_DP, resources.displayMetrics)
    private val drift = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRIFT_DP, resources.displayMetrics)

    /** The minute everything is placed for; a drawing made in an earlier one no longer matches. */
    fun minute(): Long = System.currentTimeMillis() / MS_PER_MINUTE

    /** Draws [look], [placed] over [width] by [height] of [canvas], over whatever [canvas] already holds. */
    fun draw(canvas: Canvas, look: AodWallpaperLook, width: Int, height: Int, placed: WallpaperPlacement.Placed) {
        paint(canvas, look, width, height, placed, whole)
    }

    /**
     * Lays [look] over only what [canvas] has already drawn, [alpha] of the way in,
     * from 0 to 255, as the always-on wallpaper lies that far over the photo. Needs
     * a canvas of its own, a hardware layer or a saved one, or it covers what was
     * drawn behind the caller too.
     */
    fun drawAtop(canvas: Canvas, look: AodWallpaperLook, width: Int, height: Int, placed: WallpaperPlacement.Placed, alpha: Int) {
        paint(canvas, look, width, height, placed, atop.apply { this.alpha = alpha })
    }

    private fun paint(canvas: Canvas, look: AodWallpaperLook, width: Int, height: Int, placed: WallpaperPlacement.Placed, paint: Paint) {
        shader.setInputShader("photo", placedShader(look.picture, placed))
        val subject = look.subject
        shader.setInputShader("subject", if (subject != null) placedShader(subject, placed) else NOTHING)
        shader.setFloatUniform("hasSubject", if (subject != null) 1f else 0f)
        val (shiftX, shiftY) = burnInShift(minute(), look.style)
        shader.setFloatUniform("shift", shiftX, shiftY)
        shader.setFloatUniform("brightness", BRIGHTNESS)
        shader.setFloatUniform("style", look.style.value.toFloat())
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

    private companion object {
        /** Stands in for the subject where there is none; the shader ignores it. */
        val NOTHING: Shader = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        const val MS_PER_MINUTE = 60_000L

        /**
         * How much of the photo's own light the always-on display keeps: the whole photo is
         * dimmed to this, and dots, which are lit nearly fully, spend it on their width instead.
         */
        const val BRIGHTNESS = 0.25f

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
         * middle and light a disc there that grows with the photo's lightness and with
         * the brightness, which they spend on width rather than on grey, and that
         * lights nothing at all where it would be narrower than a pixel.
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
                    // Dots are lit nearly as brightly as the clock beside them, a little under it so the
                    // two still tell apart: the always-on display holds greys unsteadily and dimmed dots
                    // flickered. The brightness is spent on their width instead, by its square root, so
                    // the light asked for and the power drawn are the same.
                    float radius = light * cell * 0.36 * sqrt(brightness);
                    // A dot narrower than a pixel lights nothing. Without this the half pixel every disc
                    // covers at its own middle is lit wherever the photo is, so a grid showed over the
                    // whole screen, over the black around the subject and behind the clock.
                    if (radius < 0.5) return half4(0.0, 0.0, 0.0, 1.0);
                    half lit = half(clamp(radius - distance(at, centre) + 0.5, 0.0, 1.0)) * 0.8;
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
 * What the always-on wallpaper shows: the [picture] of the photo stretched to [surface], in [style],
 * and, as dots, only the photo's [subject] where there is one (a cut-out of the same surface).
 */
internal data class AodWallpaperLook(
    val picture: Bitmap,
    val surface: WallpaperPlacement.Size,
    val style: AodWallpaperStyle,
    val subject: Bitmap?,
)
