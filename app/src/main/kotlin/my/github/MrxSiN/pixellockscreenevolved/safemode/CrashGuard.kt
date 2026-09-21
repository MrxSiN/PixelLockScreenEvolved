package my.github.MrxSiN.pixellockscreenevolved.safemode

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.Feature

/**
 * Notices a host that keeps crashing, and holds this module's features in it
 * off before they are installed again.
 *
 * A monthly update can move what a hook reaches into, and a host can then die
 * before it draws anything, restart, and die again. So every crash is written
 * down as it happens, and the next start, before any feature is installed,
 * looks at how many there were.
 *
 * Only a crash that reaches the host's default handler is counted. That is
 * every exception a hook throws on the host's threads; a native crash or an ANR
 * is not seen, and is not what a moved hook causes.
 */
class CrashGuard(
    private val store: SafeModeStore,
    private val logger: Logger,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Records every uncaught crash, then lets the host's own handler end the process. */
    fun watch() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { store.recordCrash(clock()) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Holds [features] off when the host is crash looping, and answers which of
     * them that newly held off. Call before any feature installs.
     */
    fun recover(features: Collection<Feature>): Set<Feature> {
        if (!CrashLoop.isLooping(store.crashes(), clock())) return emptySet()
        store.clearCrashes()

        val held = store.disabled()
        val newlyHeld = features.toSet() - held
        if (newlyHeld.isEmpty()) {
            logger.warn("Crash looping with every feature of this module already held off; the crashes are not this module's")
            return emptySet()
        }
        store.setDisabled(held + newlyHeld)
        store.setAnswered(false)
        logger.warn("Safe Mode: crashed ${CrashLoop.CRASHES} times within a minute; held off ${newlyHeld.map { it.title }}")
        return newlyHeld
    }
}
