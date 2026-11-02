package com.santimattius.basic.skeleton.runtime.validation

import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/** Records how many tracked blocks ran at the same time. */
internal class ConcurrencyProbe {
    private val inFlight = AtomicInteger(0)
    private val highWater = AtomicInteger(0)

    val peak: Int get() = highWater.get()

    suspend fun <T> track(block: suspend () -> T): T {
        val now = inFlight.incrementAndGet()
        highWater.updateAndGet { max(it, now) }
        try {
            return block()
        } finally {
            inFlight.decrementAndGet()
        }
    }
}
