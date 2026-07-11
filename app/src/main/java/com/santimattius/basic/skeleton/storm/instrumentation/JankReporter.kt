package com.santimattius.basic.skeleton.storm.instrumentation

import java.util.concurrent.atomic.AtomicInteger

/**
 * Atomic counter of janky frames, fed by [androidx.metrics.performance.JankStats].
 */
class JankReporter {
    val count = AtomicInteger(0)

    fun onJank() {
        count.incrementAndGet()
    }

    fun reset() {
        count.set(0)
    }
}
