package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.content.Context
import android.view.View

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FontChoosingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ResizableClockFace

/**
 * One [ClockFace] presented to SystemUI as a `ClockFaceController`.
 *
 * SystemUI talks to a face through four objects: the controller itself, its
 * layout, its events and its animations. This class builds all four and keeps
 * the one piece of state they share, the colour inputs, so the face only ever
 * hears a settled colour.
 */
internal class ClockFaceAdapter(
    private val api: ClockPluginApi,
    proxies: InterfaceProxy,
    private val face: ClockFace,
    private val size: FaceSize,
    private val fontCount: Int,
    private val context: Context,
    initialTheme: Any,
) {

    private var theme: Any = initialTheme

    /** How far the face has gone into the always-on display, from 0 awake to 1 dozing. */
    var dozeFraction = 0f
        private set

    private val placement = ClockFacePlacement(api.viewId(size), size, LockScreenInsets.largeClockTop(context))

    private val layout = proxies.create(
        api.faceLayoutType,
        mapOf(
            "getViews" to { _ -> listOf(face.view) },
            "applyConstraints" to { args -> args[0]?.also { placement.onLockScreen(ConstraintSetEditor(it)) } },
            "applyPreviewConstraints" to { args ->
                args[1]?.also { placement.inPreview(ConstraintSetEditor(it), api.previewTops(requireNotNull(args[0]))) }
            },
            "applyExternalDisplayPresentationConstraints" to { args -> args[0] },
            // SystemUI moves and scales the clock's views for burn-in itself, as Google's faces rely on;
            // moving them here too doubled the shift, which snapped back midway through waking up.
            "applyAodBurnIn" to IgnoreCall,
            "getElements" to { _ -> emptyList<Any>() },
        ),
    )

    private val events = proxies.create(
        api.faceEventsType,
        mapOf(
            "onTimeTick" to { _ -> face.refresh() },
            "onThemeChanged" to { args -> setTheme(requireNotNull(args[0])) },
            "onFontSettingChanged" to IgnoreCall,
            "onSecondaryDisplayChanged" to IgnoreCall,
            "onTargetRegionChanged" to IgnoreCall,
        ),
    )

    private val animations = proxies.create(
        api.animationsType,
        mapOf(
            "doze" to { args -> setDoze(args[0] as Float) },
            "enter" to IgnoreCall,
            "charge" to IgnoreCall,
            "fold" to IgnoreCall,
            "onFidgetTap" to IgnoreCall,
            "onFontAxesChanged" to { args -> setAxes(requireNotNull(args[0])) },
            "onPickerCarouselSwiping" to IgnoreCall,
            "onPositionAnimated" to IgnoreCall,
        ),
    )

    private val config = api.faceConfig(face.drawsDate)

    /** The face's time, as [ClockFace.timeView] names it. */
    val timeView: View get() = face.timeView

    /** The face's whole view, as SystemUI places it. */
    val view: View get() = face.view

    val controller: Any = proxies.create(
        api.faceControllerType,
        mapOf(
            "getView" to { _ -> face.view },
            "getLayout" to { _ -> layout },
            "getConfig" to { _ -> config },
            "getEvents" to { _ -> events },
            "getAnimations" to { _ -> animations },
            "getTheme" to { _ -> theme },
            "setTheme" to { args -> setTheme(requireNotNull(args[0])) },
        ),
    )

    init {
        face.view.id = api.viewId(size)
        recolor()
    }

    fun refresh() {
        face.refresh()
    }

    fun setTheme(theme: Any) {
        this.theme = theme
        recolor()
    }

    fun setDarkTheme(isDarkTheme: Boolean) {
        setTheme(api.withDarkTheme(theme, isDarkTheme))
    }

    /**
     * Shows what a `ClockAxisStyle` chooses. The picker passes only the axes it
     * is changing, so an axis the style does not hold is left as it is.
     */
    private fun setAxes(axisStyle: Any) {
        api.axisOf(axisStyle, ClockAxes.FONT_KEY)?.let(::setFont)
        api.axisOf(axisStyle, ClockAxes.SIZE_KEY)?.let(::setSizeStep)
    }

    /**
     * Shows the size step the picker or the clock setting chose on the large
     * face; the small face keeps [ClockAxes.SMALL_CLOCK_SIZE_STEP]. A face that
     * cannot be resized has nothing to change.
     */
    fun setSizeStep(step: Float?) {
        val shown = if (size == FaceSize.SMALL) ClockAxes.SMALL_CLOCK_SIZE_STEP else step
        (face as? ResizableClockFace)?.setSize(ClockAxes.sizeOf(shown))
    }

    /** Shows the font a stored font value chooses; a face with one font has nothing to change. */
    fun setFont(value: Float?) {
        (face as? FontChoosingClockFace)?.setFont(ClockAxes.fontIndexOf(value, fontCount))
    }

    fun setDoze(fraction: Float) {
        dozeFraction = fraction.coerceIn(0f, 1f)
        (face as? DozingClockFace)?.setDoze(dozeFraction)
        recolor()
    }

    private fun recolor() {
        val colors = api.colors(theme, context)
        face.setColor(COLOR_EVALUATOR.evaluate(dozeFraction, colors.normal, colors.doze) as Int)
    }

    private companion object {
        val COLOR_EVALUATOR = ArgbEvaluator()
    }
}
