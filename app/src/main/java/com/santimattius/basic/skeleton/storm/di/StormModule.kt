package com.santimattius.basic.skeleton.storm.di

import com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the process-lifetime coroutine scope and the storm metrics singleton, both
 * reachable from Hilt injection points and from [com.santimattius.basic.skeleton.storm.startup.StormInitializer]
 * via [dagger.hilt.android.EntryPointAccessors].
 */
@Module
@InstallIn(SingletonComponent::class)
object StormModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideStormMetrics(): StormMetrics = StormMetrics()
}
