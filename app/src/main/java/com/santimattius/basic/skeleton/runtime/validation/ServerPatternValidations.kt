package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.hedged
import io.github.santimattius.structured.runtime.runCatchingCancellable
import io.github.santimattius.structured.runtime.warmIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration

/** `hedged` and `warmIn` (server-side patterns, not tied to a rule). */
internal class ServerPatternValidations(private val dispatchers: DispatcherProvider) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "hedged: second attempt wins, slow loser cancelled") { hedgedWinner() },
        ValidationCase(GROUP, "hedged: shouldHedge=false rethrows immediately") { hedgedShouldHedge() },
        ValidationCase(GROUP, "hedged: all fail -> first failure + suppressed; arg checks") { hedgedAllFail() },
        ValidationCase(GROUP, "warmIn: one upstream run shared by collectors") { warmShared() },
        ValidationCase(GROUP, "warmIn: scope cancel -> IllegalStateException(cause)") { warmScopeCancelled() },
        ValidationCase(GROUP, "warmIn: upstream error propagates") { warmError() },
    )

    private suspend fun hedgedWinner(): String {
        val firstCancelled = AtomicBoolean(false)
        val winner = hedged(hedgeAfter = ValidationTimings.HEDGE_AFTER, maxAttempts = ValidationSizes.FEW) { attempt ->
            if (attempt == 0) {
                try {
                    awaitCancellation()
                } finally {
                    firstCancelled.set(true)
                }
            } else {
                "attempt-$attempt"
            }
        }
        check(winner == "attempt-1") { "winner was $winner" }
        check(firstCancelled.get()) { "slow first attempt was not cancelled" }
        return "winner=$winner after hedgeAfter=${ValidationTimings.HEDGE_AFTER}; attempt 0 cancelled"
    }

    private suspend fun hedgedShouldHedge(): String {
        val attempts = AtomicInteger(0)
        val failure = runCatchingCancellable {
            hedged(
                hedgeAfter = ValidationTimings.HEDGE_AFTER,
                maxAttempts = ValidationSizes.FEW,
                shouldHedge = { it !is IllegalArgumentException },
            ) {
                attempts.incrementAndGet()
                throw IllegalArgumentException("fatal")
            }
        }.exceptionOrNull()
        check(failure is IllegalArgumentException) { "failure was $failure" }
        check(attempts.get() == 1) { "attempts=${attempts.get()}, expected 1 (no hedge)" }
        return "non-hedgeable error rethrown after ${attempts.get()} attempt"
    }

    private suspend fun hedgedAllFail(): String {
        val failure = runCatchingCancellable {
            hedged(hedgeAfter = ValidationTimings.HEDGE_AFTER, maxAttempts = ValidationSizes.FEW) { attempt ->
                throw IOException("fail-$attempt")
            }
        }.exceptionOrNull()
        check(failure is IOException && failure.message == "fail-0") { "failure was $failure" }
        // With coroutine stack-trace recovery on (debug/tests) the thrown instance is a copy whose
        // `cause` is the original, and suppressed exceptions live on the original.
        val suppressed = generateSequence<Throwable>(failure) { it.cause }
            .flatMap { it.suppressed.asSequence() }
            .toList()
        check(suppressed.size == ValidationSizes.BOUND) { "suppressed=${suppressed.size}" }
        val noAttempts = runCatchingCancellable { hedged(Duration.ZERO, maxAttempts = 0) { 1 } }
        val negative = runCatchingCancellable { hedged(Duration.ZERO - ValidationTimings.SHORT) { 1 } }
        check(noAttempts.exceptionOrNull() is IllegalArgumentException) { "maxAttempts=0 -> $noAttempts" }
        check(negative.exceptionOrNull() is IllegalArgumentException) { "negative hedgeAfter -> $negative" }
        return "rethrew ${failure.message} with ${suppressed.size} suppressed; bad args -> IllegalArgumentException"
    }

    private suspend fun warmShared(): String = coroutineScope {
        val upstreamRuns = AtomicInteger(0)
        val shared = flow {
            upstreamRuns.incrementAndGet()
            delay(ValidationTimings.SHORT)
            emit(1)
            emit(2)
        }.warmIn(this)
        val first = shared.toList()
        val second = shared.toList()
        check(first == listOf(1, 2) && second == first) { "first=$first second=$second" }
        check(upstreamRuns.get() == 1) { "upstream ran ${upstreamRuns.get()} times" }
        "two collectors replayed $first from ${upstreamRuns.get()} upstream run"
    }

    private suspend fun warmScopeCancelled(): String = coroutineScope {
        val warmScope = CoroutineScope(SupervisorJob() + dispatchers.default)
        val gotFirst = CompletableDeferred<Unit>()
        val shared = flow {
            emit(1)
            awaitCancellation()
        }.warmIn(warmScope)
        val outcome = async { runCatchingCancellable { shared.collect { gotFirst.complete(Unit) } } }
        gotFirst.await()
        warmScope.cancel()
        val failure = outcome.await().exceptionOrNull()
        check(failure is IllegalStateException && failure.cause is CancellationException) {
            "collector failed with $failure (cause=${failure?.cause})"
        }
        "collector failed with IllegalStateException(cause=${failure.cause?.let { it::class.simpleName }})"
    }

    private suspend fun warmError(): String = coroutineScope {
        val shared = flow<Int> {
            emit(1)
            throw IOException("upstream broke")
        }.warmIn(this)
        val seen = mutableListOf<Int>()
        val failure = runCatchingCancellable { shared.collect { seen += it } }.exceptionOrNull()
        check(failure is IOException && failure.message == "upstream broke") { "failure was $failure" }
        "collector saw $seen then ${failure.message}"
    }

    private companion object {
        const val GROUP = "Server patterns"
    }
}
