package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

import java.util.Collections
import java.util.WeakHashMap

import kotlin.math.abs

import my.github.MrxSiN.pixellockscreenevolved.motion.ExpressiveSpring
import my.github.MrxSiN.pixellockscreenevolved.motion.SystemMotion

/**
 * Stacks SystemUI's date row and smartspace card under a large clock of this
 * module's on Android 17's composed lock screen, on the always-on display too,
 * as the clock's design asks ([ModuleClockFaces]).
 *
 * The scene lays both out above the clock, and this module pins its large
 * clock under the status bar or lower down ([ClockFacePlacement]), so they overlapped. Every
 * large face writes its own date, so beside one the row is hidden and the card
 * is moved under the clock, spaced as the lock screen's rows are
 * ([LockScreenInsets]), every frame the scene moves anything ([SceneFrames]);
 * with a small clock both go back to where the scene put them. Their holders
 * are let draw them outside their own bounds, where they now stand.
 */
internal class ComposedSmartspacePlacement {

    private val followed = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val hiddenRows = WeakHashMap<View, Int>()
    private val glides = WeakHashMap<View, Glide>()

    /**
     * A card's way to a place far from where it stands, as when the clock
     * changes size and the card's place moves by the height of a face: it
     * glides there on the expressive spatial spring rather than jumping, and
     * follows its place one to one again once there.
     */
    private class Glide {
        val spring = SpringFollower(GLIDE_STIFFNESS)
        var gliding = false
        var lastFrameNanos = 0L
    }

    // Scratch for the placement run every frame, on the main thread.
    private val clockBounds = RectF()
    private val toWindow = Matrix()
    private val values = FloatArray(MATRIX_VALUES)
    private val origin = FloatArray(2)

    /** Starts keeping [card] under the large clock, once per card, and room for it there ([LockScreenFloor]). */
    fun followCard(card: View) {
        LockScreenFloor.followCard(card)
        follow(card) { placeCard(card) }
    }

    /** Starts hiding the date and weather [row] beside a large clock that writes its own date, once per row. */
    fun followRow(row: View) = follow(row) { placeRow(row) }

    private fun follow(view: View, place: () -> Unit) {
        if (!followed.add(view)) return
        val frames = SceneFrames(view, place)
        BeforeEachDraw.follow(view) {
            place()
            frames.start()
        }
        view.post(place)
    }

    private fun placeRow(row: View) {
        if (!row.isAttachedToWindow) return
        if (ModuleClockFaces.largeIn(row.rootView) != null) {
            // Translating a Compose child can be clipped by its interop holder, so the row is hidden in place.
            if (row.visibility == View.VISIBLE && !hiddenRows.containsKey(row)) {
                hiddenRows[row] = row.visibility
                fade(row, show = false)
            }
            return
        }
        hiddenRows.remove(row)?.let { original ->
            if (original == View.VISIBLE) fade(row, show = true) else row.visibility = original
        }
    }

    private fun placeCard(card: View) {
        moveUnder(card, ModuleClockFaces.largeIn(card.rootView), LockScreenInsets.cardGap(card.context))
    }

    /** Fades the date [row] out, then hides it, or shows it and fades it in, as the clock beside it changes size. */
    private fun fade(row: View, show: Boolean) {
        row.animate().cancel()
        if (show) {
            row.alpha = 0f
            row.visibility = View.VISIBLE
            row.animate().alpha(1f).setDuration(ROW_FADE_MS).start()
        } else {
            row.animate().alpha(0f).setDuration(ROW_FADE_MS).withEndAction {
                if (hiddenRows.containsKey(row)) row.visibility = View.INVISIBLE
                row.alpha = 1f
            }.start()
        }
    }

    /** Moves [view] so its top sits [gap] pixels under [clock]'s bottom, in window pixels, or back to its place where there is none. */
    private fun moveUnder(view: View, clock: View?, gap: Int) {
        val target = if (clock == null || view.height == 0) 0f else offset(view, ViewBounds.inWindow(clock, clockBounds).bottom + gap)
        val moved = glide(view, target)
        if (view.translationY != moved) view.translationY = moved
        (view.parent as? ViewGroup)?.takeIf { it.clipChildren }?.clipChildren = false
    }

    /** Where [view] stands this frame on its way to [target]: there at once, unless that is a leap. */
    private fun glide(view: View, target: Float): Float {
        val glide = glides[view] ?: Glide().also { glides[view] = it }
        val now = System.nanoTime()
        val seconds = if (glide.lastFrameNanos == 0L) 0f else ((now - glide.lastFrameNanos) / NANOS_PER_SECOND).coerceAtMost(LONGEST_FRAME_SECONDS)
        glide.lastFrameNanos = now
        val leap = abs(target - view.translationY) > LEAP_DP * view.resources.displayMetrics.density
        if (!glide.gliding) {
            if (!leap || !view.isShown || SystemMotion.isReduced) return target
            glide.gliding = true
            glide.spring.jumpTo(view.translationY)
        }
        val at = glide.spring.follow(target, seconds)
        if (abs(at - target) < SETTLED_PX) glide.gliding = false
        return if (glide.gliding) at else target
    }

    /** How far [view] moves down, in its parent's pixels, for its top to land on [wanted] in the window. */
    private fun offset(view: View, wanted: Float): Float {
        ViewBounds.toWindow(view, toWindow).getValues(values)
        val scale = values[Matrix.MSCALE_Y]
        if (!(scale > 0f)) return 0f
        origin[0] = 0f
        origin[1] = 0f
        toWindow.mapPoints(origin)
        val top = origin[1]
        val laidOutTop = top - view.translationY * scale
        return (wanted - laidOutTop) / scale
    }

    private companion object {
        const val MATRIX_VALUES = 9

        /** The expressive scheme's default spatial stiffness ([ExpressiveSpring.DEFAULT_SPATIAL]). */
        const val GLIDE_STIFFNESS = 380f

        /** A move further than this in one frame is a leap, glided rather than jumped. */
        const val LEAP_DP = 24f
        const val SETTLED_PX = 0.5f
        const val NANOS_PER_SECOND = 1_000_000_000f
        const val LONGEST_FRAME_SECONDS = 0.05f

        /** As the clock sheet fades its content, and SystemUI fades the row beside a Pixel clock. */
        const val ROW_FADE_MS = 150L
    }
}
