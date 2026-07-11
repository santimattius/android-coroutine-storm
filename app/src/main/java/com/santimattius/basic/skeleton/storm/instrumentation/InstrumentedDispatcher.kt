package com.santimattius.basic.skeleton.storm.instrumentation

import com.santimattius.basic.skeleton.storm.StormConstants
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlin.math.max

/**
 * Instrumented view of a [CoroutineDispatcher]. Measures:
 *  - queueDelay: nanos between [dispatch] and the block actually starting to run.
 *  - peak concurrency: the maximum number of blocks running at the same time.
 *  - total dispatch count.
 *
 * Wrapping [kotlinx.coroutines.Dispatchers.IO] this way breaks its thread-pool elasticity;
 * acceptable for measurement in this PoC, not for production use.
 */
class InstrumentedDispatcher(
    private val delegate: CoroutineDispatcher,
    val label: String,
) : CoroutineDispatcher() {

    private val active = AtomicInteger(0)
    val peakConcurrency = AtomicInteger(0)
    val maxQueueDelayMs = AtomicLong(0)
    val totalDispatched = AtomicLong(0)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val enqueuedAtNs = System.nanoTime()
        totalDispatched.incrementAndGet()
        delegate.dispatch(context, instrumentedRunnable(enqueuedAtNs, block))
    }

    private fun instrumentedRunnable(enqueuedAtNs: Long, block: Runnable) = Runnable {
        recordQueueDelay(enqueuedAtNs)
        val concurrentCount = active.incrementAndGet()
        peakConcurrency.updateAndGet { max(it, concurrentCount) }
        try {
            block.run()
        } finally {
            active.decrementAndGet()
        }
    }

    private fun recordQueueDelay(enqueuedAtNs: Long) {
        val queueDelayMs = (System.nanoTime() - enqueuedAtNs) / StormConstants.NANOS_PER_MILLI
        maxQueueDelayMs.updateAndGet { max(it, queueDelayMs) }
    }

    fun reset() {
        peakConcurrency.set(0)
        maxQueueDelayMs.set(0)
        totalDispatched.set(0)
    }
}
