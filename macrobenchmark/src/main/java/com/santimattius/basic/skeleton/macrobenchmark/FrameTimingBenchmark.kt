package com.santimattius.basic.skeleton.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures frame timing while the spinner runs. On the `storm` variant, the pre-frame
 * fan-out already ran inside [BuildConfig.TARGET_APP_ID]'s `StormInitializer`; this
 * benchmark measures its ongoing effect on frame durations while the app settles.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun frameTiming() = rule.measureRepeated(
        packageName = BuildConfig.TARGET_APP_ID,
        metrics = listOf(FrameTimingMetric()),
        iterations = BenchmarkConfig.ITERATIONS,
        startupMode = StartupMode.WARM,
    ) {
        startActivityAndWait()
        Thread.sleep(BenchmarkConfig.ANIMATION_SETTLE_MS)
    }
}
