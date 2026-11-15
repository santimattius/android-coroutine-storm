package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.runCatchingCancellable
import io.github.santimattius.structured.runtime.withCleanup
import io.github.santimattius.structured.runtime.withTimeoutResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** `runCatchingCancellable` (CANCEL_003), `withCleanup` (CANCEL_004), `withTimeoutResult` (CANCEL_006/007). */
internal class CancellationValidations {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "runCatchingCancellable: captures failures, both overloads") { capturesFailures() },
        ValidationCase(GROUP, "runCatchingCancellable: rethrows CancellationException") { rethrowsCancellation() },
        ValidationCase(GROUP, "withCleanup: suspending cleanup survives cancellation") { cleanupUnderCancellation() },
        ValidationCase(GROUP, "withCleanup: success/failure/suppressed semantics") { cleanupSemantics() },
        ValidationCase(GROUP, "withTimeoutResult: timeout becomes Result, parent stays alive") { timeoutAsResult() },
        ValidationCase(GROUP, "withTimeoutResult: nested timeout and outer cancel propagate") { timeoutPropagation() },
    )

    private suspend fun capturesFailures(): String {
        val failed = runCatchingCancellable { throw IOException("io") }
        check(failed.exceptionOrNull() is IOException) { "expected IOException, got ${failed.exceptionOrNull()}" }
        val ok = runCatchingCancellable {
            delay(ValidationTimings.SHORT)
            "ok"
        }
        check(ok.getOrNull() == "ok") { "suspending success lost: $ok" }
        val receiver = "structured".runCatchingCancellable { length }
        check(receiver.getOrNull() == "structured".length) { "receiver overload returned $receiver" }
        return "failure=IOException, suspending block in inline lambda OK, receiver overload=${receiver.getOrNull()}"
    }

    private suspend fun rethrowsCancellation(): String = coroutineScope {
        val runtimeSwallowed = AtomicBoolean(false)
        val stdlibSwallowed = AtomicBoolean(false)
        val safe = launch(start = CoroutineStart.UNDISPATCHED) {
            runCatchingCancellable { awaitCancellation() }
            runtimeSwallowed.set(true)
        }
        val naive = launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { awaitCancellation() }
            stdlibSwallowed.set(true)
        }
        safe.cancelAndJoin()
        naive.cancelAndJoin()
        check(!runtimeSwallowed.get() && safe.isCancelled) { "runCatchingCancellable swallowed the cancellation" }
        check(stdlibSwallowed.get()) { "contrast check failed: stdlib runCatching was expected to swallow" }
        "runCatchingCancellable aborted the coroutine; stdlib runCatching kept running after cancel (the bug)"
    }

    private suspend fun cleanupUnderCancellation(): String = coroutineScope {
        val cleaned = AtomicBoolean(false)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            withCleanup(cleanup = {
                delay(ValidationTimings.SHORT) // would throw if the cleanup were cancellable
                cleaned.set(true)
            }) { awaitCancellation() }
        }
        job.cancelAndJoin()
        check(cleaned.get()) { "suspending cleanup did not complete after cancel" }
        check(job.isCancelled) { "job was expected to end cancelled" }
        "cleanup ran delay() to completion inside NonCancellable, job still cancelled"
    }

    private suspend fun cleanupSemantics(): String {
        var ran = 0
        val value = withCleanup(cleanup = { ran++ }) { "value" }
        check(value == "value" && ran == 1) { "success path: value=$value ran=$ran" }

        val both = runCatchingCancellable {
            withCleanup(cleanup = { throw IOException("cleanup") }) { error("block") }
        }.exceptionOrNull()
        check(both is IllegalStateException && both.message == "block") { "block error lost: $both" }
        check(both.suppressed.any { it is IOException && it.message == "cleanup" }) { "cleanup not suppressed" }

        val cleanupOnly = runCatchingCancellable {
            withCleanup(cleanup = { throw IOException("cleanup") }) { "fine" }
        }.exceptionOrNull()
        check(cleanupOnly is IOException) { "cleanup-only failure expected IOException, got $cleanupOnly" }
        return "cleanup ran on success; block error kept + cleanup suppressed; cleanup-only error propagates"
    }

    private suspend fun timeoutAsResult(): String {
        val timedOut = withTimeoutResult(ValidationTimings.TIMEOUT) { delay(ValidationTimings.NEVER_MS) }
        check(timedOut.exceptionOrNull() is TimeoutCancellationException) { "expected timeout, got $timedOut" }
        val parentActive = currentCoroutineContext().isActive
        check(parentActive) { "parent scope was cancelled by the timeout" }
        val fast = withTimeoutResult(ValidationTimings.NEVER_MS) { "done" }
        check(fast.getOrNull() == "done") { "success path returned $fast" }
        val millis = withTimeoutResult(ValidationTimings.TIMEOUT.inWholeMilliseconds) {
            delay(ValidationTimings.NEVER_MS)
        }
        check(millis.isFailure) { "Long overload did not time out" }
        return "Duration + Long overloads: timeout -> Result.failure(TimeoutCancellationException), parent active"
    }

    private suspend fun timeoutPropagation(): String {
        // TimeoutCancellationException IS a CancellationException, so runCatchingCancellable would
        // (correctly) rethrow it; a plain try/catch is the way to observe the propagation here.
        val nested = try {
            withTimeoutResult(ValidationTimings.NEVER_MS) {
                withTimeout(ValidationTimings.NESTED_TIMEOUT_MS) { delay(ValidationTimings.NEVER_MS) }
            }.exceptionOrNull()
        } catch (e: TimeoutCancellationException) {
            e
        }
        check(nested is TimeoutCancellationException) { "nested withTimeout should propagate, got $nested" }
        val outer = withTimeoutOrNull(ValidationTimings.TIMEOUT) {
            withTimeoutResult(ValidationTimings.NEVER_MS) { delay(ValidationTimings.NEVER_MS) }
        }
        check(outer == null) { "outer timeout was swallowed into a Result: $outer" }
        return "nested withTimeout thrown unchanged (not a Result); outer withTimeoutOrNull still fired"
    }

    private companion object {
        const val GROUP = "Cancellation"
    }
}
