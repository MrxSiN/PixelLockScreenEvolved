package my.github.MrxSiN.pixellockscreenevolved.depth

import java.util.concurrent.Executor

import org.junit.Assert.assertEquals
import org.junit.Test

class NewestOnlyWorkerTest {

    /** Runs a submitted task only when told to, as a busy worker thread would. */
    private class HeldExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks += command
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private val executor = HeldExecutor()
    private val ran = mutableListOf<Int>()
    private val superseded = mutableListOf<Int>()
    private val worker = NewestOnlyWorker<Int>(executor, { ran += it }, { superseded += it })

    @Test
    fun aJobAloneRuns() {
        worker.submit(1)
        executor.runAll()

        assertEquals(listOf(1), ran)
        assertEquals(emptyList<Int>(), superseded)
    }

    @Test
    fun onlyTheNewestWaitingJobRunsAfterTheOneUnderWay() {
        worker.submit(1)
        worker.submit(2)
        worker.submit(3)
        worker.submit(4)
        executor.runAll()

        assertEquals(listOf(1, 4), ran)
        assertEquals(listOf(2, 3), superseded)
    }

    @Test
    fun aJobArrivingWhileOneRunsIsRunNext() {
        lateinit var worker: NewestOnlyWorker<Int>
        worker = NewestOnlyWorker(executor, { job ->
            ran += job
            if (job == 1) worker.submit(2)
        }, { superseded += it })

        worker.submit(1)
        executor.runAll()

        assertEquals(listOf(1, 2), ran)
        assertEquals(0, executor.tasks.size)
    }

    @Test
    fun theWorkerIsReadyAgainOnceIdle() {
        worker.submit(1)
        executor.runAll()
        worker.submit(2)
        executor.runAll()

        assertEquals(listOf(1, 2), ran)
        assertEquals(emptyList<Int>(), superseded)
    }
}
