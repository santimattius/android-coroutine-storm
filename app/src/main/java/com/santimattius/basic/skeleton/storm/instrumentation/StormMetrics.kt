package com.santimattius.basic.skeleton.storm.instrumentation

import com.santimattius.basic.skeleton.storm.StormConstants
import io.github.santimattius.structured.runtime.AndroidTraceSink
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.traced
import kotlinx.coroutines.CoroutineDispatcher
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

    /**
     * The instrumented dispatchers wrapped with the runtime's tracing, so a system trace shows
     * `structured.default.*` / `structured.io.*` slices and pending/running counter tracks.
     * [AndroidTraceSink] stays disabled (near-zero cost) unless a trace session is recording.
     */
    private val traced = object : DispatcherProvider {
        override val main: CoroutineDispatcher get() = Dispatchers.Main
        override val io: CoroutineDispatcher = ioInstrumented
        override val default: CoroutineDispatcher = defaultInstrumented
        override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
    }.traced(AndroidTraceSink())

    val defaultTraced: CoroutineDispatcher get() = traced.default
    val ioTraced: CoroutineDispatcher get() = traced.io

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
