package my.github.MrxSiN.pixellockscreenevolved.host

import android.graphics.Matrix
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

import java.util.Collections
import java.util.WeakHashMap

/**
 * Stacks SystemUI's date row and smartspace card under a large clock of this
 * module's on Android 17's composed lock screen, on the always-on display too,
 * as the clock's design asks ([ModuleClockFaces]).
 *
 * The scene lays both out above the clock, and this module pins its large
 * clock under the status bar ([ClockFacePlacement]), so they overlapped. Every
 * large face writes its own date, so beside one the row is hidden and the card
 * is moved under the clock, spaced as the lock screen's rows are
 * ([LockScreenInsets]), every frame the scene moves anything ([SceneFrames]);
 * with a small clock both go back to where the scene put them. Their holders
 * are let draw them outside their own bounds, where they now stand.
 */
internal class ComposedSmartspacePlacement {

    private val followed = Collections.newSetFromMap(WeakHashMap<View, Boolean>())
    private val hiddenRows = WeakHashMap<View, Int>()

    // Scratch for the placement run every frame, on the main thread.
    private val clockBounds = RectF()
    private val toWindow = Matrix()
    private val values = FloatArray(MATRIX_VALUES)
    private val origin = FloatArray(2)

    /** Starts keeping [card] under the large clock, once per card. */
    fun followCard(card: View) = follow(card) { placeCard(card) }

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
            hiddenRows.putIfAbsent(row, row.visibility)
            if (row.visibility != View.INVISIBLE) row.visibility = View.INVISIBLE
            return
        }
        hiddenRows.remove(row)?.let { original -> if (row.visibility != original) row.visibility = original }
    }

    private fun placeCard(card: View) {
        moveUnder(card, ModuleClockFaces.largeIn(card.rootView), LockScreenInsets.cardGap(card.context))
    }

    /** Moves [view] so its top sits [gap] pixels under [clock]'s bottom, in window pixels, or back to its place where there is none. */
    private fun moveUnder(view: View, clock: View?, gap: Int) {
        val moved = if (clock == null || view.height == 0) 0f else offset(view, ViewBounds.inWindow(clock, clockBounds).bottom + gap)
        if (view.translationY != moved) view.translationY = moved
        (view.parent as? ViewGroup)?.takeIf { it.clipChildren }?.clipChildren = false
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
    }
}
