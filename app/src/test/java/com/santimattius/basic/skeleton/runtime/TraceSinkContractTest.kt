package com.santimattius.basic.skeleton.runtime

import io.github.santimattius.structured.runtime.AndroidTraceSink
import io.github.santimattius.structured.runtime.CompositeSink
import io.github.santimattius.structured.runtime.LoggingSink
import io.github.santimattius.structured.runtime.RingBufferSink
import io.github.santimattius.structured.runtime.StatsSink
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import io.github.santimattius.structured.runtime.traced
import io.github.santimattius.structured.runtime.test.RecordingSink
import io.github.santimattius.structured.runtime.test.TraceSinkContract
import io.github.santimattius.structured.runtime.test.testDispatcherProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = StatsSink()

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class RingBufferSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = RingBufferSink(capacity = 16)

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class LoggingSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = LoggingSink { }

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class CompositeSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = CompositeSink(StatsSink(), RingBufferSink(capacity = 16))

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class AndroidTraceSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = AndroidTraceSink()

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class RecordingSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = RecordingSink()

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class RecordingSinkTest {

    @Test
    fun `records Queued Started Finished for one traced dispatch`() = runTest {
        val sink = RecordingSink()
        val traced = testDispatcherProvider().traced(sink)

        withContext(traced.io) { }

        assertEquals(1, sink.eventsOf<TraceEvent.Queued>().size)
        assertEquals(1, sink.eventsOf<TraceEvent.Started>().size)
        assertEquals(1, sink.eventsOf<TraceEvent.Finished>().size)
        sink.clear()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `a disabled sink records nothing and forwards the block`() = runTest {
        val sink = RecordingSink(maxLevel = null)
        val traced = testDispatcherProvider().traced(sink)

        val value = withContext(traced.io) { "forwarded" }

        assertEquals("forwarded", value)
        assertTrue(sink.events.isEmpty())
        assertTrue(TraceLevel.entries.none { sink.isEnabled(it) })
    }
}
