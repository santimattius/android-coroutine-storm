package com.santimattius.basic.skeleton.storm.instrumentation

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val POOL_SIZE = 4
private const val DISPATCH_COUNT = 8
private const val CONTENTION_TASK_COUNT = 6
private const val CONTENTION_POOL_SIZE = 2
private const val TASK_SLEEP_MS = 50L
private const val AWAIT_TIMEOUT_SECONDS = 5L

/**
 * Uses a real fixed thread-pool dispatcher (NOT a virtual-time test dispatcher) so that
 * queueDelay and concurrency measurements reflect true thread contention.
 */
class InstrumentedDispatcherTest {

    private lateinit var executor: ExecutorService

    @After
    fun tearDown() {
        if (this::executor.isInitialized) {
            executor.shutdownNow()
        }
    }

    @Test
    fun `totalDispatched counts every dispatched block and queueDelay is non-negative`() = runBlocking {
        executor = Executors.newFixedThreadPool(POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "test")
        val latch = CountDownLatch(DISPATCH_COUNT)

        coroutineScope {
            repeat(DISPATCH_COUNT) {
                launch(instrumented) { latch.countDown() }
            }
        }
        latch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        assertTrue(instrumented.maxQueueDelayMs.get() >= 0)
        assertEquals(DISPATCH_COUNT.toLong(), instrumented.totalDispatched.get())
    }

    @Test
    fun `peakConcurrency tracks the true observed max overlap under contention`() = runBlocking {
        executor = Executors.newFixedThreadPool(POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "test")
        val readyLatch = CountDownLatch(POOL_SIZE)
        val releaseLatch = CountDownLatch(1)

        coroutineScope {
            repeat(POOL_SIZE) {
                launch(instrumented) {
                    readyLatch.countDown()
                    releaseLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            readyLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            releaseLatch.countDown()
        }

        assertEquals(POOL_SIZE, instrumented.peakConcurrency.get())
    }

    @Test
    fun `maxQueueDelayMs rises when tasks outnumber pool threads`() = runBlocking {
        executor = Executors.newFixedThreadPool(CONTENTION_POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "test")

        coroutineScope {
            repeat(CONTENTION_TASK_COUNT) {
                launch(instrumented) { Thread.sleep(TASK_SLEEP_MS) }
            }
        }

        assertTrue(instrumented.maxQueueDelayMs.get() > 0)
        assertTrue(instrumented.peakConcurrency.get() <= CONTENTION_POOL_SIZE)
    }

    @Test
    fun `reset zeroes all counters`() = runBlocking {
        executor = Executors.newFixedThreadPool(POOL_SIZE)
        val instrumented = InstrumentedDispatcher(executor.asCoroutineDispatcher(), "test")
        coroutineScope {
            launch(instrumented) { /* no-op work */ }
        }

        instrumented.reset()

        assertEquals(0, instrumented.peakConcurrency.get())
        assertEquals(0L, instrumented.maxQueueDelayMs.get())
        assertEquals(0L, instrumented.totalDispatched.get())
    }
}
