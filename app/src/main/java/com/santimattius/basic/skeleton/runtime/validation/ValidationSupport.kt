package com.santimattius.basic.skeleton.runtime.validation

import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.withTimeoutResult
import kotlinx.coroutines.delay

internal fun <T> List<T>.preview(limit: Int = ValidationSizes.SOME): String =
    if (size <= limit) toString() else take(limit).toString().dropLast(1) + ", ...] (size=$size)"

/**
 * Polls [condition] until true. Needed because a traced dispatcher emits `Finished` from the worker
 * thread, which can land slightly after `withContext` has already resumed the caller.
 */
internal suspend fun awaitUntil(what: String, condition: () -> Boolean) {
    val outcome = withTimeoutResult(ValidationTimings.AWAIT_MS) {
        while (!condition()) delay(POLL_MS)
    }
    check(outcome.isSuccess) { "timed out waiting for $what" }
}

internal fun TraceEvent.dispatchIdOrNull(): Long? = when (this) {
    is TraceEvent.Queued -> dispatchId
    is TraceEvent.Started -> dispatchId
    is TraceEvent.Finished -> dispatchId
    else -> null
}

private const val POLL_MS = 5L
