package com.santimattius.basic.skeleton.storm.instrumentation

import org.junit.Assert.assertEquals
import org.junit.Test

private const val JANK_HITS = 3

class JankReporterTest {

    @Test
    fun `onJank increments the counter and reset zeroes it`() {
        val reporter = JankReporter()

        repeat(JANK_HITS) { reporter.onJank() }
        assertEquals(JANK_HITS, reporter.count.get())

        reporter.reset()
        assertEquals(0, reporter.count.get())
    }
}
