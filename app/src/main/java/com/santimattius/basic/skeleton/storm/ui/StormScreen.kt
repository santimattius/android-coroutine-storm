package com.santimattius.basic.skeleton.storm.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.santimattius.basic.skeleton.storm.StormConstants
import com.santimattius.basic.skeleton.storm.instrumentation.MetricsSnapshot

@Composable
fun StormScreen(viewModel: StormViewModel = hiltViewModel<StormViewModel>()) {
    val metrics by viewModel.snapshot.collectAsStateWithLifecycle()

    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = StormConstants.FULL_ROTATION_DEGREES,
        animationSpec = infiniteRepeatable(
            animation = tween(StormConstants.SPIN_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )

    LaunchedEffect(angle) { viewModel.refresh() }

    StormContent(
        angle = angle,
        metrics = metrics,
        onUnleashStorm = viewModel::unleashStorm,
        onRunStructured = viewModel::runStructured,
        onReset = viewModel::reset,
    )
}

@Composable
private fun StormContent(
    angle: Float,
    metrics: MetricsSnapshot,
    onUnleashStorm: () -> Unit,
    onRunStructured: () -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(StormDimens.SCREEN_PADDING)) {
        Text("Coroutine Storm Lab", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(StormDimens.SPACER_LARGE))

        Box(
            Modifier
                .size(StormDimens.BOX_SIZE)
                .rotate(angle)
                .background(StormDimens.BOX_COLOR, RoundedCornerShape(StormDimens.CORNER_RADIUS)),
        )
        Spacer(Modifier.height(StormDimens.SPACER_LARGE))

        MetricsPanel(metrics)
        Spacer(Modifier.height(StormDimens.SPACER_LARGE))

        Button(onClick = onUnleashStorm, modifier = Modifier.fillMaxWidth()) {
            Text("Unleash storm (unbounded)")
        }
        Spacer(Modifier.height(StormDimens.SPACER_SMALL))
        Button(onClick = onRunStructured, modifier = Modifier.fillMaxWidth()) {
            Text("Run structured (bounded concurrency)")
        }
        Spacer(Modifier.height(StormDimens.SPACER_SMALL))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset metrics")
        }
    }
}

@Composable
private fun MetricsPanel(metrics: MetricsSnapshot) {
    Column {
        MetricRow("Default - peak concurrency", metrics.defaultPeak.toString())
        MetricRow("Default - max queue delay", "${metrics.defaultQueueDelayMs} ms")
        MetricRow("IO - peak concurrency", metrics.ioPeak.toString())
        MetricRow("IO - max queue delay", "${metrics.ioQueueDelayMs} ms")
        MetricRow("Janky frames", metrics.jankFrames.toString())
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = StormDimens.ROW_PADDING)) {
        Text(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
