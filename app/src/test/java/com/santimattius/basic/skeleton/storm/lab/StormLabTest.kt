package com.santimattius.basic.skeleton.storm.lab

import com.santimattius.basic.skeleton.storm.instrumentation.InstrumentedDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import io.github.santimattius.structured.runtime.parallel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val STRUCTURED_COUNT = 200
private const val STRUCTURED_PERMITS = 4
private const val UNLEASH_COUNT = 32
private const val REAL_POOL_SIZE = 8

/**
 * The one hard correctness invariant of this PoC: [StormLab.structured] must never let
 * concurrency exceed the configured permits, even under real thread contention.
 * Uses a real fixed thread-pool dispatcher, NOT a virtual-time test dispatcher.
 */
class StormLabTest {

    private lateinit var executor: ExecutorService

    @After
    fun tearDown() {
        if (this::executor.isInitialized) {
            executor.shutdownNow()
        }
    }

    @Test
    fun `structured never exceeds the permit bound under contention`() = runBlocking {
        executor = Executors.newFixedThreadPool(REAL_POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "structured-test")

        StormLab.structured(instrumented, STRUCTURED_COUNT, STRUCTURED_PERMITS)

        assertTrue(instrumented.peakConcurrency.get() <= STRUCTURED_PERMITS)
        assertTrue(instrumented.totalDispatched.get() > 0)
    }

    @Test
    fun `unleashStorm has no concurrency ceiling`() = runBlocking {
        executor = Executors.newFixedThreadPool(REAL_POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "unleash-test")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        StormLab.unleashStorm(scope, instrumented, UNLEASH_COUNT)
        scope.coroutineContext.job.children.forEach { it.join() }

        assertTrue(instrumented.peakConcurrency.get() > STRUCTURED_PERMITS)
    }

    @Test
    fun `parallelBounded runs every task and returns results in input order`() = runBlocking {
        executor = Executors.newFixedThreadPool(REAL_POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "parallel-test")

        val results = StormLab.parallelBounded(instrumented, STRUCTURED_COUNT, STRUCTURED_PERMITS)

        assertEquals(STRUCTURED_COUNT, results.size)
        assertTrue(instrumented.totalDispatched.get() > 0)
        println("parallelBounded observed peak=${instrumented.peakConcurrency.get()} permits=$STRUCTURED_PERMITS")
    }

    @Test
    fun `parallelBounded actual task concurrency never exceeds the permits`() = runBlocking {
        executor = Executors.newFixedThreadPool(REAL_POOL_SIZE)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val tasks = List<suspend () -> Unit>(STRUCTURED_COUNT) {
            {
                val now = running.incrementAndGet()
                peak.accumulateAndGet(now, ::maxOf)
                running.decrementAndGet()
            }
        }

        withContext(executor.asCoroutineDispatcher()) { tasks.parallel(concurrency = STRUCTURED_PERMITS) }

        assertTrue("peak=${peak.get()}", peak.get() <= STRUCTURED_PERMITS)
    }
}

