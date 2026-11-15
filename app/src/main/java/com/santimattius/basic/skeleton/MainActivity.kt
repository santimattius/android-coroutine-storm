package com.santimattius.basic.skeleton

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.metrics.performance.JankStats
import com.santimattius.basic.skeleton.runtime.ui.RuntimeValidationScreen
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


private enum class MainTab(val label: String, val icon: ImageVector) {
    STORM("Storm", Icons.Filled.Bolt),
    RUNTIME("Runtime", Icons.Filled.CheckCircle),
}

@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.STORM) }
    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(id = R.string.app_name),
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        ) {
            when (selectedTab) {
                MainTab.STORM -> StormScreen()
                MainTab.RUNTIME -> RuntimeValidationScreen()
            }
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