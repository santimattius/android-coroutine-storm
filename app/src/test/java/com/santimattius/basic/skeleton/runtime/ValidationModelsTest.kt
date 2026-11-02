package com.santimattius.basic.skeleton.runtime

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import com.santimattius.basic.skeleton.runtime.model.ValidationStatus
import com.santimattius.basic.skeleton.runtime.model.execute
import com.santimattius.basic.skeleton.runtime.validation.DispatcherValidations
import io.github.santimattius.structured.runtime.test.TestDispatcherProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TIMEOUT_MS = 10L

class ValidationModelsTest {

    @Test
    fun `execute reports PASS with the detail`() = runTest {
        val row = ValidationCase("g", "n") { "all good" }.execute()
        assertEquals(ValidationStatus.PASS, row.status)
        assertEquals("all good", row.detail)
    }

    @Test
    fun `execute reports FAIL with exception type and message`() = runTest {
        val row = ValidationCase("g", "n") { check(false) { "nope" }.let { "unreachable" } }.execute()
        assertEquals(ValidationStatus.FAIL, row.status)
        assertEquals("IllegalStateException: nope", row.detail)
    }

    @Test
    fun `execute does not turn cancellation into FAIL`() = runTest {
        var reported = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            ValidationCase("g", "n") { awaitCancellation() }.execute()
            reported = true
        }
        job.cancelAndJoin()
        assertFalse("cancellation was reported as a row", reported)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `a validation that throws its own timeout is a FAIL row, not an aborted run`() = runTest {
        val row = ValidationCase("g", "n") {
            withTimeout(TIMEOUT_MS) { awaitCancellation() }
        }.execute()
        assertEquals(ValidationStatus.FAIL, row.status)
        assertTrue(row.detail, row.detail.startsWith("TimeoutCancellationException"))
    }

    @Test
    fun `main dispatcher validation fails when the provider does not run on main`() = runTest {
        val notMain = object : com.santimattius.basic.skeleton.runtime.validation.MainThreadProbe {
            override val isMainDispatcherAvailable = false
            override fun isOnMainThread() = false
        }
        val case = DispatcherValidations(TestDispatcherProvider(testScheduler), notMain).cases()
            .first { it.name.contains("main thread") }
        assertEquals(ValidationStatus.FAIL, case.execute().status)
    }
}
