package com.santimattius.basic.skeleton.storm.ui

import androidx.lifecycle.ViewModel
import com.santimattius.basic.skeleton.storm.StormConstants
import com.santimattius.basic.skeleton.storm.di.AppScope
import com.santimattius.basic.skeleton.storm.instrumentation.MetricsSnapshot
import com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics
import com.santimattius.basic.skeleton.storm.lab.StormLab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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
        StormLab.unleashStorm(
            appScope,
            metrics.defaultInstrumented,
            StormConstants.STORM_COROUTINE_COUNT,
        )
    }

    fun runStructured() {
        appScope.launch {
            StormLab.structured(
                metrics.ioInstrumented,
                StormConstants.STORM_COROUTINE_COUNT,
                StormConstants.STRUCTURED_PERMITS,
            )
        }
    }
}
