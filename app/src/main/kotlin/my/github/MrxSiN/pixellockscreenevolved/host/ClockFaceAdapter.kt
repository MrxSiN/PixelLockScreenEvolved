package my.github.MrxSiN.pixellockscreenevolved.host

import android.animation.ArgbEvaluator
import android.content.Context
import android.os.PowerManager
import android.view.View

import java.lang.ref.WeakReference


import my.github.MrxSiN.pixellockscreenevolved.clock.AdjustableWidthClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.DozingClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.EdgeToEdgeClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.ExpressiveClockFace
import my.github.MrxSiN.pixellockscreenevolved.clock.FaceSize
import my.github.MrxSiN.pixellockscreenevolved.clock.FillingClockFace
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
    /** The stops of the style's size slider ([ClockStyle.sizeSteps]). */
    private val sizeSteps: Int,
    /** The host's own context, for whatever places the face against what SystemUI lays out around it. */
    val context: Context,
    initialTheme: Any,
) : DepthTarget {

    private var theme: Any = initialTheme

    /**
     * Counts the changes to how the time is written (its text, size and width), so
     * whatever measures the time can tell its measure is out of date.
     */
    override var timeChanges = 0
        private set

    /**
     * How far the parts of the face around its time have come in, from 0 to 1,
     * as the clock changes size; the time itself is carried across instead.
     */
    override var arrival = 1f

    /** How far the face has gone into the always-on display, from 0 awake to 1 dozing. */
    override var dozeFraction = 0f
        private set

    /** Where the large face stands awake. */
    val clockLayout = ClockLayout(face.view, movable = size == FaceSize.LARGE)

    private val placement = ClockFacePlacement(api.viewId(size), size, LockScreenInsets.largeClockTop(context), clockLayout)

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
                args[1]?.also { placement.inPreview(ConstraintSetEditor(it), api.previewSmallClockTop(requireNotNull(args[0]))) }
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
    override val timeChanging: Boolean get() = face.isChanging

    /** The face's time, as [ClockFace.timeView] names it. */
    val timeView: View get() = face.timeView

    /** What the subject may stand in front of, as [ClockFace.depthView] names it. */
    override val depthView: View get() = face.depthView

    override val inkColor: Int? get() = color

    /** Only the large face moves with the phone's tilt; the small one sits among notifications. */
    override val followsMotion: Boolean get() = size == FaceSize.LARGE

    /** The face's whole view, as SystemUI places it. */
    override val view: View get() = face.view

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
        ModuleClockFaces.track(face.view, size, face.drawsDate)
        // The rows under a small clock are spaced against SystemUI's own band for it ([LockScreenInsets]).
        if (size == FaceSize.SMALL) face.view.minimumHeight = LockScreenInsets.smallClockHeight(context)
        if (api.elements != null && context.packageName == SYSTEMUI_PACKAGE) {
            val edgeToEdge = face as? EdgeToEdgeClockFace
            placement.inComposedLockScreen(face.view, { dozeFraction }, edgeToEdge?.let { edge -> DrawnScale(edge::setDrawnScale) }, room(), fit())
            arriveOnSwap()
        } else if (context.packageName != SYSTEMUI_PACKAGE) {
            placement.inPickerPreview(face.view, room(), fit())
        }
        recolor()
    }

    /**
     * Plays the face's entrance ([ExpressiveClockFace.enter]) each time the
     * scene swaps it in for the other size on an awake lock screen. The scene
     * fades one size out and only then fades the other in where it stands, and
     * attaches the incoming face's view as it does, so the face moves into place
     * of its own accord rather than simply appearing. Asleep, the always-on
     * display keeps it still.
     */
    private fun arriveOnSwap() {
        val expressive = face as? ExpressiveClockFace ?: return
        val power = context.getSystemService(PowerManager::class.java) ?: return
        face.view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                // After the scene has laid the view out, so the entrance knows its size.
                if (power.isInteractive && dozeFraction == 0f) view.post { if (power.isInteractive) expressive.enter() }
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }

    /** The large face's adaptive size, for a face whose style can be resized; null otherwise. */
    private fun fit(): AdaptiveFit? = if (size == FaceSize.LARGE && face is ResizableClockFace) {
        AdaptiveFit(::fitTo)
    } else {
        null
    }

    /** The 0 to 1 size the size step chose ([setSizeStep]): what the face shows unless adaptive size fits it. */
    private var steppedSize = 0f

    /** Whether adaptive size is on ([ClockAxes.SIZE_ADAPTIVE_KEY]); only a large face ever fits. */
    private var sizeAdaptive = false

    /** The height adaptive size last asked for, NaN for none, and whether the face shows a size fitted to it. */
    private var fitHeight = Float.NaN
    private var fitted = false

    /** Turns adaptive size on or off ([ClockAxes.SIZE_ADAPTIVE_KEY]); off, the face goes back to its stepped size. */
    fun setSizeAdaptive(adaptive: Boolean) {
        if (size != FaceSize.LARGE || adaptive == sizeAdaptive) return
        sizeAdaptive = adaptive
        refit()
    }

    /** Hears the height adaptive size gives the face now ([ClockFacePlacement]). On the main thread. */
    private fun fitTo(height: Float) {
        fitHeight = height
        refit()
    }

    /**
     * Shows the largest size of the face's own range whose height is no more than [fitHeight], found by
     * halving that range against the face measured at each size tried; the stepped size where adaptive
     * size is off or has no subject to fit to. The size grows the face's width with its height, so the
     * height alone is matched; the style's smallest and largest sizes bound the answer.
     */
    private fun refit() {
        val resizable = face as? ResizableClockFace ?: return
        val wanted = fitHeight
        if (!sizeAdaptive || wanted.isNaN() || wanted <= 0f) {
            if (!fitted) return
            fitted = false
            timeChanges++
            show(resizable, steppedSize)
            return
        }
        fun heightAt(size: Float): Int {
            resizable.setSizeAtOnce(size)
            face.view.measure(FIT_WIDTH, FIT_HEIGHT)
            return face.view.measuredHeight
        }
        val chosen = when {
            heightAt(1f) <= wanted -> 1f
            heightAt(0f) >= wanted -> 0f
            else -> {
                var low = 0f
                var high = 1f
                repeat(FIT_STEPS) {
                    val middle = (low + high) / 2
                    if (heightAt(middle) <= wanted) low = middle else high = middle
                }
                low
            }
        }
        fitted = true
        timeChanges++
        // Back where it stood before it was measured, so the face moves to its new size as a step of the slider does.
        resizable.setSizeAtOnce(shownSize)
        show(resizable, chosen)
        face.view.requestLayout()
    }

    /** The 0 to 1 size the face was last asked to show. */
    private var shownSize = 0f

    private fun show(resizable: ResizableClockFace, size: Float) {
        shownSize = size
        resizable.setSize(size)
    }

    private fun room(): ClockRoom? = (face as? FillingClockFace)?.let { filling -> ClockRoom { room ->
            timeChanges++
            filling.setRoom(room)
        } }

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
        val mode = api.axisOf(axisStyle, ClockAxes.POSITION_KEY)
        val share = api.axisOf(axisStyle, ClockAxes.POSITION_SHARE_KEY)
        val rotation = api.axisOf(axisStyle, ClockAxes.ROTATION_KEY)
        if (mode != null || share != null || rotation != null) setPosition(mode, share, rotation)
        api.axisOf(axisStyle, ClockAxes.SIZE_ADAPTIVE_KEY)?.let { setSizeAdaptive(ClockAxes.isSizeAdaptive(it)) }
    }

    /** Places and turns the large face as stored position values choose ([ClockPosition]). */
    fun setPosition(mode: Float?, share: Float?, rotation: Float?) {
        clockLayout.position = ClockPosition.of(mode, share, rotation)
    }

    /**
     * Shows the size step the picker or the clock setting chose on the large
     * face; the small face keeps [ClockAxes.SMALL_CLOCK_SIZE_STEP]. A face that
     * cannot be resized has nothing to change.
     */
    fun setSizeStep(step: Float?) {
        val shown = if (size == FaceSize.SMALL) ClockAxes.SMALL_CLOCK_SIZE_STEP else step
        steppedSize = ClockAxes.sizeOf(shown, sizeSteps)
        // A face adaptive size has fitted keeps its fitted size; the stepped one waits for adaptive size to let go.
        if (fitted) return
        timeChanges++
        (face as? ResizableClockFace)?.let { show(it, steppedSize) }
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

    /** The colour the face was last drawn in. */
    var color: Int = android.graphics.Color.WHITE
        private set

    private fun recolor() {
        val colors = api.colors(theme, context)
        color = COLOR_EVALUATOR.evaluate(dozeFraction, colors.normal, colors.doze) as Int
        face.setColor(color)
    }

    companion object {
        const val SYSTEMUI_PACKAGE = "com.android.systemui"

        /** Halvings of the size range adaptive size makes: a 512th of it, finer than a pixel of any face. */
        private const val FIT_STEPS = 9

        /** A face is measured as it lays itself out unbounded, as the lock screen's clock element lets it. */
        private val FIT_WIDTH: Int = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        private val FIT_HEIGHT: Int = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        private val COLOR_EVALUATOR = ArgbEvaluator()
    }
}

/** Identifies this module's face without coupling smartspace placement to its concrete view type. */
internal object ModuleClockFaceTag

/**
 * Module-owned faces currently attached to one SystemUI root, so what SystemUI
 * lays out around the clock can tell one of this module's from a Pixel clock.
 */
internal object ModuleClockFaces {

    /** A face tracked without keeping it alive. */
    private class Tracked(view: View, var size: FaceSize, var drawsDate: Boolean) {
        val view = WeakReference(view)

        /** When the view last joined a window, in attach order ([datedIn]). */
        var attachedAt = 0L
    }

    private var attachCount = 0L

    // A list walked by index: [datedIn] runs before every frame the lock screen draws, and a map's walk
    // allocates an iterator each time.
    private val faces = ArrayList<Tracked>()

    fun track(view: View, size: FaceSize, drawsDate: Boolean) {
        synchronized(faces) {
            faces.removeAll { it.view.get() == null }
            val tracked = faces.firstOrNull { it.view.get() === view }
            if (tracked != null) {
                tracked.size = size
                tracked.drawsDate = drawsDate
            } else {
                faces += Tracked(view, size, drawsDate)
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(attached: View) {
                synchronized(faces) { faces.firstOrNull { it.view.get() === attached }?.attachedAt = ++attachCount }
                if (size == FaceSize.LARGE) attached.post { largeAttached.forEach { it(attached.rootView) } }
            }

            override fun onViewDetachedFromWindow(detached: View) = Unit
        })
    }

    private val largeAttached = java.util.concurrent.CopyOnWriteArrayList<(View) -> Unit>()

    /** Tells [listener] of the window's root each time a large module face is attached to one, on the main thread. */
    fun onLargeAttached(listener: (root: View) -> Unit) {
        largeAttached += listener
    }

    /**
     * The module face shown in [root] that SystemUI's date row and card stack under: a large face, or a
     * small one that writes its own date, as iOS's does. Where several are shown, as while the scene swaps
     * the large face for the small one, the one it attached last, which it is swapping to: picked by
     * height, the card's target flipped between the two faces part way through.
     */
    fun datedIn(root: View): View? = synchronized(faces) {
        var latest: Tracked? = null
        for (i in faces.indices) {
            val tracked = faces[i]
            if (tracked.size != FaceSize.LARGE && !tracked.drawsDate) continue
            val view = tracked.view.get() ?: continue
            if (!view.isAttachedToWindow || !view.isShown || view.rootView !== root) continue
            if (latest == null || tracked.attachedAt > latest.attachedAt) latest = tracked
        }
        latest?.view?.get()
    }

    /**
     * The height of the small module face on screen, in dp as Compose reads a
     * dimension, or 0 where there is none ([KeyguardSmallClockBand]). When the
     * scene first makes room for it, the face is not yet on screen; the faces
     * built for the lock screen are then measured instead, so the room is right
     * from the first frame. Faces built for previews are left out: they are
     * laid out for another window, and are taller.
     */
    fun smallFaceHeightDp(): Float = synchronized(faces) {
        var shown = 0f
        var waiting = 0f
        for (i in faces.indices) {
            val tracked = faces[i]
            if (tracked.size != FaceSize.SMALL) continue
            val view = tracked.view.get() ?: continue
            val density = view.resources.displayMetrics.density
            if (view.isAttachedToWindow) {
                if (!view.isInNotificationShade()) continue
                shown = maxOf(shown, view.measuredHeight / density)
            } else {
                if (view.measuredHeight == 0) view.measure(UNBOUNDED, UNBOUNDED)
                waiting = maxOf(waiting, view.measuredHeight / density)
            }
        }
        if (shown > 0f) shown else waiting
    }

    private val UNBOUNDED = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
}
