package com.santimattius.basic.skeleton.storm.instrumentation

/**
 * Immutable point-in-time view of the storm instrumentation, rendered by the UI.
 */
data class MetricsSnapshot(
    val defaultPeak: Int = 0,
    val defaultQueueDelayMs: Long = 0,
    val ioPeak: Int = 0,
    val ioQueueDelayMs: Long = 0,
    val jankFrames: Int = 0,
)
