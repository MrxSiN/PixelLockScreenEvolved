package my.github.MrxSiN.pixellockscreenevolved.host

import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.BOTTOM
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.MATCH_CONSTRAINT
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.PARENT_ID
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.TOP
import my.github.MrxSiN.pixellockscreenevolved.host.ConstraintSetEditor.Companion.WRAP_CONTENT

/**
 * Where a face sits on the lock screen and in the picker preview: centred, the
 * large face pinned under the status bar rather than floated down the screen
 * the way a Pixel clock is, and the small face as tall as its drawing rather
 * than the fixed band SystemUI gives a Pixel small clock. The small face spans
 * the width, which is what keeps SystemUI's date and weather line below it; a
 * narrower clock leaves room beside it, where SystemUI puts the line instead.
 *
 * On the lock screen SystemUI places both clock views by id before asking the
 * face, so only what differs is changed. The preview has no placement of its
 * own, so it is given one whole.
 */
internal class ClockFacePlacement(
    private val viewId: Int,
    private val size: FaceSize,
    private val largeClockTop: Int,
) {

    fun onLockScreen(editor: ConstraintSetEditor) {
        when (size) {
            FaceSize.LARGE -> pinToTop(editor, largeClockTop)
            FaceSize.SMALL -> {
                editor.constrainWidth(viewId, MATCH_CONSTRAINT)
                editor.constrainHeight(viewId, WRAP_CONTENT)
            }
        }
        editor.centerHorizontally(viewId)
    }

    fun inPreview(editor: ConstraintSetEditor, tops: PreviewTops) {
        editor.constrainWidth(viewId, WRAP_CONTENT)
        editor.constrainHeight(viewId, WRAP_CONTENT)
        pinToTop(
            editor,
            when (size) {
                FaceSize.LARGE -> maxOf(largeClockTop, tops.largeClock)
                FaceSize.SMALL -> tops.smallClock
            },
        )
        editor.centerHorizontally(viewId)
    }

    private fun pinToTop(editor: ConstraintSetEditor, top: Int) {
        editor.clear(viewId, TOP)
        editor.clear(viewId, BOTTOM)
        editor.connect(viewId, TOP, PARENT_ID, TOP, top)
    }
}
