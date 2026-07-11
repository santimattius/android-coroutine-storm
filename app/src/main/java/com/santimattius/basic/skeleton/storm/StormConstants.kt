package com.santimattius.basic.skeleton.storm

/**
 * Centralizes numeric and string literals used across the storm subpackage so that
 * detekt's `MagicNumber` rule stays clean and the values are documented in one place.
 */
object StormConstants {
    const val STORM_COROUTINE_COUNT = 2_000
    const val STRUCTURED_PERMITS = 4
    const val CPU_BURN_ITERATIONS = 2_000_000
    const val HASH_MULTIPLIER = 31L
    const val NANOS_PER_MILLI = 1_000_000L
    const val LABEL_DEFAULT = "Default"
    const val LABEL_IO = "IO"
    const val SPIN_DURATION_MS = 1_000
    const val FULL_ROTATION_DEGREES = 360f
}
