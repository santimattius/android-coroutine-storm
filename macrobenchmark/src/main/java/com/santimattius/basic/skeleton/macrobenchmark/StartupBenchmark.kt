package com.santimattius.basic.skeleton.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compares cold-start TTID between the `calm` and `storm` scenario variants. The storm
 * fan-out (or its absence) already happened inside [BuildConfig.TARGET_APP_ID]'s
 * `StormInitializer` before the first frame, so no intent extras are needed here - the
 * variant under test alone determines the behavior.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupTtid() = rule.measureRepeated(
        packageName = BuildConfig.TARGET_APP_ID,
        metrics = listOf(StartupTimingMetric()),
        iterations = BenchmarkConfig.ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
