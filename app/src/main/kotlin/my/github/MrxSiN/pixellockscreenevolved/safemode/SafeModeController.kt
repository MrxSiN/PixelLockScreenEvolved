package my.github.MrxSiN.pixellockscreenevolved.safemode

import android.os.Process

import my.github.MrxSiN.pixellockscreenevolved.core.Logger
import my.github.MrxSiN.pixellockscreenevolved.diagnostics.SafeModeAnswer

/**
 * Carries out an answer for what Safe Mode holds off in this host.
 *
 * Keeping the features off only stops the asking. Restoring them has to start
 * the host again, because a feature is installed once, as the host starts.
 */
class SafeModeController(
    private val store: SafeModeStore,
    private val logger: Logger,
    /** Called once what is held off or answered has changed, so the change can be shown. */
    private val onChanged: () -> Unit,
    private val restart: () -> Unit = { Process.killProcess(Process.myPid()) },
) {

    fun answer(answer: SafeModeAnswer) {
        val held = store.disabled()
        if (held.isEmpty()) return
        when (answer) {
            SafeModeAnswer.KEEP_DISABLED -> {
                store.setAnswered(true)
                logger.info("Safe Mode: keeping ${held.map { it.title }} off")
                onChanged()
            }
            SafeModeAnswer.RESTORE -> {
                store.setDisabled(emptySet())
                store.clearCrashes()
                store.setAnswered(true)
                logger.info("Safe Mode: restoring ${held.map { it.title }}; restarting to install them")
                onChanged()
                restart()
            }
        }
    }
}
