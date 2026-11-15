package com.santimattius.basic.skeleton.storm.lab

import com.santimattius.basic.skeleton.storm.StormConstants
import io.github.santimattius.structured.runtime.parallel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Side-by-side comparison of the storm anti-pattern and its structured-concurrency fix.
 */
object StormLab {

    @Volatile
    var blackHole: Long = 0 // Prevents the compiler from eliminating burnCpu as dead code.

    /**
     * CPU-bound work that forces the scheduler to compete for cores.
     */
    private fun burnCpu(iterations: Int = StormConstants.CPU_BURN_ITERATIONS): Long {
        var acc = 0L
        for (i in 0 until iterations) {
            acc += (i * StormConstants.HASH_MULTIPLIER) xor (i.toLong() shl 1)
        }
        return acc
    }

    /**
     * Storm: fans out [count] coroutines against [dispatcher] with no concurrency ceiling.
     */
    fun unleashStorm(scope: CoroutineScope, dispatcher: CoroutineDispatcher, count: Int) {
        repeat(count) {
            scope.launch(dispatcher) { blackHole += burnCpu() }
        }
    }

    /**
     * Structured fix: same total work, bounded to [permits] concurrent executions.
     *
     * Launches exactly [permits] coroutines directly on [dispatcher], each pulling items from
     * a shared index and running its entire share of the work as ONE continuous, non-suspending
     * loop (no `withContext`, no [kotlinx.coroutines.sync.Semaphore], no
     * [CoroutineDispatcher.limitedParallelism]). Every alternative that hands work off between
     * threads mid-stream — a semaphore acquired/released around the dispatched block, a
     * worker-pool that re-enters the dispatcher via `withContext` per item, or
     * `limitedParallelism` (which self-redispatches its "Worker" every 16 tasks for fairness,
     * see kotlinx.coroutines.internal.LimitedDispatcher) — lets the OLD Runnable's tail
     * (about to decrement [com.santimattius.basic.skeleton.storm.instrumentation.InstrumentedDispatcher]'s
     * active count) race against the NEW Runnable's head (about to increment it) on a DIFFERENT
     * real thread, because the hand-off is triggered from inside the coroutine machinery that
     * unwinds strictly before the wrapper's `finally` runs. With exactly [permits] non-suspending
     * loops, [dispatcher] is only ever entered [permits] times total, so concurrency is bounded
     * by construction with no hand-off race at all.
     */
    suspend fun structured(dispatcher: CoroutineDispatcher, count: Int, permits: Int) = coroutineScope {
        val nextIndex = AtomicInteger(0)
        repeat(permits) {
            launch(dispatcher) {
                while (true) {
                    val index = nextIndex.getAndIncrement()
                    if (index >= count) break
                    blackHole += burnCpu()
                }
            }
        }
    }

    /**
     * Third scenario: same total work bounded with the runtime's `parallel(concurrency = permits)`.
     *
     * Unlike [structured], this hands work off through the runtime's semaphore, so the
     * [com.santimattius.basic.skeleton.storm.instrumentation.InstrumentedDispatcher] peak can read
     * slightly above [permits] (the hand-off race described on [structured]); the bound itself is
     * enforced by the semaphore, not by the dispatcher count.
     */
    suspend fun parallelBounded(dispatcher: CoroutineDispatcher, count: Int, permits: Int): List<Long> =
        withContext(dispatcher) {
            List<suspend () -> Long>(count) { { burnCpu().also { blackHole += it } } }
                .parallel(concurrency = permits)
        }
}
