package com.santimattius.basic.skeleton.storm.di

import javax.inject.Qualifier

/**
 * Qualifies the process-lifetime [kotlinx.coroutines.CoroutineScope] provided by Hilt's
 * [dagger.hilt.components.SingletonComponent].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
