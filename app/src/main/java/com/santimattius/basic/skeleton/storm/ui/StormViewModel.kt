package com.santimattius.basic.skeleton.storm.ui

import androidx.lifecycle.ViewModel
import com.santimattius.basic.skeleton.storm.StormConstants
import com.santimattius.basic.skeleton.storm.di.AppScope
import com.santimattius.basic.skeleton.storm.instrumentation.MetricsSnapshot
import com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics
import com.santimattius.basic.skeleton.storm.lab.StormLab
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.santimattius.structured.runtime.withTraceSpan
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes the storm lab actions and the live [MetricsSnapshot] to [StormScreen]. Storm work
 * runs on the process-lifetime [appScope] (not [androidx.lifecycle.viewModelScope]) so it
 * behaves the same way it would from a real app-wide SDK/initializer.
 */
@HiltViewModel
class StormViewModel @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    private val metrics: StormMetrics,
) : ViewModel() {

    val snapshot: StateFlow<MetricsSnapshot> = metrics.snapshot

    fun refresh() = metrics.refresh()

    fun reset() = metrics.reset()

    fun unleashStorm() {
        appScope.launch {
            traced(SPAN_UNBOUNDED) {
                coroutineScope {
                    StormLab.unleashStorm(this, metrics.defaultTraced, StormConstants.STORM_COROUTINE_COUNT)
                }
            }
        }
    }

    fun runStructured() {
        appScope.launch {
            traced(SPAN_BOUNDED) {
                StormLab.structured(
                    metrics.ioTraced,
                    StormConstants.STORM_COROUTINE_COUNT,
                    StormConstants.STRUCTURED_PERMITS,
                )
            }
        }
    }

    fun runParallelBounded() {
        appScope.launch {
            traced(SPAN_PARALLEL) {
                StormLab.parallelBounded(
                    metrics.ioTraced,
                    StormConstants.STORM_COROUTINE_COUNT,
                    StormConstants.STRUCTURED_PERMITS,
                )
            }
        }
    }

    /** Span (parent/child links) plus a [CoroutineName] so dispatcher slices read `io:storm-bounded`. */
    private suspend fun <T> traced(name: String, block: suspend () -> T): T =
        withTraceSpan(name) { withContext(CoroutineName(name)) { block() } }

    private companion object {
        const val SPAN_UNBOUNDED = "storm-unbounded"
        const val SPAN_BOUNDED = "storm-bounded"
        const val SPAN_PARALLEL = "storm-parallel"
    }
}
