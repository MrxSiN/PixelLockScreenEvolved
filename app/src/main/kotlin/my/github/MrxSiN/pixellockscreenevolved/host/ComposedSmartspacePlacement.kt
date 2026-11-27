package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
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
 * large face writes its own date, as does a small face that asks to (iOS), so
 * beside one the row is hidden and the card is moved under the clock, spaced as the lock screen's rows are
 * ([LockScreenInsets]), every frame the scene moves anything ([SceneFrames]);
 * the card measures only as tall as its content there ([SmartspaceCardCrop]);
 * with any other small clock both go back to where the scene put them. Their holders
 * are let draw them outside their own bounds, where they now stand.
 */
internal class ComposedSmartspacePlacement(private val crop: SmartspaceCardCrop) {

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

        /** Where the view's top was drawn the frame before, in the window; NaN before the first. */
        var lastTop = Float.NaN

        /** The view's fade back in once the scene has stopped morphing it ([sitOutMorph]), built once per view. */
        var fadeIn: ValueAnimator? = null
    }

    // Scratch for the placement run every frame, on the main thread.
    private val clockBounds = RectF()
    private val toWindow = Matrix()
    private val values = FloatArray(MATRIX_VALUES)
    private val origin = FloatArray(2)

    /** The space between the clock and the card, in pixels ([LockScreenInsets.cardGap]); -1 before it is read. */
    private var cardGap = -1

    /** Starts keeping [card] under the large clock, once per card, and room for it there ([LockScreenFloor]). */
    fun followCard(card: View) {
        LockScreenFloor.followCard(card)
        crop.follow(card)
        follow(card) { beforeDraw -> placeCard(card, beforeDraw) }
    }

    /**
     * Starts following the card and date rows already in [root]. SystemUI hands the module them
     * only as it builds them, and a hot reload of the module keeps the ones SystemUI built before,
     * so they are found by their ids when the new code's large clock is first shown.
     */
    fun adopt(root: View) {
        find(root, CARD_ID)?.let(::followCard)
        ROW_IDS.forEach { id -> find(root, id)?.let(::followRow) }
    }

    private fun find(root: View, name: String): View? =
        root.resources.getIdentifier(name, "id", root.context.packageName).takeIf { it != 0 }?.let { root.findViewById(it) }

    /**
     * Starts hiding the date and weather [row] beside a large clock that writes its own date, once per row. A hot
     * reload shows the row again if this code hid it: the new code knows nothing of it, and SystemUI keeps the row,
     * so it stayed hidden under the small clock until SystemUI restarted.
     */
    fun followRow(row: View) = follow(row, onRetired = { showRow(row) }) { placeRow(row) }

    /** Places [view] before each draw, on the frame after, and once now; [place] is told whether it runs before a draw. */
    private fun follow(view: View, onRetired: () -> Unit = {}, place: (beforeDraw: Boolean) -> Unit) {
        if (!followed.add(view)) return
        val frames = SceneFrames(view) { place(false) }
        BeforeEachDraw.follow(view, onRetired) {
            place(true)
            frames.start()
        }
        view.post { place(false) }
    }

    private fun placeRow(row: View) {
        if (!row.isAttachedToWindow) return
        if (ModuleClockFaces.datedIn(row.rootView) != null) {
            // Translating a Compose child can be clipped by its interop holder, so the row is taken out instead:
            // gone, its holder measures nothing, and the card and the notifications under it close up, as on iOS.
            // At once, and again whenever the scene shows it anew: faded, the row under the small clock showed
            // for a tenth of a second as a notification arrived.
            if (row.visibility == View.VISIBLE) {
                if (!hiddenRows.containsKey(row)) hiddenRows[row] = View.VISIBLE
                row.animate().cancel()
                row.alpha = 1f
                row.visibility = View.GONE
            }
            return
        }
        hiddenRows.remove(row)?.let { original ->
            if (original == View.VISIBLE) fadeIn(row) else row.visibility = original
        }
    }

    /** Puts [row] back as it was before this code hid it, at once. */
    private fun showRow(row: View) {
        val original = hiddenRows.remove(row) ?: return
        row.animate().cancel()
        row.alpha = 1f
        row.visibility = original
    }

    private fun placeCard(card: View, beforeDraw: Boolean) {
        crop.check(card)
        if (cardGap < 0) cardGap = LockScreenInsets.cardGap(card.context)
        moveUnder(card, ModuleClockFaces.datedIn(card.rootView), cardGap, beforeDraw)
    }

    /** Shows the date [row] again and fades it in, as a clock that writes no date replaces one that does. */
    private fun fadeIn(row: View) {
        row.animate().cancel()
        row.alpha = 0f
        row.visibility = View.VISIBLE
        row.animate().alpha(1f).setDuration(ROW_FADE_MS).start()
    }

    /**
     * Moves [view] so its top sits [gap] pixels under [clock]'s bottom, in window pixels, or back to its place where
     * there is none; [beforeDraw] as for [ViewBounds.inWindow].
     */
    private fun moveUnder(view: View, clock: View?, gap: Int, beforeDraw: Boolean) {
        ViewBounds.toWindow(view, toWindow, beforeDraw).getValues(values)
        val scale = values[Matrix.MSCALE_Y]
        if (!(scale > 0f)) return
        origin[0] = 0f
        origin[1] = 0f
        toWindow.mapPoints(origin)
        // Where the scene lays the view out this frame, without the move made here.
        val laidOutTop = origin[1] - view.translationY * scale
        val placed = clock != null && view.height > 0
        val wantedTop = if (placed) ViewBounds.inWindow(clock, clockBounds, beforeDraw).bottom + gap else laidOutTop
        val state = glides[view] ?: Glide().also { glides[view] = it }
        val top = glide(view, state, wantedTop)
        val moved = (top - laidOutTop) / scale
        if (view.translationY != moved) view.translationY = moved
        sitOutMorph(view, state, placed && view.width > view.rootView.width)
        (view.parent as? ViewGroup)?.takeIf { it.clipChildren }?.clipChildren = false
    }

    /**
     * Hides [view] while the scene [morphing] it, and fades it back in once it rests. As the scene swaps the
     * clock's sizes it morphs the card between where the two put it: it lays the card out wider than the window
     * (about 1580 px against 1248) and scales it down, in the view and in a layer of its own that no view
     * property undoes, to about 0.79, then snaps both back at the end. Under a face of this module's the card
     * does not move between the two, so all that showed was its lines shrinking and snapping back. Hidden with
     * the transition alpha, which the scene's own fades never touch.
     */
    private fun sitOutMorph(view: View, state: Glide, morphing: Boolean) {
        if (morphing) {
            state.fadeIn?.cancel()
            view.transitionAlpha = 0f
            return
        }
        // Also hidden as it is measured wider than the window ([SmartspaceCardCrop]), a frame before this sees it.
        if (view.transitionAlpha >= 1f || state.fadeIn?.isRunning == true) return
        if (!view.isShown || SystemMotion.isReduced) {
            view.transitionAlpha = 1f
            return
        }
        val fade = state.fadeIn ?: ValueAnimator().apply {
            duration = MORPH_FADE_MS
            addUpdateListener { view.transitionAlpha = it.animatedValue as Float }
        }.also { state.fadeIn = it }
        fade.setFloatValues(view.transitionAlpha, 1f)
        fade.start()
    }

    /**
     * Where [view]'s top stands this frame, in the window, on its way to [target]: there at once, unless
     * that is a leap from where it was drawn the frame before, which it glides from. Kept in the window,
     * not in the view's translation: the scene moves the card's holder by hundreds of pixels as the clock
     * changes size, and a glide counted from the holder jumped with it, the card lagging over the
     * notifications for a third of a second.
     */
    private fun glide(view: View, glide: Glide, target: Float): Float {
        val now = System.nanoTime()
        val seconds = if (glide.lastFrameNanos == 0L) 0f else ((now - glide.lastFrameNanos) / NANOS_PER_SECOND).coerceAtMost(LONGEST_FRAME_SECONDS)
        glide.lastFrameNanos = now
        val last = glide.lastTop
        if (!glide.gliding) {
            val leap = !last.isNaN() && abs(target - last) > LEAP_DP * view.resources.displayMetrics.density
            if (!leap || !view.isShown || SystemMotion.isReduced) {
                glide.lastTop = target
                return target
            }
            glide.gliding = true
            glide.spring.jumpTo(last)
        }
        val at = glide.spring.follow(target, seconds)
        if (abs(at - target) < SETTLED_PX) glide.gliding = false
        glide.lastTop = if (glide.gliding) at else target
        return glide.lastTop
    }

    companion object {
        /** SystemUI's smartspace card's view id, as its layouts name it. */
        const val CARD_ID = "bc_smartspace_view"

        /** SystemUI's date and weather rows beside the small and the large clock. */
        private val ROW_IDS = listOf("date_smartspace_view", "date_smartspace_view_large")

        private const val MATRIX_VALUES = 9

        /** The expressive scheme's default spatial stiffness ([ExpressiveSpring.DEFAULT_SPATIAL]). */
        private const val GLIDE_STIFFNESS = 380f

        /** A move further than this in one frame is a leap, glided rather than jumped. */
        private const val LEAP_DP = 24f
        private const val SETTLED_PX = 0.5f
        private const val NANOS_PER_SECOND = 1_000_000_000f
        private const val LONGEST_FRAME_SECONDS = 0.05f

        /** As the clock sheet fades its content, and SystemUI fades the row beside a Pixel clock. */
        private const val ROW_FADE_MS = 150L

        /** How long the card takes to come back in once the scene has stopped morphing it. */
        private const val MORPH_FADE_MS = 200L
    }
}
