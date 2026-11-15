package com.santimattius.basic.skeleton.runtime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.santimattius.basic.skeleton.runtime.model.ValidationRow
import com.santimattius.basic.skeleton.runtime.model.ValidationStatus
import com.santimattius.basic.skeleton.runtime.model.execute
import com.santimattius.basic.skeleton.runtime.model.pending
import com.santimattius.basic.skeleton.runtime.validation.ValidationSuite
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.santimattius.structured.runtime.EventChannel
import io.github.santimattius.structured.runtime.StateHolder
import io.github.santimattius.structured.runtime.stateInWhileSubscribed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RuntimeValidationUiState(
    val rows: List<ValidationRow> = emptyList(),
    val running: Boolean = false,
) {
    val passed: Int get() = rows.count { it.status == ValidationStatus.PASS }
    val failed: Int get() = rows.count { it.status == ValidationStatus.FAIL }
}

sealed interface RuntimeValidationEvent {
    data class Completed(val passed: Int, val total: Int) : RuntimeValidationEvent
}

/**
 * Runs the runtime validations on `viewModelScope` and dogfoods the runtime itself:
 * [StateHolder] for state, [EventChannel] for the one-shot "finished" event and
 * [stateInWhileSubscribed] for the UI state stream.
 */
@HiltViewModel
class RuntimeValidationViewModel @Inject constructor(
    suite: ValidationSuite,
) : ViewModel() {

    private val cases = suite.cases(lifecycleOwner = viewModelScope)
    private val rows = StateHolder(cases.map { it.pending() })
    private val running = StateHolder(false)
    private val eventChannel = EventChannel<RuntimeValidationEvent>()

    val uiState: StateFlow<RuntimeValidationUiState> = combine(rows.state, running.state, ::RuntimeValidationUiState)
        .stateInWhileSubscribed(viewModelScope, RuntimeValidationUiState(rows.value))

    val events: Flow<RuntimeValidationEvent> = eventChannel.events

    fun runAll() {
        if (running.value) return
        viewModelScope.launch {
            running.set(true)
            rows.set(cases.map { it.pending() })
            cases.forEachIndexed { index, case ->
                rows.update { current ->
                    current.replaced(index, current[index].copy(status = ValidationStatus.RUNNING))
                }
                val result = case.execute()
                rows.update { current -> current.replaced(index, result) }
            }
            running.set(false)
            val finished = rows.value
            eventChannel.send(RuntimeValidationEvent.Completed(finished.count { it.passed }, finished.size))
        }
    }

    private fun List<ValidationRow>.replaced(index: Int, row: ValidationRow): List<ValidationRow> =
        mapIndexed { i, old -> if (i == index) row else old }
}
