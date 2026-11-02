package com.santimattius.basic.skeleton.runtime.validation

import android.os.Build
import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.AndroidTraceSink
import io.github.santimattius.structured.runtime.DefaultDispatcherProvider
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.PoolSampler
import io.github.santimattius.structured.runtime.RingBufferSink
import io.github.santimattius.structured.runtime.SignalThresholds
import io.github.santimattius.structured.runtime.StatsSink
import io.github.santimattius.structured.runtime.TraceCapabilities
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import io.github.santimattius.structured.runtime.TracingDispatcher
import io.github.santimattius.structured.runtime.parallelism
import io.github.santimattius.structured.runtime.platformCapabilities
import io.github.santimattius.structured.runtime.platformTraceSink
import io.github.santimattius.structured.runtime.runCatchingCancellable
import io.github.santimattius.structured.runtime.signals
import io.github.santimattius.structured.runtime.traced
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/** `PoolSampler`, signals, `parallelism`, capabilities, `AndroidTraceSink`, `TracingDispatcher`, `TraceSink.None`. */
internal class TracingDiagnosticsValidations(private val dispatchers: DispatcherProvider) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "PoolSampler: sampleOnce + launchIn + signals()") { poolSampler() },
        ValidationCase(GROUP, "DispatcherMetrics: histograms and signals(thresholds)") { metricsAndSignals() },
        ValidationCase(GROUP, "TracedDispatcherProvider.parallelism: inferred limits") { inferredParallelism() },
        ValidationCase(GROUP, "platformCapabilities / platformTraceSink / AndroidTraceSink") { androidPlatform() },
        ValidationCase(GROUP, "TraceSink.None + TracingDispatcher direct use") { noneAndDirectDispatcher() },
    )

    private suspend fun poolSampler(): String {
        val stats = StatsSink()
        val ring = RingBufferSink(ValidationSizes.RING_BIG, TraceLevel.Pool)
        val traced = dispatchers.traced(stats)
        repeat(ValidationSizes.DISPATCHES) { withContext(traced.io) { } }
        awaitUntil("io drained") { (stats.metrics("io")?.finished ?: 0L) >= ValidationSizes.DISPATCHES }

        val sampler = PoolSampler(stats, ring, traced::parallelism)
        sampler.sampleOnce()
        val manual = ring.snapshot().filterIsInstance<TraceEvent.PoolSample>().single { it.dispatcher == "io" }
        check(manual.queued >= ValidationSizes.DISPATCHES && manual.pending == 0L) { "sample=$manual" }

        ring.clear()
        coroutineScope {
            val job = sampler.launchIn(this, ValidationTimings.SAMPLER_INTERVAL)
            delay(ValidationTimings.SAMPLER_WINDOW_MS)
            job.cancelAndJoin()
        }
        val periodic = ring.snapshot().filterIsInstance<TraceEvent.PoolSample>().filter { it.dispatcher == "io" }
        check(periodic.size >= ValidationSizes.BOUND) { "only ${periodic.size} periodic samples" }
        val bad = coroutineScope { runCatchingCancellable { sampler.launchIn(this, Duration.ZERO) } }
        check(bad.exceptionOrNull() is IllegalArgumentException) { "interval=0 -> $bad" }
        val signals = periodic.signals()
        return "manual sample ok; ${periodic.size} periodic samples; signals()=${signals.size}; interval=0 rejected"
    }

    private suspend fun metricsAndSignals(): String {
        val stats = StatsSink(TraceLevel.Coroutine)
        val traced = dispatchers.traced(stats)
        repeat(ValidationSizes.DISPATCHES) { withContext(traced.default) { delay(ValidationTimings.SHORT) } }
        awaitUntil("default drained") { (stats.metrics("default")?.finished ?: 0L) >= ValidationSizes.DISPATCHES }
        val metrics = checkNotNull(stats.metrics("default")) { "no default metrics" }
        val p95 = metrics.queueDelay.percentile(ValidationSizes.P95)
        check(metrics.queueDelay.count >= ValidationSizes.DISPATCHES) { "queueDelay.count=${metrics.queueDelay.count}" }
        check(metrics.runTime.count >= ValidationSizes.DISPATCHES && metrics.longestRun >= Duration.ZERO) { "runTime" }
        val histogram = metrics.queueDelay
        // One overflow bucket past the last upper bound (not mentioned in the README).
        check(histogram.bucketCounts.size == histogram.bucketUpperBounds.size + 1) { "bucket shape" }
        check(histogram.bucketCounts.sum() == histogram.count) { "buckets do not add up to count" }
        val badPercentile = runCatchingCancellable { histogram.percentile(2.0) }
        check(badPercentile.exceptionOrNull() is IllegalArgumentException) { "percentile(2.0) -> $badPercentile" }
        val relaxed = metrics.signals(SignalThresholds(starvationQueueDelayP95 = ValidationTimings.TINY))
        val zero = runCatchingCancellable { SignalThresholds(starvationQueueDelayP95 = Duration.ZERO) }
        check(zero.exceptionOrNull() is IllegalArgumentException) { "zero threshold accepted: $zero" }
        return "queueDelay p95=$p95 max=${metrics.queueDelay.max}; longestRun=${metrics.longestRun}; " +
            "default signals=${metrics.signals().size}, with 1ns thresholds=${relaxed.size}; zero threshold rejected"
    }

    private fun inferredParallelism(): String {
        val traced = DefaultDispatcherProvider.traced(TraceSink.None)
        val default = traced.parallelism("default")
        val io = traced.parallelism("io")
        check(default != null && default >= ValidationSizes.MIN_FIRST_PARALLELISM) { "default=$default" }
        check(io != null && io >= default) { "io=$io" }
        check(traced.parallelism("main") == null) { "main should be unknown" }
        val custom = dispatchers.traced(TraceSink.None)
        check(dispatchers === DefaultDispatcherProvider || custom.parallelism("io") == null) { "custom base inferred" }
        return "default=$default, io=$io, main=null, non-default base=null"
    }

    private suspend fun androidPlatform(): String {
        val caps = platformCapabilities
        check(caps.threadIdentity && caps.systemTraceIntegration) { "caps=${caps.describe()}" }
        check(!caps.flows) { "flows should be false on Android" }
        check(caps.counters == (Build.VERSION.SDK_INT >= API_COUNTERS)) {
            "counters=${caps.counters} sdk=${Build.VERSION.SDK_INT}"
        }
        check(platformTraceSink() is AndroidTraceSink) { "platformTraceSink()=${platformTraceSink()}" }

        val android = AndroidTraceSink()
        val traced = dispatchers.traced(android)
        val value = withContext(traced.io + CoroutineName("fetch")) {
            delay(ValidationTimings.SHORT)
            ANSWER
        }
        check(value == ANSWER) { "work through AndroidTraceSink returned $value" }
        val enabled = TraceLevel.entries.associateWith { android.isEnabled(it) }
        return "caps ${caps.describe()}, sdk=${Build.VERSION.SDK_INT}; AndroidTraceSink enabled=$enabled " +
            "(true only while a system trace records)"
    }

    private suspend fun noneAndDirectDispatcher(): String {
        check(TraceLevel.entries.none { TraceSink.None.isEnabled(it) }) { "None should be disabled" }
        val untouched = withContext(dispatchers.traced(TraceSink.None).io) { "forwarded" }
        check(untouched == "forwarded") { "None sink altered the block" }
        val ring = RingBufferSink(ValidationSizes.RING_BIG, TraceLevel.Coroutine)
        val direct = TracingDispatcher(dispatchers.default, "direct", ring)
        withContext(direct) { delay(ValidationTimings.SHORT) }
        awaitUntil("direct events") { ring.snapshot().any { it is TraceEvent.Finished && it.dispatcher == "direct" } }
        return "disabled sink forwards untouched; TracingDispatcher('${direct.label}') " +
            "emitted ${ring.snapshot().size} events"
    }

    private fun TraceCapabilities.describe(): String =
        "[thread=$threadIdentity system=$systemTraceIntegration counters=$counters flows=$flows]"

    private companion object {
        const val GROUP = "Tracing"
        const val API_COUNTERS = 29
        const val ANSWER = "forwarded"
    }
}
