package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.launchInSafely
import io.github.santimattius.structured.runtime.safeCallbackFlow
import io.github.santimattius.structured.runtime.stateInWhileSubscribed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.job
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android lifecycle angle: everything the runtime started on behalf of a lifecycle owner (for example
 * `viewModelScope`) must wind down when that owner is cancelled.
 */
internal class LifecycleValidations(
    private val dispatchers: DispatcherProvider,
    private val ownerScope: CoroutineScope,
) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "Owner scope cancel: callback disposed, no onError, scope detached") { ownerCancel() },
    )

    private suspend fun ownerCancel(): String {
        val parentJob = checkNotNull(ownerScope.coroutineContext[Job]) { "owner scope has no Job" }
        val owner = CoroutineScope(ownerScope.coroutineContext + dispatchers.default + SupervisorJob(parentJob))
        check(parentJob.children.contains(owner.coroutineContext.job)) { "child scope is not attached to the owner" }

        val disposals = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val delivered = CompletableDeferred<Unit>()
        val source = safeCallbackFlow<Int> { channel ->
            channel.trySend(1)
            DisposableHandle { disposals.incrementAndGet() }
        }
        val collector = source.onEach { delivered.complete(Unit) }.launchInSafely(owner) { errors.incrementAndGet() }
        val state = source.stateInWhileSubscribed(owner, initialValue = 0)
        delivered.await()

        owner.coroutineContext.job.cancelAndJoin() // simulates the owner (ViewModel/Activity) going away
        collector.join()
        check(disposals.get() >= 1) { "callback was never disposed" }
        check(errors.get() == 0) { "onError fired ${errors.get()}x during cancellation" }
        check(collector.isCancelled && !owner.coroutineContext.job.isActive) { "child work still active" }
        check(parentJob.isActive) { "cancelling the child scope cancelled the owner scope" }
        check(!parentJob.children.contains(owner.coroutineContext.job)) { "cancelled child still attached" }
        return "child of owner scope; after cancel: disposed=${disposals.get()}x, onError=0, " +
            "owner still active, state.value=${state.value}"
    }

    private companion object {
        const val GROUP = "Android lifecycle"
    }
}
