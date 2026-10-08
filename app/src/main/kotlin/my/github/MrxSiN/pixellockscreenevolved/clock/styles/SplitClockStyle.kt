package my.github.MrxSiN.pixellockscreenevolved.clock.styles

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockStyle
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectAwareClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.SubjectFrame
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.ClockThumbnail
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.DateLine
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.FlexCut
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.NumeralView
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.OneLineSmallFace
import my.github.MrxSiN.pixellockscreenevolved.clock.parts.TimeParts

/**
 * Split: made for the depth effect. The time is one heavy line drawn down the
 * screen, stretched until it meets the photo's subject and a little past it, so
 * the subject stands in front of the ends of the numerals. As the subject moves,
 * because the photo changed or was scrolled, the line is drawn again around
 * where it now stands ([SubjectAwareClockFace], [SplitClockScale]).
 */
class SplitClockStyle : ClockStyle {

    override val id: String = "PIXEL_LOCK_SCREEN_EVOLVED_SPLIT"
    override val name: String = "Split"
    override val description: String = "The time drawn down the screen and split by the photo's subject"
    override val isResizable: Boolean = true

    /** The line runs down from under the status bar to the subject; it has nowhere else to stand. */
    override val isMovable: Boolean = false

    override fun createFace(context: Context, size: FaceSize): ClockFace = when (size) {
        FaceSize.LARGE -> Face(context)
        // Beside notifications the line cannot run down the screen, so it keeps its weight in height instead.
        FaceSize.SMALL -> OneLineSmallFace(context, SMALL_CUT, SMALL_NUMERAL_TO_SHORT_SIDE)
    }

    /**
     * The time split by the subject standing in front of it, as the face draws
     * it: tall stretched numerals with a figure cut out of them, so the style
     * reads apart from the plain one-line clocks beside it in the list.
     */
    override fun createThumbnail(context: Context): Drawable = ClockThumbnail(context) { canvas, box, paint ->
        paint.fontVariationSettings = CUT.settings
        paint.textSize = PROBE
        val numerals = Rect().also { paint.getTextBounds(NUMERALS, 0, NUMERALS.length, it) }
        val widthPerSize = paint.measureText(ClockThumbnail.SAMPLE_TIME) / PROBE
        paint.textSize = box.width() * THUMBNAIL_WIDTH / widthPerSize
        paint.getTextBounds(NUMERALS, 0, NUMERALS.length, numerals)
        val stretch = box.height() * THUMBNAIL_HEIGHT / numerals.height()

        val layer = canvas.saveLayer(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat(), null)
        canvas.save()
        canvas.translate(
            box.exactCenterX() - paint.measureText(ClockThumbnail.SAMPLE_TIME) / 2,
            box.exactCenterY() + numerals.height() * stretch / 2,
        )
        canvas.scale(1f, stretch)
        canvas.drawText(ClockThumbnail.SAMPLE_TIME, 0f, 0f, paint)
        canvas.restore()

        // The subject, cut out of the time as the depth effect cuts it on the lock screen.
        val head = box.width() * SUBJECT_HEAD
        val centreX = box.exactCenterX()
        val headY = box.exactCenterY() + box.height() * SUBJECT_DROP
        canvas.drawCircle(centreX, headY, head, SUBJECT)
        val shoulders = head * SUBJECT_SHOULDERS
        canvas.drawRoundRect(
            centreX - shoulders, headY + head * SUBJECT_NECK, centreX + shoulders, box.bottom.toFloat(), head, head, SUBJECT,
        )
        canvas.restoreToCount(layer)
    }

    private class Face(context: Context) : NumeralClockFace(context, DateLine(context, DATE)), ResizableClockFace, SubjectAwareClockFace {

        private val time = NumeralView(context, CUT)

        override val numerals: List<NumeralView> = listOf(time)
        override val view: View = column(Gravity.CENTER_HORIZONTAL, requireNotNull(date).view, time)
        override val timeView: View get() = time

        private var size = 0f
        private var subject: SubjectFrame? = null

        init {
            relayout()
            refresh()
        }

        override fun write(parts: TimeParts.Parts) = time.setText(parts.joined)

        override fun setSize(size: Float) {
            this.size = size
            relayout()
        }

        override fun setSubject(subject: SubjectFrame?) {
            if (this.subject == subject) return
            this.subject = subject
            relayout()
        }

        private fun relayout() {
            val room = SplitClockScale.roomFor(subject, aboveNumerals(), screenHeight)
            spaceAbove(time, shortSide * DATE_GAP)
            time.fit(
                SplitClockScale.numeralHeight(size, room, shortSide),
                maxWidth = shortSide * MAX_WIDTH,
                maxStretch = MAX_STRETCH,
            )
        }

        /**
         * How far below the face's top the numerals begin: the date line and the
         * space under it, as laid out, or what they will measure before the face
         * has been laid out once.
         */
        private fun aboveNumerals(): Float = time.top.toFloat().takeIf { it > 0f } ?: shortSide * (DATE.sizeToShortSide + DATE_GAP)
    }

    private companion object {
        /** Heavy and wide, so the horizontals still read once the numerals are drawn taller than designed. */
        val CUT = FlexCut(weight = 700f, width = 140f)

        /** The small face's cut: the same voice, narrow enough to sit beside notifications. */
        val SMALL_CUT = FlexCut(weight = 700f, width = 100f)

        /**
         * The small face stands taller than a Pixel small clock's figures: this
         * style's large face fills the screen, and dropping to the usual height
         * beside a notification read as the clock shrinking away.
         */
        const val SMALL_NUMERAL_TO_SHORT_SIDE = 0.18f

        val DATE = DateLine.Look(skeleton = "EEEMMMd", sizeToShortSide = 0.04f, weight = 600)

        /** Widest the line may be, against the screen's short side. */
        const val MAX_WIDTH = 0.96f

        /** How much taller than the font designs them the numerals may be drawn to reach the subject. */
        const val MAX_STRETCH = 3f

        const val DATE_GAP = 0.05f

        const val NUMERALS = "0123456789"
        const val PROBE = 100f
        const val THUMBNAIL_WIDTH = 0.92f
        const val THUMBNAIL_HEIGHT = 0.86f

        /** The cut-out figure, against the thumbnail's own box. */
        const val SUBJECT_HEAD = 0.13f
        const val SUBJECT_DROP = 0.14f
        const val SUBJECT_SHOULDERS = 1.9f
        const val SUBJECT_NECK = 0.7f

        val SUBJECT = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    }
}
