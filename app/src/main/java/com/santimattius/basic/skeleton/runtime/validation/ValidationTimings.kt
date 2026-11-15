package com.santimattius.basic.skeleton.runtime.validation

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Centralizes the literals used by the validations so detekt's `MagicNumber` rule stays clean.
 * Delays are short on purpose: on a device they run in real time, in unit tests in virtual time.
 */
internal object ValidationTimings {
    val SHORT = 20.milliseconds
    val MEDIUM = 50.milliseconds
    val HEDGE_AFTER = 30.milliseconds
    val TIMEOUT = 40.milliseconds
    val SAMPLER_INTERVAL = 40.milliseconds
    val TINY = 1.nanoseconds
    const val SHORT_MS = 20L
    const val GRACE_MS = 300L
    const val GRACE_PROBE_MS = 60L
    const val GRACE_EXPIRY_MS = 600L
    const val DEFAULT_GRACE_PROBE_MS = 200L
    const val SAMPLER_WINDOW_MS = 200L
    const val NEVER_MS = 60_000L
    const val AWAIT_MS = 5_000L
    const val NESTED_TIMEOUT_MS = 10L
}

internal object ValidationSizes {
    const val FEW = 3
    const val SOME = 5
    const val TASKS = 6
    const val BOUND = 2
    const val BURST = 100
    const val DEFAULT_BUFFER = 64
    const val SMALL_BUFFER = 8
    const val WORKERS = 8
    const val INCREMENTS = 500
    const val RING_CAPACITY = 4
    const val RING_BIG = 512
    const val DISPATCHES = 5
    const val MIN_FIRST_PARALLELISM = 2
    const val P95 = 0.95
}
