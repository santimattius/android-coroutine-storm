package com.santimattius.basic.skeleton.runtime.di

import com.santimattius.basic.skeleton.runtime.validation.AndroidMainThreadProbe
import com.santimattius.basic.skeleton.runtime.validation.MainThreadProbe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.santimattius.structured.runtime.DefaultDispatcherProvider
import io.github.santimattius.structured.runtime.DispatcherProvider
import javax.inject.Singleton

/**
 * Binds the production [DispatcherProvider] and the Android main-thread probe used by the
 * runtime validations. Tests construct `ValidationSuite` directly with a `TestDispatcherProvider`.
 */
@Module
@InstallIn(SingletonComponent::class)
object RuntimeModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider

    @Provides
    @Singleton
    fun provideMainThreadProbe(): MainThreadProbe = AndroidMainThreadProbe
}
