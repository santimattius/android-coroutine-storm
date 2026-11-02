package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.EventChannel
import io.github.santimattius.structured.runtime.StateHolder
import io.github.santimattius.structured.runtime.launchInSafely
import io.github.santimattius.structured.runtime.stateInWhileSubscribed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** `StateHolder` (FLOW_010), `EventChannel` (FLOW_011), `launchInSafely` (FLOW_005). */
internal class StateValidations(private val dispatchers: DispatcherProvider) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "StateHolder: atomic update under contention, read-only state") { stateHolder() },
        ValidationCase(GROUP, "EventChannel: buffers before collection, single consumer") {
            eventsBufferedSingleConsumer()
        },
        ValidationCase(GROUP, "EventChannel: trySend backpressure, undelivered callback, close") {
            eventsBackpressure()
        },
        ValidationCase(GROUP, "launchInSafely: routes errors, ignores cancellation") { launchInSafelyRouting() },
        ValidationCase(GROUP, "stateInWhileSubscribed: lazy start, grace period, 5s default") { whileSubscribed() },
    )

    private suspend fun stateHolder(): String {
        val holder = StateHolder(0)
        withContext(dispatchers.default) {
            (1..ValidationSizes.WORKERS).map {
                async { repeat(ValidationSizes.INCREMENTS) { holder.update { value -> value + 1 } } }
            }.awaitAll()
        }
        val expected = ValidationSizes.WORKERS * ValidationSizes.INCREMENTS
        check(holder.value == expected) { "lost updates: ${holder.value} != $expected" }
        holder.set(-1)
        check(holder.state.value == -1) { "set() not visible through state" }
        check(holder.state !is MutableStateFlow<*>) { "state leaks MutableStateFlow" }
        return "${ValidationSizes.WORKERS}x${ValidationSizes.INCREMENTS} concurrent updates = $expected; " +
            "state is ${holder.state::class.simpleName}, not MutableStateFlow"
    }

    private suspend fun eventsBufferedSingleConsumer(): String = coroutineScope {
        val channel = EventChannel<Int>()
        for (event in 1..ValidationSizes.SOME) channel.send(event) // nobody is collecting yet
        channel.close()
        val shared = CopyOnWriteArrayList<Int>()
        val collectors = List(ValidationSizes.BOUND) {
            async { channel.events.collect { shared += it } }
        }
        collectors.awaitAll()
        check(shared.sorted() == (1..ValidationSizes.SOME).toList()) { "events were $shared (lost or duplicated)" }
        "events sent before collection were kept; 2 collectors split them without duplicates: ${shared.toList()}"
    }

    private suspend fun eventsBackpressure(): String = coroutineScope {
        val undelivered = CopyOnWriteArrayList<Int>()
        val channel = EventChannel<Int>(capacity = 1, onUndeliveredElement = { undelivered += it })
        check(channel.trySend(1)) { "first trySend should fit the buffer" }
        check(!channel.trySend(2)) { "trySend should fail on a full buffer" }
        val suspended = launch(start = CoroutineStart.UNDISPATCHED) { channel.send(ValidationSizes.FEW) }
        suspended.cancelAndJoin()
        check(undelivered == listOf(ValidationSizes.FEW)) { "onUndeliveredElement saw $undelivered" }
        channel.close()
        val drained = channel.events.toList()
        check(drained == listOf(1)) { "drained $drained after close" }
        "trySend(2)=false on full buffer, cancelled send(3) reported undelivered, close() completed flow with $drained"
    }

    private suspend fun launchInSafelyRouting(): String = coroutineScope {
        val errors = CopyOnWriteArrayList<Throwable>()
        val failing = flow<Int> {
            emit(1)
            error("flow broke")
        }.launchInSafely(this) { errors += it }
        failing.join()
        check(errors.size == 1 && errors.single().message == "flow broke") { "errors=$errors" }
        check(!failing.isCancelled) { "job should complete normally after routing the error" }

        val cancelledErrors = AtomicInteger(0)
        val gotValue = CompletableDeferred<Unit>()
        val infinite = flow {
            emit(1)
            awaitCancellation()
        }.onEach { gotValue.complete(Unit) }.launchInSafely(this) { cancelledErrors.incrementAndGet() }
        gotValue.await()
        infinite.cancelAndJoin()
        check(cancelledErrors.get() == 0) { "onError called ${cancelledErrors.get()}x on cancellation" }
        "error routed to onError once; cancellation did not call onError"
    }

    private suspend fun whileSubscribed(): String = withContext(dispatchers.default) {
        val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
        try {
            val starts = AtomicInteger(0)
            val stops = AtomicInteger(0)
            val upstream = flow {
                starts.incrementAndGet()
                try {
                    emit(1)
                    awaitCancellation()
                } finally {
                    stops.incrementAndGet()
                }
            }
            val state = upstream.stateInWhileSubscribed(
                scope,
                initialValue = 0,
                stopTimeoutMillis = ValidationTimings.GRACE_MS,
            )
            delay(ValidationTimings.GRACE_PROBE_MS)
            check(state.value == 0 && starts.get() == 0) { "upstream started without subscribers" }
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { state.collect { } }
            state.first { it == 1 }
            check(starts.get() == 1) { "starts=${starts.get()}" }
            collector.cancelAndJoin()
            delay(ValidationTimings.GRACE_PROBE_MS)
            check(stops.get() == 0) { "upstream stopped inside the grace period" }
            delay(ValidationTimings.GRACE_EXPIRY_MS)
            check(stops.get() == 1) { "upstream still running after grace (stops=${stops.get()})" }
            check(state.value == 1) { "replay cache lost the last value" }

            val defaultStops = AtomicInteger(0)
            val withDefault = flow {
                try {
                    emit(1)
                    awaitCancellation()
                } finally {
                    defaultStops.incrementAndGet()
                }
            }.stateInWhileSubscribed(scope, initialValue = 0)
            val second = launch(start = CoroutineStart.UNDISPATCHED) { withDefault.collect { } }
            withDefault.first { it == 1 }
            second.cancelAndJoin()
            delay(ValidationTimings.DEFAULT_GRACE_PROBE_MS)
            check(defaultStops.get() == 0) {
                "default grace shorter than ${ValidationTimings.DEFAULT_GRACE_PROBE_MS}ms"
            }
            "lazy start, kept ${ValidationTimings.GRACE_MS}ms after last subscriber, then stopped; " +
                "default grace still holding after ${ValidationTimings.DEFAULT_GRACE_PROBE_MS}ms"
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val GROUP = "State & Flow"
    }
}
