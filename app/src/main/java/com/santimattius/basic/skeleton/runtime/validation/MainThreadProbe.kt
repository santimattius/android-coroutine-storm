package com.santimattius.basic.skeleton.runtime.validation

import android.os.Looper

/**
 * Abstraction over "am I on the Android main thread?" so the Main-dispatcher validation can run on
 * a device (real [Looper]) and in JVM unit tests (fake probe) with the same logic.
 */
interface MainThreadProbe {
    /** False when no Android main dispatcher exists (plain JVM), so `DispatcherProvider.main` must not be read. */
    val isMainDispatcherAvailable: Boolean

    fun isOnMainThread(): Boolean
}

object AndroidMainThreadProbe : MainThreadProbe {
    override val isMainDispatcherAvailable: Boolean = true

    override fun isOnMainThread(): Boolean = Looper.getMainLooper() === Looper.myLooper()
}
