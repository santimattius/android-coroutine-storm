package com.santimattius.basic.skeleton.runtime

import com.santimattius.basic.skeleton.runtime.model.execute
import com.santimattius.basic.skeleton.runtime.validation.ValidationSuite
import io.github.santimattius.structured.runtime.test.TestDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Runs every runtime validation under [TestDispatcherProvider] (one shared scheduler, virtual time),
 * so the exact logic the Android screen runs is verifiable without a device.
 */
@RunWith(Parameterized::class)
class ValidationSuiteTest(private val caseName: String) {

    @Test
    fun `validation passes`() {
        val provider = TestDispatcherProvider()
        val suite = ValidationSuite(provider, FakeMainThreadProbe)
        runTest(StandardTestDispatcher(provider.scheduler)) {
            val case = suite.cases(lifecycleOwner = this).first { it.name == caseName }
            val row = case.execute()
            assertTrue("${row.group} / ${row.name}: ${row.detail}", row.passed)
        }
    }

    companion object {
        private fun allCases() =
            ValidationSuite(TestDispatcherProvider(), FakeMainThreadProbe).cases(CoroutineScope(Job()))

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun names(): List<String> = allCases().map { it.name }
    }
}

class ValidationSuiteShapeTest {

    @Test
    fun `case names are unique and every runtime API group is present`() {
        val cases = ValidationSuite(TestDispatcherProvider(), FakeMainThreadProbe).cases(CoroutineScope(Job()))
        assertEquals(cases.size, cases.map { it.name }.toSet().size)
        val text = cases.joinToString("\n") { it.name }
        listOf(
            "safeCallbackFlow", "runCatchingCancellable", "withCleanup", "withTimeoutResult", "parallel",
            "supervisedAll", "concurrentOrdered", "hedged", "warmIn", "DispatcherProvider", "ioCompat",
            "instrumented", "InstrumentedDispatcher", "StateHolder", "EventChannel", "launchInSafely",
            "stateInWhileSubscribed", "PoolSampler", "StatsSink", "RingBufferSink", "withTraceSpan",
            "parallelism", "platformCapabilities", "AndroidTraceSink", "TraceSink.None", "TracingDispatcher",
        ).forEach { api -> assertTrue("no validation mentions $api", text.contains(api)) }
    }
}
