package com.santimattius.basic.skeleton.storm.lab

import com.santimattius.basic.skeleton.storm.StormConstants
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
     * Uses exactly [permits] worker coroutines pulling from a shared index instead of a
     * [kotlinx.coroutines.sync.Semaphore]. A semaphore acquired/released around the dispatched
     * block lets multiple real threads race to check it at once, each briefly counted as
     * "active" by [com.santimattius.basic.skeleton.storm.instrumentation.InstrumentedDispatcher]
     * before any of them suspends or releases — momentarily exceeding [permits] under real
     * contention. With a fixed worker pool, each worker issues at most one [dispatcher] call
     * at a time and only starts its next call after the previous one fully returns, so
     * concurrency is bounded by construction rather than by a racy gate.
     */
    suspend fun structured(dispatcher: CoroutineDispatcher, count: Int, permits: Int) = coroutineScope {
        val nextIndex = AtomicInteger(0)
        repeat(permits) {
            launch {
                while (nextIndex.getAndIncrement() < count) {
                    withContext(dispatcher) { blackHole += burnCpu() }
                }
            }
        }
    }
}
