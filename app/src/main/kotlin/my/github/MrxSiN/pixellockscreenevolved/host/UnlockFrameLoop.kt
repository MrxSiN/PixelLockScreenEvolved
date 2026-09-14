package my.github.MrxSiN.pixellockscreenevolved.host

import android.view.Choreographer

import java.util.concurrent.CopyOnWriteArrayList

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.hook.Hooks
import my.github.MrxSiN.pixellockscreenevolved.hook.HostPatch

/**
 * Runs whatever moves with an unlock on every frame of it, from the moment one
 * begins until each says it is done.
 *
 * SystemUI tells its unlock animation controller of every way an unlock begins
 * ([KeyguardUnlockApi.unlockProgressed]). Those methods are hooked here once
 * for everything that follows an unlock, since a method hooked a second time
 * by the same module replaces the first hook. The first such call while the
 * keyguard is being dismissed starts each [add]ed motion's frames: its step is
 * given the unlock state, the controller and each frame's time until it
 * answers false, when its stop runs.
 */
internal class UnlockFrameLoop(
    private val hooks: Hooks,
    private val logger: Logger,
) : HostPatch {

    private val motions = CopyOnWriteArrayList<Motion>()
    private var unlockController: Any? = null

    /** Follows every unlock with [step] and ends with [stop]; [name] is what failures are reported as. */
    fun add(
        name: String,
        step: (api: KeyguardUnlockApi, unlockController: Any, frameTimeNanos: Long) -> Boolean,
        stop: () -> Unit,
    ) {
        motions += Motion(name, step, stop)
    }

    override fun install(classLoader: ClassLoader) {
        val api = try {
            KeyguardUnlockApi(classLoader)
        } catch (error: ReflectiveOperationException) {
            logger.warn("Unlock state not found; nothing moves with an unlock", error)
            return
        }
        api.unlockProgressed.forEach { method ->
            hooks.after(method) { controller, _ ->
                unlockController = controller
                if (controller != null && api.isUnlocking(controller)) motions.forEach { it.start(api) }
            }
        }
    }

    private inner class Motion(
        val name: String,
        val step: (KeyguardUnlockApi, Any, Long) -> Boolean,
        val stop: () -> Unit,
    ) {
        private var running = false
        private lateinit var api: KeyguardUnlockApi

        private val onFrame = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                val controller = unlockController
                val more = controller != null && runCatching { step(api, controller, frameTimeNanos) }
                    .onFailure { logger.warn("$name failed", it) }
                    .getOrDefault(false)
                if (more) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    runCatching(stop).onFailure { logger.warn("$name could not stop", it) }
                    running = false
                }
            }
        }

        fun start(api: KeyguardUnlockApi) {
            if (running) return
            running = true
            this.api = api
            Choreographer.getInstance().postFrameCallback(onFrame)
        }
    }
}
