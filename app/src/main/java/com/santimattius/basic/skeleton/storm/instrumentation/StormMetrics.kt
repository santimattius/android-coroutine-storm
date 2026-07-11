package com.santimattius.basic.skeleton.storm.instrumentation

import com.santimattius.basic.skeleton.storm.StormConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the two instrumented dispatchers used across the storm lab plus the jank reporter,
 * and exposes a single observable [snapshot] for the UI.
 */
class StormMetrics {

    val defaultInstrumented = InstrumentedDispatcher(Dispatchers.Default, StormConstants.LABEL_DEFAULT)
    val ioInstrumented = InstrumentedDispatcher(Dispatchers.IO, StormConstants.LABEL_IO)
    val jankReporter = JankReporter()

    private val _snapshot = MutableStateFlow(MetricsSnapshot())
    val snapshot: StateFlow<MetricsSnapshot> = _snapshot

    fun refresh() {
        _snapshot.value = MetricsSnapshot(
            defaultPeak = defaultInstrumented.peakConcurrency.get(),
            defaultQueueDelayMs = defaultInstrumented.maxQueueDelayMs.get(),
            ioPeak = ioInstrumented.peakConcurrency.get(),
            ioQueueDelayMs = ioInstrumented.maxQueueDelayMs.get(),
            jankFrames = jankReporter.count.get(),
        )
    }

    fun reset() {
        defaultInstrumented.reset()
        ioInstrumented.reset()
        jankReporter.reset()
        refresh()
    }
}
