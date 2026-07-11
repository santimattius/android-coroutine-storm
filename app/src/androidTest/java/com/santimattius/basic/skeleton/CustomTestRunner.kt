package com.santimattius.basic.skeleton

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps the real [Application] for Hilt's [HiltTestApplication] so `@HiltAndroidTest`
 * instrumented tests can install a test `SingletonComponent`.
 */
class CustomTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
