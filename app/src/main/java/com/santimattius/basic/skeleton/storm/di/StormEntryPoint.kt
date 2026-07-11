package com.santimattius.basic.skeleton.storm.di

import com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/**
 * Lets [com.santimattius.basic.skeleton.storm.startup.StormInitializer] resolve the same
 * Hilt singletons as the rest of the app via [dagger.hilt.android.EntryPointAccessors.fromApplication],
 * which only requires the `Application` object to exist — not that `onCreate()` has run.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StormEntryPoint {
    @AppScope
    fun appScope(): CoroutineScope

    fun stormMetrics(): StormMetrics
}
