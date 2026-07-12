package com.santimattius.basic.skeleton

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.metrics.performance.JankStats
import com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics
import com.santimattius.basic.skeleton.storm.ui.StormScreen
import com.santimattius.basic.skeleton.ui.component.AppBar
import com.santimattius.basic.skeleton.ui.component.BasicSkeletonContainer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val NANOS_PER_MILLI_DOUBLE = 1_000_000.0

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var stormMetrics: StormMetrics

    private lateinit var jankStats: JankStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BasicSkeletonContainer {
                MainScreen()
            }
        }

        jankStats = JankStats.createAndTrack(window) { frame ->
            if (frame.isJank) {
                stormMetrics.jankReporter.onJank()
                Log.w(TAG, "Janky frame: ${frame.frameDurationUiNanos / NANOS_PER_MILLI_DOUBLE} ms")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        jankStats.isTrackingEnabled = true
    }

    override fun onPause() {
        super.onPause()
        jankStats.isTrackingEnabled = false
    }

    private companion object {
        const val TAG = "StormJankStats"
    }
}


@Composable
fun MainScreen() {
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(id = R.string.app_name),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        ) {
            StormScreen()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BasicSkeletonContainer {
        MainScreen()
    }
}