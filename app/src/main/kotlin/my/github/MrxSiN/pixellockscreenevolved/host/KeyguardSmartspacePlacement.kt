package my.github.MrxSiN.pixellockscreenevolved.host

import android.util.TypedValue
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.BOTTOM
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.TOP

/**
 * Puts SystemUI's smartspace, the date and weather row and the card below it,
 * where this module's clocks want it.
 *
 * With the large clock, the clock writes its own date, and SystemUI leaves the
 * card for such a clock to place: it gives it no top, and it drifts to the
 * bottom of the screen. It is put below the clock, spaced as the clock's own
 * rows are ([LockScreenInsets]).
 *
 * With the small clock, SystemUI places the row for a Pixel small clock, which
 * hugs the start edge: flush left and almost touching the clock. Under a
 * centred clock that reads as misaligned, so the row is centred and given the
 * same space above it as SystemUI leaves between it and the notifications.
 * The keyguard lays out its clock section before its smartspace section, so the
 * row's constraints are adjusted right after the smartspace section sets them.
 *
 * In the picker preview, SystemUI shows a date row with the large clock too,
 * because the preview never asks whether the clock writes its own date; over a
 * tall clock it lands on the numerals. It is hidden there, as the lock screen
 * hides it.
 */
class KeyguardSmartspacePlacement(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }.toSet()

    override fun install(classLoader: ClassLoader) {
        val (smartspace, clocks) = try {
            val clocks = ClockPluginApi(classLoader)
            KeyguardSmartspaceApi(classLoader, clocks) to clocks
        } catch (error: ReflectiveOperationException) {
            logger.warn("Keyguard smartspace not found; the date row keeps SystemUI's place", error)
            return
        }
        val smallClockId = clocks.viewId(FaceSize.SMALL)
        val largeClockId = clocks.viewId(FaceSize.LARGE)

        hooks.after(smartspace.applyConstraints) { section, args ->
            val set = args.firstOrNull() ?: return@after
            val owner = requireNotNull(section)
            if (smartspace.sectionClockId(owner) !in styleIds) return@after
            val row = smartspace.dateRow(owner) ?: return@after
            val editor = ConstraintSetEditor(set)

            if (smartspace.isLargeClockVisible(owner)) {
                val card = systemUiId(row, CARD_ID)
                if (card == 0) return@after
                editor.clear(card, TOP)
                editor.clear(card, BOTTOM)
                editor.connect(card, TOP, largeClockId, BOTTOM, LockScreenInsets.cardGap(row.context))
            } else {
                editor.connect(row.id, TOP, smallClockId, BOTTOM, gapAbove(row))
                editor.centerHorizontally(row.id)
            }
        }

        hooks.after(smartspace.previewVisibility) { binder, args ->
            val emission = args.firstOrNull() ?: return@after
            val owner = requireNotNull(binder)
            if (!smartspace.previewShowsLargeClock(emission)) return@after
            if (smartspace.previewClockId(owner) !in styleIds) return@after
            smartspace.previewDateRows(owner).forEach { it.visibility = View.GONE }
        }
    }

    private fun systemUiId(view: View, name: String): Int =
        view.resources.getIdentifier(name, "id", view.context.packageName)

    private fun gapAbove(row: View): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, GAP_ABOVE_DP, row.resources.displayMetrics,
    ).toInt()

    private companion object {
        /** Matches the space SystemUI leaves between the row and the notifications. */
        const val GAP_ABOVE_DP = 20f

        /** The smartspace card: weather forecasts, events, and the like. */
        const val CARD_ID = "bc_smartspace_view"
    }
}
