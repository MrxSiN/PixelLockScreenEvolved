package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.content.Context
import android.view.View

import java.util.WeakHashMap

import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ExpressiveClockFace
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
    /** The host's own context, for whatever places the face against what SystemUI lays out around it. */
    val context: Context,
    initialTheme: Any,
) {

    private var theme: Any = initialTheme

    /**
     * Counts the changes to how the time is written (its text, size and width), so
     * whatever measures the time can tell its measure is out of date.
     */
    var timeChanges = 0
        private set

    /**
     * How far the parts of the face around its time have come in, from 0 to 1,
     * as the clock changes size; the time itself is carried across instead.
     */
    var arrival = 1f

    /** How far the face has gone into the always-on display, from 0 awake to 1 dozing. */
    var dozeFraction = 0f
        private set

    private val placement = ClockFacePlacement(api.viewId(size), size, LockScreenInsets.largeClockTop(context))

    /**
     * This face as the lock screen scene composes it, or null on a build whose
     * keyguard still adds and constrains the face's view itself.
     */
    private val element: Any? = api.elements?.let { elements ->
        proxies.create(
            elements.elementType,
            mapOf(
                "getKey" to { _ -> elements.key(size) },
                "getSource" to { _ -> elements.dynamicSource },
                "getContext" to { _ -> context },
                "LockscreenElement" to { args ->
                    elements.draw(requireNotNull(args[0]), requireNotNull(args[1]), face.view, size)
                    null
                },
            ),
        )
    }

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
            "getElements" to { _ -> listOfNotNull(element) },
        ),
    )

    private val events = proxies.create(
        api.faceEventsType,
        mapOf(
            "onTimeTick" to { _ -> refresh() },
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
            "enter" to { _ -> (face as? ExpressiveClockFace)?.enter() },
            "charge" to { _ -> (face as? ExpressiveClockFace)?.charge() },
            "fold" to IgnoreCall,
            "onFidgetTap" to IgnoreCall,
            "onFontAxesChanged" to { args -> setAxes(requireNotNull(args[0])) },
            "onPickerCarouselSwiping" to IgnoreCall,
            "onPositionAnimated" to IgnoreCall,
        ),
    )

    private val config = api.faceConfig(hasCustomWeatherDataDisplay = face.drawsDate)

    /** Whether the face's time is moving to new text ([ClockFace.isChanging]). */
    val timeChanging: Boolean get() = face.isChanging

    /** The face's time, as [ClockFace.timeView] names it. */
    val timeView: View get() = face.timeView

    /** The face's whole view, as SystemUI places it. */
    val view: View get() = face.view

    /** The style face, exposed only for optional capabilities such as weather. */
    val viewFace: ClockFace get() = face

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
        face.view.tag = ModuleClockFaceTag
        ModuleClockFaces.track(face.view, size)
        // The rows under a small clock are spaced against SystemUI's own band for it ([LockScreenInsets]).
        if (size == FaceSize.SMALL) face.view.minimumHeight = LockScreenInsets.smallClockHeight(context)
        if (api.elements != null && context.packageName == SYSTEMUI_PACKAGE) {
            placement.inComposedLockScreen(face.view) { dozeFraction }
        }
        recolor()
    }

    fun refresh() {
        timeChanges++
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
        api.axisOf(axisStyle, ClockAxes.WIDTH_KEY)?.let(::setWidth)
        api.axisOf(axisStyle, ClockAxes.SIZE_KEY)?.let(::setSizeStep)
    }

    /**
     * Shows the size step the picker or the clock setting chose on the large
     * face; the small face keeps [ClockAxes.SMALL_CLOCK_SIZE_STEP]. A face that
     * cannot be resized has nothing to change.
     */
    fun setSizeStep(step: Float?) {
        val shown = if (size == FaceSize.SMALL) ClockAxes.SMALL_CLOCK_SIZE_STEP else step
        timeChanges++
        (face as? ResizableClockFace)?.setSize(ClockAxes.sizeOf(shown))
    }

    /** Shows the width a stored width value chooses; a face with one width has nothing to change. */
    fun setWidth(value: Float?) {
        timeChanges++
        (face as? AdjustableWidthClockFace)?.setWidth(ClockAxes.widthOf(value))
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
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        val COLOR_EVALUATOR = ArgbEvaluator()
    }
}

/** Identifies this module's face without coupling smartspace placement to its concrete view type. */
internal object ModuleClockFaceTag

/**
 * Module-owned faces currently attached to one SystemUI root, so what SystemUI
 * lays out around the clock can tell one of this module's from a Pixel clock.
 */
internal object ModuleClockFaces {

    private val faces = WeakHashMap<View, FaceSize>()

    fun track(view: View, size: FaceSize) = synchronized(faces) { faces[view] = size }

    /** Scratch for [largeIn], under the lock. */
    private val location = IntArray(2)

    /** The large module face shown in [root], the highest where there are several. */
    fun largeIn(root: View): View? = synchronized(faces) {
        var highest: View? = null
        var highestTop = 0
        // Walked without a list or a lambda: it runs every frame the lock screen draws.
        for (entry in faces.entries) {
            val view = entry.key ?: continue
            if (entry.value != FaceSize.LARGE || !view.isAttachedToWindow || !view.isShown || view.rootView !== root) continue
            view.getLocationOnScreen(location)
            if (highest == null || location[1] < highestTop) {
                highest = view
                highestTop = location[1]
            }
        }
        highest
    }
}
