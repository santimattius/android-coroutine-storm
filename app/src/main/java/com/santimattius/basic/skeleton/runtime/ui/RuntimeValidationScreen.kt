package com.santimattius.basic.skeleton.runtime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santimattius.basic.skeleton.runtime.model.ValidationRow
import com.santimattius.basic.skeleton.runtime.model.ValidationStatus

@Composable
fun RuntimeValidationScreen(viewModel: RuntimeValidationViewModel = hiltViewModel<RuntimeValidationViewModel>()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RuntimeValidationEvent.Completed ->
                    snackbar.showSnackbar("Validations finished: ${event.passed}/${event.total} passed")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(RuntimeDimens.SCREEN_PADDING)) {
        Button(onClick = viewModel::runAll, enabled = !state.running, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.running) "Running..." else "Run all validations")
        }
        Text(
            text = "PASS ${state.passed}  FAIL ${state.failed}  TOTAL ${state.rows.size}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = RuntimeDimens.SPACER),
        )
        SnackbarHost(snackbar)
        ValidationList(state.rows)
    }
}

@Composable
private fun ValidationList(rows: List<ValidationRow>) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { "${it.group}/${it.name}" }) { row ->
            ValidationItem(row)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ValidationItem(row: ValidationRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = RuntimeDimens.ROW_PADDING)) {
        Text(row.group, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.Start) {
            Text(
                text = row.status.label(),
                color = row.status.color(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(RuntimeDimens.STATUS_WIDTH),
            )
            Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        if (row.detail.isNotEmpty()) {
            Text(row.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun ValidationStatus.label(): String = when (this) {
    ValidationStatus.PENDING -> "..."
    ValidationStatus.RUNNING -> "RUN"
    ValidationStatus.PASS -> "PASS"
    ValidationStatus.FAIL -> "FAIL"
}

private fun ValidationStatus.color(): Color = when (this) {
    ValidationStatus.PENDING -> RuntimeDimens.PENDING_COLOR
    ValidationStatus.RUNNING -> RuntimeDimens.RUNNING_COLOR
    ValidationStatus.PASS -> RuntimeDimens.PASS_COLOR
    ValidationStatus.FAIL -> RuntimeDimens.FAIL_COLOR
}
