package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.runCatchingCancellable
import io.github.santimattius.structured.runtime.safeCallbackFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** `safeCallbackFlow` (INTEROP_002). */
internal class InteropValidations {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "safeCallbackFlow: delivers events, disposes once on completion") {
            deliversAndDisposes()
        },
        ValidationCase(GROUP, "safeCallbackFlow: disposes on collector cancellation") { disposesOnCancellation() },
        ValidationCase(GROUP, "safeCallbackFlow: register failure reaches collector") { registerFailure() },
        ValidationCase(GROUP, "safeCallbackFlow: buffer / overflow policies") { overflowPolicies() },
    )

    private suspend fun deliversAndDisposes(): String {
        val disposals = AtomicInteger(0)
        val flow = safeCallbackFlow<Int> { channel ->
            repeat(ValidationSizes.FEW) { channel.trySend(it + 1) }
            DisposableHandle { disposals.incrementAndGet() }
        }
        val received = flow.take(ValidationSizes.FEW).toList()
        check(received == (1..ValidationSizes.FEW).toList()) { "received $received" }
        check(disposals.get() == 1) { "disposed ${disposals.get()} times, expected exactly 1" }
        return "received $received, handle disposed ${disposals.get()}x"
    }

    private suspend fun disposesOnCancellation(): String = coroutineScope {
        val disposals = AtomicInteger(0)
        val registered = CompletableDeferred<Unit>()
        val flow = safeCallbackFlow<Int> {
            registered.complete(Unit)
            DisposableHandle { disposals.incrementAndGet() }
        }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { } }
        registered.await()
        check(disposals.get() == 0) { "disposed before cancellation" }
        job.cancelAndJoin()
        check(disposals.get() == 1) { "disposed ${disposals.get()} times after cancel, expected 1" }
        "listener removed exactly once after cancelAndJoin"
    }

    private suspend fun registerFailure(): String {
        val disposals = AtomicInteger(0)
        val flow = safeCallbackFlow<Int> {
            disposals.incrementAndGet() // never reached by a handle: nothing is returned
            error("register boom")
        }
        val failure = runCatchingCancellable { flow.toList() }.exceptionOrNull()
        check(failure is IllegalStateException && failure.message == "register boom") { "failure was $failure" }
        return "collector got ${failure::class.simpleName}(\"${failure.message}\"), no handle to dispose"
    }

    private suspend fun overflowPolicies(): String {
        val default = burst()
        // 64 buffered + at most one element handed straight to the already-suspended collector.
        check(default.size <= ValidationSizes.DEFAULT_BUFFER + 1 && default.first() == 1) {
            "default buffer kept ${default.preview()}"
        }
        val conflated = burst(buffer = Channel.CONFLATED)
        check(conflated.last() == ValidationSizes.BURST && conflated.size < ValidationSizes.BURST) {
            "CONFLATED kept ${conflated.preview()}"
        }
        val dropOldest = burst(buffer = ValidationSizes.SMALL_BUFFER, overflow = BufferOverflow.DROP_OLDEST)
        check(dropOldest.last() == ValidationSizes.BURST && dropOldest.size <= ValidationSizes.SMALL_BUFFER + 1) {
            "DROP_OLDEST kept ${dropOldest.preview()}"
        }
        val unlimited = burst(buffer = Channel.UNLIMITED)
        check(unlimited.size == ValidationSizes.BURST) { "UNLIMITED kept ${unlimited.size}" }
        return "burst of ${ValidationSizes.BURST}: default=${default.size} " +
            "(buffer 64 + 1 handed to waiting collector, drops newest), " +
            "CONFLATED=${conflated.size}, DROP_OLDEST(8)=${dropOldest.size} (1 + latest 8), UNLIMITED=${unlimited.size}"
    }

    private suspend fun burst(
        buffer: Int = Channel.BUFFERED,
        overflow: BufferOverflow = BufferOverflow.SUSPEND,
    ): List<Int> = burstFlow(buffer, overflow).toList()

    private fun burstFlow(buffer: Int, overflow: BufferOverflow): Flow<Int> =
        safeCallbackFlow(buffer = buffer, onBufferOverflow = overflow) { channel: SendChannel<Int> ->
            repeat(ValidationSizes.BURST) { channel.trySend(it + 1) }
            channel.close()
            DisposableHandle { }
        }

    private companion object {
        const val GROUP = "Interop"
    }
}
