package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.CompositeSink
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.LoggingSink
import io.github.santimattius.structured.runtime.RingBufferSink
import io.github.santimattius.structured.runtime.StatsSink
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSpan
import io.github.santimattius.structured.runtime.parallel
import io.github.santimattius.structured.runtime.traced
import io.github.santimattius.structured.runtime.withTraceSpan
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/** `traced()`, `StatsSink`, `RingBufferSink`, `LoggingSink`, `CompositeSink`, spans (DISPATCH_005 diagnostics). */
internal class TracingValidations(private val dispatchers: DispatcherProvider) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "traced + StatsSink: queued = started = finished") { statsSink() },
        ValidationCase(GROUP, "RingBufferSink + LoggingSink via CompositeSink") { ringLoggingComposite() },
        ValidationCase(GROUP, "withTraceSpan: nesting and parallel child span") { spans() },
    )

    private suspend fun statsSink(): String {
        val stats = StatsSink()
        val traced = dispatchers.traced(stats)
        repeat(ValidationSizes.DISPATCHES) {
            withContext(traced.io) { }
            withContext(traced.default) { }
        }
        awaitUntil("Finished events") {
            listOf("io", "default").all { (stats.metrics(it)?.finished ?: 0L) >= ValidationSizes.DISPATCHES }
        }
        val io = checkNotNull(stats.metrics("io")) { "no metrics for io" }
        check(io.queued == io.started && io.started == io.finished) { "io counters $io" }
        check(io.pending == 0L && io.running == 0 && io.failed == 0L) { "io not drained: $io" }
        check(io.threadsObserved >= 1) { "no thread identity recorded" }
        check(stats.metrics("main") == null) { "main reported although never used" }
        stats.reset()
        check(stats.metrics().isEmpty() || stats.metrics().all { it.queued == 0L }) { "reset left counters" }
        return "io: queued=${io.queued} started=${io.started} finished=${io.finished} " +
            "threads=${io.threadsObserved} peak=${io.peakRunning}; labels=${listOf("io", "default")}"
    }

    private suspend fun ringLoggingComposite(): String {
        val ring = RingBufferSink(ValidationSizes.RING_BIG, TraceLevel.Coroutine)
        val logs = CopyOnWriteArrayList<String>()
        val logging = LoggingSink(TraceLevel.Dispatch) { logs += it }
        val traced = dispatchers.traced(CompositeSink(ring, logging))
        repeat(ValidationSizes.FEW) { withContext(traced.io + CoroutineName("fetch")) { } }
        awaitUntil("3 Finished events") { ring.snapshot().count { it is TraceEvent.Finished } >= ValidationSizes.FEW }

        val byDispatch = ring.snapshot().groupBy { it.dispatchIdOrNull() }.filterKeys { it != null }
        check(byDispatch.isNotEmpty()) { "no dispatch events" }
        byDispatch.forEach { (id, events) ->
            val kinds = events.sortedBy { it.timestampNanos }.map { it::class.simpleName }
            check(kinds.take(ValidationSizes.FEW) == listOf("Queued", "Started", "Finished")) {
                "dispatchId=$id order was $kinds"
            }
        }
        check(logs.isNotEmpty()) { "LoggingSink received nothing" }

        val small = RingBufferSink(ValidationSizes.RING_CAPACITY, TraceLevel.Coroutine)
        val smallTraced = dispatchers.traced(small)
        repeat(ValidationSizes.FEW) { withContext(smallTraced.default) { } }
        awaitUntil("small ring full") { small.snapshot().size == ValidationSizes.RING_CAPACITY }
        small.clear()
        check(small.snapshot().isEmpty()) { "clear() left ${small.snapshot().size} events" }
        return "${byDispatch.size} dispatch ids, each Queued<Started<Finished; ${logs.size} log lines; " +
            "ring(capacity=4) capped and cleared"
    }

    private suspend fun spans(): String {
        val ring = RingBufferSink(ValidationSizes.RING_BIG, TraceLevel.Coroutine)
        val traced = dispatchers.traced(ring)
        val (outer, inner) = withTraceSpan("outer") {
            val outerSpan = checkNotNull(currentCoroutineContext()[TraceSpan]) { "no span in context" }
            withContext(traced.default) { 0 }
            val innerSpan = withTraceSpan("inner") {
                withContext(traced.io) { 0 }
                checkNotNull(currentCoroutineContext()[TraceSpan])
            }
            listOf<suspend () -> Int>(
                { withContext(traced.default) { 1 } },
                { withContext(traced.io) { 2 } },
            ).parallel()
            outerSpan to innerSpan
        }
        withContext(traced.io) { 0 } // outside any span
        check(outer.parentId == null && inner.parentId == outer.id) { "span ids: $outer / $inner" }
        awaitUntil("span events") { ring.snapshot().count { it is TraceEvent.Finished } >= ValidationSizes.SOME }

        val queued = ring.snapshot().filterIsInstance<TraceEvent.Queued>()
        check(queued.any { it.spanName == "outer" && it.spanId == outer.id && it.parentSpanId == null }) { "outer" }
        check(queued.any { it.spanName == "inner" && it.parentSpanId == outer.id }) { "inner not linked to outer" }
        check(queued.any { it.spanName == "parallel" && it.parentSpanId == outer.id }) { "parallel child span missing" }
        check(queued.any { it.spanId == null && it.spanName == null }) { "span-less dispatch was tagged" }
        return "outer#${outer.id} -> inner#${inner.id}; parallel{} opened child span 'parallel'; " +
            "plain dispatch untagged"
    }

    private companion object {
        const val GROUP = "Tracing"
    }
}
