package my.github.MrxSiN.pixellockscreenevolved.host

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * SystemUI's unlock state and its status bar clock, reached through its class
 * loader. HOOK_NOTES.md records the shapes.
 */
internal class KeyguardUnlockApi(classLoader: ClassLoader) {

    private val unlockControllerType = load("com.android.systemui.keyguard.KeyguardUnlockAnimationController", classLoader)

    /**
     * Called on the unlock animation controller as a swipe drags the keyguard
     * away, as the keyguard starts going away, and as the app behind it starts
     * to appear: every way an unlock begins.
     */
    val unlockProgressed: List<Method> = listOf(
        unlockControllerType.getDeclaredMethod("onKeyguardDismissAmountChanged"),
        unlockControllerType.getDeclaredMethod("onKeyguardGoingAwayChanged"),
        unlockControllerType.declaredMethods.firstOrNull { it.name == "notifyStartSurfaceBehindRemoteAnimation" }
            ?: throw NoSuchMethodException("KeyguardUnlockAnimationController.notifyStartSurfaceBehindRemoteAnimation"),
    )

    private val keyguardState = unlockControllerType.field("keyguardStateController")
    private val stateType = load("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", classLoader)
    private val showing = stateType.field("mShowing")
    private val goingAway = stateType.field("mKeyguardGoingAway")
    private val dismissAmount = stateType.field("mDismissAmount")
    private val flingingToDismiss = stateType.field("mFlingingToDismissKeyguard")

    private val statusBarClockType = load("com.android.systemui.statusbar.policy.Clock", classLoader)

    /** Every SystemUI clock text view, in the status bar and elsewhere, as it is attached and detached. */
    val clockAttached: Method = statusBarClockType.getDeclaredMethod("onAttachedToWindow")
    val clockDetached: Method = statusBarClockType.getDeclaredMethod("onDetachedFromWindow")

    /** Whether the keyguard is up, in the state the unlock animation controller reads. */
    fun isKeyguardShowing(unlockController: Any): Boolean =
        state(unlockController)?.let { showing.getBoolean(it) } ?: false

    /** Whether the unlock can no longer be called back: the keyguard is flung or going away. */
    fun isCommitted(unlockController: Any): Boolean {
        val state = state(unlockController) ?: return false
        return goingAway.getBoolean(state) || flingingToDismiss.getBoolean(state)
    }

    /** Whether the keyguard is being dragged, flung or animated away. */
    fun isUnlocking(unlockController: Any): Boolean {
        val state = state(unlockController) ?: return false
        return goingAway.getBoolean(state) || flingingToDismiss.getBoolean(state) || dismissAmount.getFloat(state) > 0f
    }

    private fun state(unlockController: Any): Any? =
        keyguardState.get(unlockController)?.takeIf(stateType::isInstance)

    private companion object {
        fun load(name: String, classLoader: ClassLoader): Class<*> = Class.forName(name, false, classLoader)

        fun Class<*>.field(name: String): Field = getDeclaredField(name).apply { isAccessible = true }
    }
}
