package my.github.MrxSiN.pixellockscreenevolved.host

import android.content.ContentResolver
import android.content.Context
import android.view.accessibility.AccessibilityManager

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Double tap an empty space on the lock screen to turn the screen off, through
 * the gesture SystemUI already carries.
 *
 * Google's own version ships in SystemUI unfinished: the lock screen's backdrop
 * (`LockscreenTouchHandling`) detects double taps no element above it took,
 * at the platform's double-tap timeout and touch slop, screens them with the
 * falsing manager, and hands them to `KeyguardTouchHandlingInteractor
 * .onDoubleClick`, which turns the screen off through `PowerManager.goToSleep`
 * while `isDoubleTapHandlingEnabled` holds. Where Google has not turned it on,
 * that flag is a constant false. This patch sets it, just before each double
 * tap is acted on, to the stock `double_tap_to_sleep` setting
 * ([DoubleTapToSleep]). The module adds no recognizer, overlay or sleep call of
 * its own, so a build where Google turns the feature on cannot end up with two.
 *
 * Whether SystemUI drives the flag itself is read off the flag: true at a
 * double tap before this module ever wrote it can only be SystemUI's own, and
 * the patch then leaves it alone. Framework `config_supportDoubleTapSleep`
 * cannot tell: Pixel overlays set it on builds whose flag is still off. Once
 * the module has written the flag, it records so in the process's system
 * properties, which outlive a hot reload but not a SystemUI restart.
 *
 * The screen goes dark as a circle closing on the tapped point ([SleepReveal]).
 * The point is the `Offset` the lock screen's tap detector hands its double-tap
 * callback; where that callback cannot be found, or the reveal cannot be
 * drawn, the screen off keeps SystemUI's own lift and the gesture still works.
 * The first tap after it wakes the screen again ([WakeAfterDoubleTapSleep]).
 */
internal class KeyguardDoubleTapToSleep(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val reveal = SleepReveal(hooks, logger)
    private val wake = WakeAfterDoubleTapSleep(hooks)
    private var reportedStock = false

    /** Where the detector callback under way was given, in the lock screen's pixels; NaN outside one. */
    private var tapX = Float.NaN
    private var tapY = Float.NaN

    override fun install(classLoader: ClassLoader) {
        val interactor = Class.forName(INTERACTOR, false, classLoader)
        // Named onDoubleTap(Float, Float) from CP4A; found by its goToSleep call ([HostSymbols]).
        val onDoubleClick = interactor.hostMethod(INTERACTOR, "onDoubleClick")
        val enabled = interactor.field("isDoubleTapHandlingEnabled")
        val context = interactor.field("context")
        val state = Class.forName(READONLY_STATE_FLOW, false, classLoader).field("\$\$delegate_0")
        val stateFlow = Class.forName(STATE_FLOW, false, classLoader)
        val getValue = stateFlow.getMethod("getValue")
        val setValue = stateFlow.getMethod("setValue", Any::class.java)

        hooks.before(onDoubleClick) { target, _ ->
            val flag = state.get(enabled.get(target))
            if (System.getProperty(OWNED) == null) {
                if (getValue.invoke(flag) == true) {
                    if (!reportedStock) logger.info("SystemUI turns double tap to sleep on itself; leaving it alone")
                    reportedStock = true
                    return@before
                }
                System.setProperty(OWNED, "1")
            }
            setValue.invoke(flag, DoubleTapToSleep.isWanted(context.get(target) as Context))
        }
        // After SystemUI's own check of the flag: only a double tap that turned the screen off is drawn around.
        hooks.after(onDoubleClick) { target, args ->
            if (getValue.invoke(state.get(enabled.get(target))) != true) return@after
            wake.onSlept()
            // onDoubleTap(x, y) from CP4A hands the point over itself; before it, the detector callback did.
            val x = args.getOrNull(0) as? Float ?: tapX
            val y = args.getOrNull(1) as? Float ?: tapY
            if (!x.isNaN()) reveal.remember(x, y)
        }

        runCatching {
            reveal.install(classLoader)
            followTaps(classLoader)
        }.onFailure { logger.warn("The double tap's screen off keeps SystemUI's own reveal", it) }
        runCatching { wake.install(classLoader) }
            .onFailure { logger.warn("A tap straight after a double tap to sleep keeps SystemUI's own falsing check", it) }
    }

    /**
     * Notes the point of the detector callback under way. Tap, long press and
     * double tap share one synthetic class, each taking a Compose `Offset`
     * packed as two floats, and the double tap calls `onDoubleClick` from
     * inside it, so the point standing while `onDoubleClick` runs is the
     * double tap's, whichever number R8 gave each case.
     */
    private fun followTaps(classLoader: ClassLoader) {
        val invoke = classLoader.hostClass(TAP_CALLBACKS).getDeclaredMethod("invoke", Any::class.java)
        val packed = Class.forName(OFFSET, false, classLoader).field("packedValue")

        hooks.before(invoke) { _, args ->
            val offset = packed.getLong(args[0] ?: return@before)
            tapX = java.lang.Float.intBitsToFloat((offset ushr 32).toInt())
            tapY = java.lang.Float.intBitsToFloat(offset.toInt())
        }
        hooks.after(invoke) { _, _ -> tapX = Float.NaN }
    }

    private companion object {
        const val INTERACTOR = "com.android.systemui.keyguard.domain.interactor.KeyguardTouchHandlingInteractor"
        const val READONLY_STATE_FLOW = "kotlinx.coroutines.flow.ReadonlyStateFlow"
        const val STATE_FLOW = "kotlinx.coroutines.flow.StateFlowImpl"
        const val OWNED = "pixel_lock_screen_evolved.double_tap_to_sleep"
        const val TAP_CALLBACKS =
            "com.android.systemui.keyguard.ui.composable.LockscreenTouchHandlingKt\$LockscreenTouchHandling\$3\$1\$\$ExternalSyntheticLambda0"
        const val OFFSET = "androidx.compose.ui.geometry.Offset"
    }
}

/**
 * The stock `double_tap_to_sleep` setting (on by default), which Google's own
 * switch in Settings writes once a build offers it, and Wallpaper & style's
 * row writes until then ([PickerLockScreenOptions]).
 */
internal object DoubleTapToSleep {

    const val TITLE = "Double tap to turn off screen"
    const val DESCRIPTION = "Double tap an empty space on the lock screen to turn off the screen."

    private val setting = SecureSwitch("double_tap_to_sleep")

    fun isOn(resolver: ContentResolver): Boolean = setting.isOn(resolver)

    fun set(resolver: ContentResolver, on: Boolean) = setting.set(resolver, on)

    /**
     * Whether a double tap should turn the screen off now: never under touch
     * exploration, where a double tap is how TalkBack activates what it focuses.
     */
    fun isWanted(context: Context): Boolean =
        isOn(context.contentResolver) && context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled != true
}
