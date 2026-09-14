package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub

/**
 * What follows the clock on the status bar, its notification icons and the chips
 * beside them (a screen recording, a call), coming in after an unlock with
 * Material 3 Expressive motion, instead of simply reappearing.
 *
 * Everything is held hidden ([hold]) from the moment the unlock begins, again
 * on every frame, since SystemUI adds and rebinds icons as the unlock goes on.
 * Once the status bar shows the row ([enter]), each grows out from the clock's
 * side on a bouncy spatial spring as it fades in on an effects spring, one after
 * another from the clock outward. Anything SystemUI adds a moment later is
 * greeted with a bounce ([admitNewcomers]).
 *
 * SystemUI places and fades each icon itself through its translation, scale and
 * alpha, so this only uses the transforms it leaves alone: the animation matrix
 * and the transition alpha.
 */
internal class NotificationIconsEntrance(
    private val startSide: ViewGroup,
    private val clock: View,
) {

    private val running = mutableListOf<ValueAnimator>()
    private val held = mutableSetOf<View>()
    private val entered = mutableSetOf<View>()

    /** Everything on the clock's side of the status bar after the clock: notification icons and chips. */
    private fun items(): List<View> = buildList { collect(startSide) }

    private fun MutableList<View>.collect(group: ViewGroup) {
        for (child in (0 until group.childCount).map(group::getChildAt)) {
            when {
                child === clock || child is ViewStub || child.id == View.NO_ID && child.width == 0 -> Unit
                child.idName() in CONTAINERS && child is ViewGroup -> collect(child)
                child.idName() in LEFT_ALONE || child.visibility != View.VISIBLE -> Unit
                else -> add(child)
            }
        }
    }

    /** The layouts that hold what follows the clock, as SystemUI has them now. */
    private fun containers(): List<View> = buildList {
        fun find(group: ViewGroup) {
            for (child in (0 until group.childCount).map(group::getChildAt)) {
                if (child.idName() in CONTAINERS && child is ViewGroup) {
                    add(child)
                    find(child)
                }
            }
        }
        find(startSide)
    }

    /**
     * Hides whatever is in the row now until [enter] brings it in, or [release]
     * gives it back. Until then the layouts holding it are hidden too, so a chip
     * SystemUI adds in the middle of a frame does not show before it can be held.
     */
    fun hold() {
        if (entered.isEmpty()) containers().forEach { it.transitionAlpha = 0f }
        items().filter { it !in entered }.forEach {
            held += it
            it.transitionAlpha = 0f
        }
    }

    /** Whether the status bar is showing its clock's side now, so what follows the clock can come in. */
    fun rowShown(): Boolean = ViewPictures.visibleAlpha(startSide) >= SHOWN

    fun enter() {
        hold()
        bringIn(items().filter { it !in entered })
    }

    /**
     * Greets anything SystemUI has added to the row since [enter] with a small
     * bounce. SystemUI shows it before this module can hide it, and hiding it a
     * few frames late only to grow it back made it blink, so it is left shown.
     */
    fun admitNewcomers() {
        val newcomers = items().filter { it !in entered }
        newcomers.forEach { item ->
            entered += item
            val matrix = Matrix()
            running += SPATIAL.animator(
                update = { seconds ->
                    val scale = 1f + NEWCOMER_BOUNCE * SPATIAL.kickAt(seconds)
                    matrix.setScale(scale, scale, item.width / 2f, item.height / 2f)
                    item.animationMatrix = matrix
                },
                end = { settle(item) },
            ).also { it.start() }
        }
    }

    private fun bringIn(views: List<View>) {
        val offset = ENTER_OFFSET_DP * startSide.resources.displayMetrics.density
        val direction = if (startSide.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1f else -1f
        views.sortedBy { ViewBounds.inWindow(it).left * -direction }.forEachIndexed { index, item ->
            entered += item
            val matrix = Matrix()
            running += SPATIAL.animator(
                delayMillis = index * STAGGER_MS,
                update = { seconds ->
                    val move = SPATIAL.valueAt(seconds)
                    val scale = START_SCALE + (1f - START_SCALE) * move
                    matrix.setScale(scale, scale, item.width / 2f, item.height / 2f)
                    matrix.postTranslate(direction * offset * (1f - move), 0f)
                    item.animationMatrix = matrix
                    item.transitionAlpha = EFFECTS.valueAt(seconds).coerceIn(0f, 1f)
                },
                end = { settle(item) },
            ).also { it.start() }
        }
        containers().forEach { it.transitionAlpha = 1f }
    }

    /** Gives everything held back as SystemUI shows it, ending any entrance under way. */
    fun release() {
        running.toList().forEach { it.end() }
        running.clear()
        held.forEach(::settle)
        held.clear()
        containers().forEach { it.transitionAlpha = 1f }
    }

    private fun settle(item: View) {
        item.animationMatrix = null
        item.transitionAlpha = 1f
    }

    private fun View.idName(): String? =
        if (id == View.NO_ID) null else runCatching { resources.getResourceEntryName(id) }.getOrNull()

    private companion object {
        val SPATIAL = ExpressiveSpring.FAST_SPATIAL
        val EFFECTS = ExpressiveSpring.FAST_EFFECTS

        /** Layouts whose children are what follows the clock. */
        val CONTAINERS = setOf("start_side_notif_and_chip_container", "notification_icon_area", "notificationIcons")

        /** What sits on the clock's side but is not something following it. */
        val LEFT_ALONE = setOf("operator_name_frame")

        /** How small each icon starts. */
        const val START_SCALE = 0.4f

        /** How far toward the clock each icon starts. */
        const val ENTER_OFFSET_DP = 12f

        /** How much larger than its size something SystemUI adds late swells as it bounces. */
        const val NEWCOMER_BOUNCE = 0.12f

        /** Time between one icon and the next setting off. */
        const val STAGGER_MS = 45L

        const val SHOWN = 0.99f
    }
}
