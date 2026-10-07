package my.github.MrxSiN.pixellockscreenevolved.depth

import java.util.concurrent.Executor

/**
 * Runs jobs one at a time on [executor], and keeps only the newest job waiting.
 *
 * A job already running always finishes, since a segmentation cannot be stopped
 * part way. A job still waiting when a newer one arrives is handed to
 * [onSuperseded] instead of run: only the newest photo's mask is wanted, and a
 * run takes seconds of the CPU. Several wallpaper changes in a row so cost at
 * most two runs, the one under way and the last one asked for.
 */
class NewestOnlyWorker<T : Any>(
    private val executor: Executor,
    private val run: (T) -> Unit,
    private val onSuperseded: (T) -> Unit,
) {

    private val lock = Any()
    private var running = false
    private var waiting: T? = null

    fun submit(job: T) {
        val superseded = synchronized(lock) {
            if (!running) {
                running = true
                executor.execute { drain(job) }
                return
            }
            waiting.also { waiting = job }
        }
        superseded?.let(onSuperseded)
    }

    private fun drain(first: T) {
        var job: T? = first
        while (job != null) {
            run(job)
            job = synchronized(lock) {
                waiting.also {
                    waiting = null
                    running = it != null
                }
            }
        }
    }
}
