package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DefaultDispatcherProvider
import io.github.santimattius.structured.runtime.DispatcherProvider
import io.github.santimattius.structured.runtime.InstrumentedDispatcher
import io.github.santimattius.structured.runtime.instrumented
import io.github.santimattius.structured.runtime.ioCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** `DispatcherProvider`, `DefaultDispatcherProvider`, `ioCompat`, `instrumented()` (DISPATCH_005, KMP_001). */
internal class DispatcherValidations(
    private val dispatchers: DispatcherProvider,
    private val mainThread: MainThreadProbe,
) {

    fun cases(): List<ValidationCase> = listOf(
        ValidationCase(GROUP, "DefaultDispatcherProvider maps to kotlinx Dispatchers") { defaultProviderMapping() },
        ValidationCase(GROUP, "DispatcherProvider.of: four slots, one dispatcher") { singleDispatcherProvider() },
        ValidationCase(GROUP, "DispatcherProvider.main runs on the Android main thread") { mainViaProvider() },
        ValidationCase(GROUP, "Dispatchers.ioCompat is Dispatchers.IO off the main thread") { ioCompatOnAndroid() },
        ValidationCase(GROUP, "instrumented(): per-dispatcher stats, lazy, reset") { instrumentedProvider() },
        ValidationCase(GROUP, "InstrumentedDispatcher: direct wrapper + snapshot") { instrumentedDispatcher() },
    )

    private fun defaultProviderMapping(): String {
        check(DefaultDispatcherProvider.io === Dispatchers.ioCompat) { "io != ioCompat" }
        check(Dispatchers.ioCompat === Dispatchers.IO) { "ioCompat != Dispatchers.IO on Android" }
        check(DefaultDispatcherProvider.default === Dispatchers.Default) { "default mismatch" }
        check(DefaultDispatcherProvider.unconfined === Dispatchers.Unconfined) { "unconfined mismatch" }
        if (mainThread.isMainDispatcherAvailable) {
            check(DefaultDispatcherProvider.main === Dispatchers.Main) { "main mismatch" }
        }
        return "io=IO, default=Default, unconfined=Unconfined, main=Main (resolved lazily per access)"
    }

    private fun singleDispatcherProvider(): String {
        val single: CoroutineDispatcher = Dispatchers.Unconfined
        val provider = DispatcherProvider.of(single)
        val all = listOf(provider.main, provider.io, provider.default, provider.unconfined)
        check(all.all { it === single }) { "slots differ: $all" }
        return "main/io/default/unconfined all === $single"
    }

    private suspend fun mainViaProvider(): String {
        val onMain = withContext(dispatchers.main) { mainThread.isOnMainThread() }
        check(onMain) { "withContext(dispatchers.main) was not on the main thread" }
        return "withContext(dispatchers.main) ran on the main thread (via the injected provider)"
    }

    private suspend fun ioCompatOnAndroid(): String {
        val (onMain, threadName) = withContext(Dispatchers.ioCompat) {
            mainThread.isOnMainThread() to Thread.currentThread().name
        }
        check(!onMain) { "ioCompat ran on the main thread ($threadName)" }
        return "ioCompat === Dispatchers.IO, ran on '$threadName'"
    }

    private suspend fun instrumentedProvider(): String {
        val provider = dispatchers.instrumented()
        check(provider.stats().isEmpty()) { "stats before use: ${provider.stats()}" }
        coroutineScope {
            repeat(ValidationSizes.DISPATCHES) {
                launch(provider.io) { delay(ValidationTimings.SHORT) }
                launch(provider.default) { delay(ValidationTimings.SHORT) }
            }
        }
        val stats = provider.stats()
        check(stats.map { it.label } == listOf("io", "default")) { "labels=${stats.map { it.label }}" }
        check(stats.all { it.totalDispatched >= ValidationSizes.DISPATCHES }) { "dispatch counts: $stats" }
        check(provider.unconfined === dispatchers.unconfined) { "unconfined was wrapped" }
        provider.reset()
        check(provider.stats().all { it.totalDispatched == 0L && it.peakConcurrency == 0 }) { "reset left counters" }
        return stats.joinToString { "${it.label}: dispatched=${it.totalDispatched} peak=${it.peakConcurrency}" } +
            "; main omitted until read; reset() zeroed"
    }

    private suspend fun instrumentedDispatcher(): String {
        val wrapper = InstrumentedDispatcher(dispatchers.default, "direct")
        withContext(wrapper) { delay(ValidationTimings.SHORT) }
        val snapshot = wrapper.snapshot()
        check(snapshot.label == "direct" && snapshot.totalDispatched >= 1) { "snapshot=$snapshot" }
        return "label=${snapshot.label} dispatched=${snapshot.totalDispatched} peak=${snapshot.peakConcurrency}"
    }

    private companion object {
        const val GROUP = "Dispatchers"
    }
}
