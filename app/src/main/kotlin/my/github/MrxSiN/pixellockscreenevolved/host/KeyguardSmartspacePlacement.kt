package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
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
 * Every large face of this module's writes its own date, where its design puts
 * it ([ClockFace.drawsDate]), so SystemUI's own row is hidden beside one. The
 * small faces leave the date to SystemUI, which always writes its row under a
 * small clock.
 *
 * With the large clock SystemUI leaves the card for the clock to place: it
 * gives it no top, and it drifts to the bottom of the screen. It is put below
 * the clock, spaced as the clock's own rows are ([LockScreenInsets]).
 *
 * With the small clock, SystemUI places the row for a Pixel small clock, which
 * hugs the start edge: flush left and almost touching the clock. Under a
 * centred clock that reads as misaligned, so the row is centred and given the
 * space the lock screen keeps between one row's ink and the next, the same
 * space [LockScreenInsets] gives every other row. The keyguard lays out its
 * clock section before its smartspace section, so the row's constraints are
 * adjusted right after the smartspace section sets them.
 *
 * Android 17's composed lock screen lays the row and the card out above the
 * clock, where the large clock, pinned under the status bar, stood over them;
 * they are hidden or moved below the clock there ([ComposedSmartspacePlacement]).
 *
 * In the picker preview, SystemUI shows a date row with the large clock too,
 * because the preview never asks whether the clock writes its own date; over a
 * tall clock it lands on the numerals. It is hidden there.
 */
class KeyguardSmartspacePlacement(
    private val hooks: Hooks,
    styles: List<ClockStyle>,
    private val logger: Logger,
) : HostPatch {

    private val styleIds = styles.map { it.id }.toSet()
    private val composed = ComposedSmartspacePlacement()

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

        smartspace.composedRows.forEach { composedRow ->
            hooks.after(composedRow) { element, _ ->
                val owner = requireNotNull(element)
                smartspace.composedView(owner)?.let(composed::followRow)
            }
        }

        smartspace.composedCards?.let { composedCards ->
            hooks.after(composedCards) { element, _ ->
                smartspace.composedView(requireNotNull(element))?.let(composed::followCard)
            }
        }

        val applyConstraints = smartspace.applyConstraints
        if (applyConstraints == null) {
            logger.info("Keyguard smartspace places its own live row here; only the preview's is moved")
        } else {
            hooks.after(applyConstraints) { section, args ->
                val set = args.firstOrNull() ?: return@after
                val owner = requireNotNull(section)
                if (smartspace.sectionClockId(owner) !in styleIds) return@after
                val row = smartspace.dateRow(owner) ?: return@after
                val editor = ConstraintSetEditor(set)

                if (smartspace.isLargeClockVisible(owner)) {
                    // SystemUI's smartspace card (forecasts, events): always in the keyguard, GONE while empty.
                    val card = row.resources.getIdentifier("bc_smartspace_view", "id", row.context.packageName)
                    if (card == 0) return@after
                    editor.clear(card, TOP)
                    editor.clear(card, BOTTOM)
                    editor.connect(card, TOP, largeClockId, BOTTOM, LockScreenInsets.cardGap(row.context))
                } else {
                    editor.connect(row.id, TOP, smallClockId, BOTTOM, LockScreenInsets.rowGap(row.context))
                    editor.centerHorizontally(row.id)
                }
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
}
