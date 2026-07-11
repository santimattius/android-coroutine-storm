package com.santimattius.basic.skeleton.storm.startup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.santimattius.basic.skeleton.storm.di.StormEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the exact wiring [StormInitializer] relies on: [EntryPointAccessors.fromApplication]
 * resolves [StormEntryPoint] straight off the `Application` object.
 *
 * Scope note: androidx.startup's `InitializationProvider` runs even earlier than this test can
 * reach — during `attachBaseContext`, before `Application.onCreate()` — and that exact
 * process-creation moment cannot be re-triggered from inside an already-running instrumented
 * test process. What this test DOES prove is the property the design depends on: the Hilt
 * `SingletonComponent` (and therefore the `@AppScope` [kotlinx.coroutines.CoroutineScope] and
 * [com.santimattius.basic.skeleton.storm.instrumentation.StormMetrics]) is reachable purely from
 * the bare `Application` reference, with no dependency on anything `onCreate()` sets up. That is
 * the guarantee [StormInitializer] needs, and resolving the entry point from
 * [ApplicationProvider.getApplicationContext] — available the instant the test process's
 * `Application` object exists — is the closest proxy assertion an instrumented test can make for
 * "works before onCreate".
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StormInitializerHiltEntryPointTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun entryPointResolvesAppScopeAndMetricsFromBareApplication() {
        val application = ApplicationProvider.getApplicationContext<Application>()

        val entryPoint = EntryPointAccessors.fromApplication(application, StormEntryPoint::class.java)

        assertNotNull(entryPoint.appScope())
        assertNotNull(entryPoint.stormMetrics())
    }
}
