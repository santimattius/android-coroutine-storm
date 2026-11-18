package com.santimattius.basic.skeleton

import android.app.Application
import android.content.ComponentCallbacks2
import com.santimattius.basic.skeleton.storm.instrumentation.StormPerfettoTracing
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    lateinit var perfettoTracing: Lazy<StormPerfettoTracing>

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The app moved to the background: persist buffered Perfetto events so the trace file is complete.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            perfettoTracing.get().flush()
        }
    }
}
