package com.santimattius.basic.skeleton.runtime

import com.santimattius.basic.skeleton.runtime.validation.MainThreadProbe

/**
 * JVM stand-in for the Android main thread: anything that is not a `Dispatchers.Default/IO` worker
 * counts as "main" (runTest's test thread), mirroring how `Looper.getMainLooper()` behaves on a device.
 */
object FakeMainThreadProbe : MainThreadProbe {
    override val isMainDispatcherAvailable: Boolean = false

    override fun isOnMainThread(): Boolean = !Thread.currentThread().name.startsWith("DefaultDispatcher-worker")
}
