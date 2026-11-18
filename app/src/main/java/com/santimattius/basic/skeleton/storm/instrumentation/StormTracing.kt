package com.santimattius.basic.skeleton.storm.instrumentation

import android.content.Context
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink as WireTraceSink
import io.github.santimattius.structured.runtime.AndroidTraceSink
import io.github.santimattius.structured.runtime.CompositeSink
import io.github.santimattius.structured.runtime.TraceSink
import io.github.santimattius.structured.runtime.tracing.PerfettoTraceSink

/**
 * Builds the sink the storm dispatchers report to.
 *
 * [AndroidTraceSink] always writes to the system trace (`android.os.Trace`). When [perfetto] is given,
 * both sinks receive every event, so the same run produces system-trace slices and an in-process
 * Perfetto trace with slice arguments (`queue_delay_ns`, `span_name`, ...) and span flows.
 */
fun stormTraceSink(perfetto: TraceSink?): TraceSink =
    if (perfetto == null) AndroidTraceSink() else CompositeSink(AndroidTraceSink(), perfetto)

/**
 * In-process Perfetto tracing for the storm lab, backed by `androidx.tracing`.
 *
 * Categories are only enabled while a system trace is recording (the driver default), so outside a
 * capture [sink] stays disabled and adds no overhead. The trace file is written under
 * `noBackupFilesDir/perfetto_traces`; call [close] to flush it.
 */
class StormPerfettoTracing(context: Context) : AutoCloseable {

    private val driver = TraceDriver(
        context = context,
        sink = WireTraceSink(context),
    )

    /** Sink that writes dispatch slices and counters into the in-process Perfetto trace. */
    val sink: TraceSink = PerfettoTraceSink(driver.tracer)

    /** Writes buffered events to the trace file without closing the driver. */
    fun flush() = driver.flush()

    override fun close() = driver.close()
}
