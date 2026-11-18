package com.santimattius.basic.skeleton.storm.instrumentation

import io.github.santimattius.structured.runtime.AndroidTraceSink
import io.github.santimattius.structured.runtime.CompositeSink
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.test.RecordingSink
import io.github.santimattius.structured.runtime.test.testDispatcherProvider
import io.github.santimattius.structured.runtime.traced
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StormTraceSinkTest {

    @Test
    fun `without a perfetto sink only the system trace sink is used`() {
        assertTrue(stormTraceSink(perfetto = null) is AndroidTraceSink)
    }

    @Test
    fun `with a perfetto sink both sinks are composed`() {
        assertTrue(stormTraceSink(perfetto = RecordingSink()) is CompositeSink)
    }

    @Test
    fun `a traced dispatch reaches the perfetto sink`() = runTest {
        val perfetto = RecordingSink()
        val traced = testDispatcherProvider().traced(stormTraceSink(perfetto))

        withContext(traced.io) { }

        assertEquals(1, perfetto.eventsOf<TraceEvent.Queued>().size)
        assertEquals(1, perfetto.eventsOf<TraceEvent.Started>().size)
        assertEquals(1, perfetto.eventsOf<TraceEvent.Finished>().size)
    }
}
