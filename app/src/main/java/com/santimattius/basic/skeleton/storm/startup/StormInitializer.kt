package com.santimattius.basic.skeleton.storm.startup

import android.content.Context
import androidx.startup.Initializer
import com.santimattius.basic.skeleton.BuildConfig
import com.santimattius.basic.skeleton.storm.StormConstants
import com.santimattius.basic.skeleton.storm.di.StormEntryPoint
import com.santimattius.basic.skeleton.storm.lab.StormLab
import dagger.hilt.android.EntryPointAccessors

/**
 * Runs inside `InitializationProvider`, during process creation and BEFORE
 * `Application.onCreate()` and the first frame. Only fans out the storm when
 * [BuildConfig.STORM_AT_STARTUP] is true (the `storm` product flavor), which keeps
 * the Macrobenchmark comparison reproducible and gate-free (no intent extras).
 */
class StormInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        if (!BuildConfig.STORM_AT_STARTUP) return

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            StormEntryPoint::class.java,
        )
        StormLab.unleashStorm(
            entryPoint.appScope(),
            entryPoint.stormMetrics().defaultInstrumented,
            StormConstants.STORM_COROUTINE_COUNT,
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
