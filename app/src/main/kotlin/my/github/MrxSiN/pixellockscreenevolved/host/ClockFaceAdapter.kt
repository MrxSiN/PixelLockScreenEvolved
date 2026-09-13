package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.content.Context

import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
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
    private val context: Context,
    initialTheme: Any,
) {

    private var theme: Any = initialTheme
    private var dozeFraction = 0f

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
            "applyAodBurnIn" to { args -> applyBurnIn(requireNotNull(args[0])) },
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
            "onFontAxesChanged" to { args -> setSizeStep(api.sizeStepOf(requireNotNull(args[0]))) },
            "onPickerCarouselSwiping" to IgnoreCall,
            "onPositionAnimated" to IgnoreCall,
        ),
    )

    private val config = api.faceConfig(face.drawsDate)

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
     * Shows the size step the picker or the clock setting chose on the large
     * face; the small face keeps [ClockSizePresets.SMALL_CLOCK_STEP]. A face
     * that cannot be resized has nothing to change.
     */
    fun setSizeStep(step: Float?) {
        val shown = if (size == FaceSize.SMALL) ClockSizePresets.SMALL_CLOCK_STEP else step
        (face as? ResizableClockFace)?.setSize(ClockSizePresets.sizeOf(shown))
    }

    fun setDoze(fraction: Float) {
        dozeFraction = fraction.coerceIn(0f, 1f)
        recolor()
    }

    private fun recolor() {
        val colors = api.colors(theme, context)
        face.setColor(COLOR_EVALUATOR.evaluate(dozeFraction, colors.normal, colors.doze) as Int)
    }

    private fun applyBurnIn(model: Any) {
        val burnIn = api.burnIn(model)
        face.view.apply {
            scaleX = burnIn.scale
            scaleY = burnIn.scale
            translationX = burnIn.translationX
            translationY = burnIn.translationY
        }
    }

    private companion object {
        val COLOR_EVALUATOR = ArgbEvaluator()
    }
}
