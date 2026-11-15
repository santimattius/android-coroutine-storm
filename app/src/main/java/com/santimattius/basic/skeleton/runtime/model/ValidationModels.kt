package com.santimattius.basic.skeleton.runtime.model

import io.github.santimattius.structured.runtime.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * One runtime validation: [run] returns a short human-readable detail when the behaviour matches
 * the README, and throws (usually through [check]) when it does not.
 */
class ValidationCase(
    val group: String,
    val name: String,
    val run: suspend () -> String,
)

enum class ValidationStatus { PENDING, RUNNING, PASS, FAIL }

/** UI/test-facing row for a [ValidationCase]. */
data class ValidationRow(
    val group: String,
    val name: String,
    val status: ValidationStatus = ValidationStatus.PENDING,
    val detail: String = "",
) {
    val passed: Boolean get() = status == ValidationStatus.PASS
}

fun ValidationCase.pending(): ValidationRow = ValidationRow(group, name)

/**
 * Executes the case and converts its outcome to a row. Uses [runCatchingCancellable] so an
 * outer cancellation (for example `viewModelScope` being cleared) is never reported as a FAIL.
 */
suspend fun ValidationCase.execute(): ValidationRow {
    val outcome = try {
        runCatchingCancellable { run() }
    } catch (e: CancellationException) {
        // A validation may legitimately surface a CancellationException of its own (for example a
        // TimeoutCancellationException). Only abort the run when the runner itself was cancelled.
        currentCoroutineContext().ensureActive()
        Result.failure(e)
    }
    return outcome.fold(
        onSuccess = { detail -> ValidationRow(group, name, ValidationStatus.PASS, detail) },
        onFailure = { error ->
            ValidationRow(group, name, ValidationStatus.FAIL, "${error::class.simpleName}: ${error.message}")
        },
    )
}
