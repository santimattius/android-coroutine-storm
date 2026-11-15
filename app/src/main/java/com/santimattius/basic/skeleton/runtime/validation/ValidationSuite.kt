package com.santimattius.basic.skeleton.runtime.validation

import com.santimattius.basic.skeleton.runtime.model.ValidationCase
import io.github.santimattius.structured.runtime.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * Assembles every runtime validation. All dispatching goes through the injected [DispatcherProvider]
 * so the same logic runs on a device (`DefaultDispatcherProvider`) and in JVM unit tests
 * (`TestDispatcherProvider`, virtual time).
 */
class ValidationSuite @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val mainThread: MainThreadProbe,
) {

    /** [lifecycleOwner] is the scope whose cancellation the lifecycle validation simulates (viewModelScope). */
    fun cases(lifecycleOwner: CoroutineScope): List<ValidationCase> =
        InteropValidations().cases() +
            CancellationValidations().cases() +
            ParallelismValidations(dispatchers).cases() +
            ServerPatternValidations(dispatchers).cases() +
            DispatcherValidations(dispatchers, mainThread).cases() +
            TracingValidations(dispatchers).cases() +
            TracingDiagnosticsValidations(dispatchers).cases() +
            StateValidations(dispatchers).cases() +
            LifecycleValidations(dispatchers, lifecycleOwner).cases()
}
