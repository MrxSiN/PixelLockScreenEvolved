package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.view.View

import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * The lock screen photo on the always-on display, as on iOS, as Wallpaper &
 * style's switches choose ([AodWallpaperStyle]), drawn on SystemUI's reveal
 * scrim behind every lock-screen element. As dots, where the depth effect has
 * found the photo's subject ([onSubject]), only the subject is drawn, lighting
 * fewer pixels still.
 *
 * It comes and goes with the scrim's own reveal: only where the scrim is dark
 * ([ScrimReveal]), so a wake, and a fingerprint unlock straight from the
 * always-on display, sweep it away in the scrim's own shape (the ripple from
 * the sensor, the circle from a tap, the lift) rather than all at once; and over
 * the way in or out ([progress]) the dots grow from the photo itself, cell by
 * cell from the middle of the screen, and shrink back into it.
 *
 * Only small copies are kept, on the GPU: half the screen's size is plenty at
 * the always-on display's brightness. The photo's is kept whatever the style,
 * about 5MB, since SystemUI draws the photo only once, often before the
 * settings have been read, and not again until the photo changes.
 */
internal class KeyguardAodWallpaper(
    private val hooks: Hooks,
    private val feed: LockWallpaperFeed,
    private val logger: Logger,
) : LockWallpaperFeed.Listener, HostPatch {

    private val clocks = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<ClockControllerAdapter, Boolean>()),
    )
    private val main = Handler(Looper.getMainLooper())
    private val minuteTick = Runnable { scrim?.invalidate() }
    private val worker = Executors.newSingleThreadExecutor()
    private var scrim: View? = null
    private var paint: AodWallpaperPaint? = null

    /** The photo's copy and the size of the surface it is stretched to. */
    private class Photo(val picture: Bitmap, val surface: WallpaperPlacement.Size)

    private var photo: Photo? = null
    private var subject: Bitmap? = null
    private var style: AodWallpaperStyle? = null
    private var watching = false

    /** SystemUI's reveal scrim as it last drew, for whatever else follows it ([DepthLayer]); null where it cannot be read. */
    var reveal: ScrimReveal? = null
        private set

    /** Keeps the wallpaper's layer only where the scrim is dark, its gradient as shader. */
    private val keepWhereDark = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }

    /** A dot's cell, in dp, as Wallpaper & style's Dot size chooses ([AodDotDensity]). */
    private var dotCellDp = AodDotDensity.cellDp(AodDotDensity.DEFAULT)

    /** How far in the wallpaper was drawn last ([aodProgress]); on the main thread. */
    private var shownProgress = 0f

    override fun install(classLoader: ClassLoader) {
        runCatching {
            val type = Class.forName(LIGHT_REVEAL_SCRIM, false, classLoader)
            val onDraw = type.getDeclaredMethod("onDraw", Canvas::class.java).apply { isAccessible = true }
            reveal = runCatching { ScrimReveal(type) }
                .onFailure { logger.warn("The scrim's reveal cannot be read; the always-on wallpaper fades with the doze instead", it) }
                .getOrNull()
            hooks.after(onDraw) { target, args -> drawOnScrim(target as View, args[0] as Canvas) }
        }.onFailure { logger.warn("Always-on wallpaper hook is unavailable", it) }
    }

    /** Draws the photo behind a clock SystemUI built, on the always-on display. */
    fun track(clock: ClockControllerAdapter) {
        main.post {
            watch(clock.largeFace.view.context)
            clocks += clock
            invalidate()
        }
    }

    override fun onPhoto(photo: Bitmap, surface: WallpaperPlacement.Size, key: String, context: Context) {
        val copy = smallGpuCopy(photo, context)
        main.post {
            // The old copy is left to the garbage collector: a frame being drawn may still hold it.
            this.photo = Photo(copy, surface)
            invalidate()
        }
    }

    override fun onPlacementChanged() = invalidate()

    /**
     * The depth effect's cut-out of the photo's subject, as big as the wallpaper
     * surface, or null where there is none or the effect is off. Called on the main thread.
     */
    fun onSubject(cutout: Bitmap?, context: Context) {
        if (cutout == null) {
            subject = null
            invalidate()
            return
        }
        worker.execute {
            val copy = smallGpuCopy(cutout, context)
            main.post {
                subject = copy
                invalidate()
            }
        }
    }

    /** What the always-on display draws the photo as now, or null while it draws black. */
    private fun look(): AodWallpaperLook? {
        val current = photo ?: return null
        val chosen = style ?: return null
        return AodWallpaperLook(current.picture, current.surface, chosen, subject.takeIf { chosen == AodWallpaperStyle.DOTS })
    }

    /** [picture] at half the screen's longest side, or smaller, on the GPU. */
    private fun smallGpuCopy(picture: Bitmap, context: Context): Bitmap {
        val longest = context.resources.displayMetrics.let { max(it.widthPixels, it.heightPixels) } * SCREEN_SHARE
        val scale = min(1f, longest / max(picture.width, picture.height))
        val small = Bitmap.createScaledBitmap(picture, (picture.width * scale).roundToInt(), (picture.height * scale).roundToInt(), true)
        val onGpu = small.copy(Bitmap.Config.HARDWARE, false) ?: return small
        if (small !== picture) small.recycle()
        return onGpu
    }

    /** Follows the switches, once. */
    private fun watch(context: Context) {
        if (watching) return
        watching = true
        val resolver: ContentResolver = context.contentResolver
        val follow = {
            style = AodWallpaperStyle.chosen(resolver)
            invalidate()
        }
        AodWallpaperSetting.observe(resolver, follow)
        AodAppearanceSetting.observe(resolver, follow)
        AodDotDensity.observe(resolver) {
            dotCellDp = AodDotDensity.cellDp(AodDotDensity.get(resolver))
            invalidate()
        }
    }

    /**
     * Draws after SystemUI's light-reveal scrim. That scrim sits above the real
     * wallpaper and below lock-screen content, so Compose ownership cannot
     * remove this drawing and the clock, smartspace and unlock affordances stay
     * above it.
     */
    private fun drawOnScrim(target: View, canvas: Canvas) {
        scrim = target
        reveal?.drew(target)
        val doze = synchronized(clocks) {
            clocks.maxOfOrNull { clock -> clock.faces.maxOf { it.dozeFraction } }
        } ?: 0f
        val current = look() ?: return stopMinuteTick()
        val mask = reveal
        val revealed = if (mask == null) Float.NaN else mask.amountOrNaN(target)
        val shown = aodProgress(doze, revealed, shownProgress)
        shownProgress = shown
        if (shown <= 0f || target.width == 0 || target.height == 0) {
            stopMinuteTick()
            return
        }

        val renderer = paint ?: AodWallpaperPaint(target.resources).also { paint = it }
        renderer.cellDp = dotCellDp
        val checkpoint = canvas.saveLayerAlpha(null, (fadeIn(shown) * OPAQUE).roundToInt())
        renderer.draw(
            canvas,
            current,
            target.width,
            target.height,
            feed.place(WallpaperPlacement.Size(target.width, target.height), current.surface),
            shown,
        )
        if (revealed < 1f) runCatching {
            // As the scrim's own draw: dark all over (no gradient) keeps the whole layer.
            mask?.darkness(target)?.let { darkness ->
                keepWhereDark.shader = darkness
                canvas.drawRect(0f, 0f, target.width.toFloat(), target.height.toFloat(), keepWhereDark)
                keepWhereDark.shader = null
            }
        }
        canvas.restoreToCount(checkpoint)
        main.removeCallbacks(minuteTick)
        main.postDelayed(minuteTick, MILLIS_PER_MINUTE - System.currentTimeMillis() % MILLIS_PER_MINUTE)
    }

    private fun stopMinuteTick() = main.removeCallbacks(minuteTick)

    /** The layer's opacity at [progress]: it comes in over the first part of the way, where the dots are still the photo. */
    private fun fadeIn(progress: Float): Float = (progress / FADE_IN_SHARE).coerceIn(0f, 1f)

    private fun invalidate() {
        main.post { scrim?.invalidate() }
    }

    private companion object {
        /** The copies' longest side, against the screen's. */
        const val SCREEN_SHARE = 0.5f
        const val OPAQUE = 255f

        /** The share of the way in over which the layer itself fades up, before its dots take shape. */
        const val FADE_IN_SHARE = 0.2f
        const val MILLIS_PER_MINUTE = 60_000L
        const val LIGHT_REVEAL_SCRIM = "com.android.systemui.statusbar.LightRevealScrim"
    }
}

/**
 * How far into the always-on wallpaper the screen is, from 0 awake to 1 dozing: how dark SystemUI's
 * scrim is ([revealed] 0 dark, 1 clear), once the always-on display is involved at all, that is,
 * while the clock dozes ([doze] above 0) or the wallpaper was still showing on the last frame
 * ([previous]); the doze alone where the scrim cannot be read ([revealed] NaN: a primitive, as it is
 * read on every draw of the scrim, where a `Float?` would allocate).
 *
 * The doze is no measure of the way: on `CP4A.261105.040.A1` it jumps to 1 the moment the screen
 * goes off, while the scrim takes about 400 ms to darken, and following it drew the finished dots,
 * opaque, over the lock screen for the first frames (a blink). A fingerprint unlock drops it to 0
 * at once, while the scrim's reveal is only starting, and the dots vanished in one frame. The scrim
 * is the screen off and the wake as SystemUI draws them.
 */
// ponytail: assumes the scrim darkens whenever the always-on display shows; a build that dozes with a clear scrim would show no wallpaper, fall back to the doze if one appears.
internal fun aodProgress(doze: Float, revealed: Float, previous: Float): Float = when {
    revealed.isNaN() -> doze
    doze <= 0f && previous <= 0f -> 0f
    else -> 1f - revealed
}.coerceIn(0f, 1f)
