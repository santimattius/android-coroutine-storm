package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.concurrentOrdered
import io.github.santimattius.structured.runtime.parallel
import io.github.santimattius.structured.runtime.runCatchingCancellable
import io.github.santimattius.structured.runtime.supervisedAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** `parallel`, `supervisedAll`, `concurrentOrdered` (CONCUR_003, SCOPE_004). */
internal class ParallelismValidations(private val dispatchers: DispatcherProvider) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "parallel: input order, unbounded vs concurrency=2") { parallelOrderAndBound() },
        ValidationCase(GROUP, "parallel: fail-fast cancels siblings; concurrency must be > 0") { parallelFailFast() },
        ValidationCase(GROUP, "supervisedAll: failures isolated, task cancellation kept") { supervisedIsolation() },
        ValidationCase(GROUP, "concurrentOrdered: cold, input order, bounded") { orderedFlow() },
        ValidationCase(GROUP, "concurrentOrdered: fail-fast") { orderedFailFast() },
    )

    private fun tracked(probe: ConcurrencyProbe, count: Int): List<suspend () -> Int> =
        List(count) { index ->
            suspend {
                probe.track {
                    delay(ValidationTimings.MEDIUM)
                    index
                }
            }
        }

    private suspend fun parallelOrderAndBound(): String = withContext(dispatchers.default) {
        val unboundedProbe = ConcurrencyProbe()
        val unbounded = tracked(unboundedProbe, ValidationSizes.TASKS).parallel()
        check(unbounded == List(ValidationSizes.TASKS) { it }) { "order was $unbounded" }
        check(unboundedProbe.peak == ValidationSizes.TASKS) { "unbounded peak=${unboundedProbe.peak}" }

        val boundedProbe = ConcurrencyProbe()
        val bounded = tracked(boundedProbe, ValidationSizes.TASKS).parallel(concurrency = ValidationSizes.BOUND)
        check(bounded == unbounded) { "bounded order was $bounded" }
        check(boundedProbe.peak == ValidationSizes.BOUND) { "bounded peak=${boundedProbe.peak}" }
        "order $bounded; peak concurrency unbounded=${unboundedProbe.peak}, concurrency=2 -> ${boundedProbe.peak}"
    }

    private suspend fun parallelFailFast(): String = withContext(dispatchers.default) {
        val siblingCancelled = AtomicBoolean(false)
        val tasks = listOf<suspend () -> Int>(
            {
                try {
                    awaitCancellation()
                } finally {
                    siblingCancelled.set(true)
                }
            },
            {
                delay(ValidationTimings.SHORT)
                error("task failed")
            },
        )
        val failure = runCatchingCancellable { tasks.parallel() }.exceptionOrNull()
        check(failure is IllegalStateException && failure.message == "task failed") { "failure was $failure" }
        check(siblingCancelled.get()) { "sibling kept running after the failure" }
        val invalid = runCatchingCancellable { tasks.parallel(concurrency = 0) }.exceptionOrNull()
        check(invalid is IllegalArgumentException) { "concurrency=0 gave $invalid" }
        "first failure rethrown, sibling cancelled; concurrency=0 -> IllegalArgumentException"
    }

    private suspend fun supervisedIsolation(): String = withContext(dispatchers.default) {
        val tasks = listOf<suspend () -> String>(
            { "a" },
            { error("boom") },
            { throw CancellationException("own cancellation") },
            { "d" },
        )
        val results = tasks.supervisedAll(concurrency = ValidationSizes.BOUND)
        check(results.size == tasks.size) { "size ${results.size}" }
        check(results[0].getOrNull() == "a" && results.last().getOrNull() == "d") { "successes lost: $results" }
        check(results[1].exceptionOrNull() is IllegalStateException) { "failure not isolated: ${results[1]}" }
        check(results[2].exceptionOrNull() is CancellationException) { "own cancellation not preserved" }
        "ok/fail/own-cancel/ok -> ${results.map { if (it.isSuccess) "ok" else "fail" }}, caller not cancelled"
    }

    private suspend fun orderedFlow(): String = withContext(dispatchers.default) {
        val started = AtomicInteger(0)
        val probe = ConcurrencyProbe()
        val tasks = List(ValidationSizes.SOME) { index ->
            suspend {
                started.incrementAndGet()
                probe.track {
                    // Reverse delays: task 0 finishes last, yet must be emitted first.
                    delay(ValidationTimings.SHORT * (ValidationSizes.SOME - index))
                    index
                }
            }
        }
        val flow = tasks.concurrentOrdered()
        check(started.get() == 0) { "flow is not cold: ${started.get()} tasks started before collection" }
        val ordered = flow.toList()
        check(ordered == List(ValidationSizes.SOME) { it }) { "emission order was $ordered" }
        check(probe.peak == ValidationSizes.SOME) { "peak=${probe.peak}, expected every task started" }

        val boundedProbe = ConcurrencyProbe()
        val bounded = tracked(boundedProbe, ValidationSizes.TASKS).concurrentOrdered(ValidationSizes.BOUND).toList()
        check(bounded.size == ValidationSizes.TASKS && boundedProbe.peak == ValidationSizes.BOUND) {
            "bounded: $bounded peak=${boundedProbe.peak}"
        }
        "cold until collected; emitted $ordered despite reverse completion; bounded(2) peak=${boundedProbe.peak}"
    }

    private suspend fun orderedFailFast(): String = withContext(dispatchers.default) {
        val pendingCancelled = AtomicBoolean(false)
        val pendingStarted = CompletableDeferred<Unit>()
        val tasks = listOf<suspend () -> Int>(
            {
                // First in emission order, never finishes: fail-fast must not wait for it.
                try {
                    pendingStarted.complete(Unit)
                    awaitCancellation()
                } finally {
                    pendingCancelled.set(true)
                }
            },
            {
                pendingStarted.await()
                error("ordered boom")
            },
        )
        val collected = mutableListOf<Int>()
        val failure = runCatchingCancellable { tasks.concurrentOrdered().collect { collected += it } }
            .exceptionOrNull()
        check(failure is IllegalStateException && failure.message == "ordered boom") { "failure was $failure" }
        check(pendingCancelled.get()) { "pending task was not cancelled" }
        check(collected.isEmpty()) { "emitted $collected although the head task never finished" }
        "later task failed -> head task (still pending) cancelled, nothing emitted"
    }

    private companion object {
        const val GROUP = "Parallelism"
    }
}
