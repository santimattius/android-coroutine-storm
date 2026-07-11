package com.santimattius.basic.skeleton.storm.instrumentation

import org.junit.Assert.assertEquals
import org.junit.Test

private const val DEFAULT_PEAK = 3
private const val DEFAULT_QUEUE_DELAY_MS = 12L
private const val IO_PEAK = 2
private const val IO_QUEUE_DELAY_MS = 7L
private const val JANK_HITS = 4

class StormMetricsTest {

    private fun stormMetricsWithNonZeroCounters(): StormMetrics {
        val metrics = StormMetrics()
        metrics.defaultInstrumented.peakConcurrency.set(DEFAULT_PEAK)
        metrics.defaultInstrumented.maxQueueDelayMs.set(DEFAULT_QUEUE_DELAY_MS)
        metrics.ioInstrumented.peakConcurrency.set(IO_PEAK)
        metrics.ioInstrumented.maxQueueDelayMs.set(IO_QUEUE_DELAY_MS)
        repeat(JANK_HITS) { metrics.jankReporter.onJank() }
        return metrics
    }

    @Test
    fun `refresh pulls current values from both dispatchers and the jank reporter`() {
        val metrics = stormMetricsWithNonZeroCounters()

        metrics.refresh()

        val snapshot = metrics.snapshot.value
        assertEquals(DEFAULT_PEAK, snapshot.defaultPeak)
        assertEquals(DEFAULT_QUEUE_DELAY_MS, snapshot.defaultQueueDelayMs)
        assertEquals(IO_PEAK, snapshot.ioPeak)
        assertEquals(IO_QUEUE_DELAY_MS, snapshot.ioQueueDelayMs)
        assertEquals(JANK_HITS, snapshot.jankFrames)
    }

    @Test
    fun `reset zeroes both dispatchers and the jank reporter then refreshes`() {
        val metrics = stormMetricsWithNonZeroCounters()

        metrics.reset()

        assertEquals(MetricsSnapshot(), metrics.snapshot.value)
    }
}
