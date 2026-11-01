package com.santimattius.basic.skeleton.runtime

import androidx.lifecycle.viewModelScope
import com.santimattius.basic.skeleton.runtime.ui.RuntimeValidationEvent
import com.santimattius.basic.skeleton.runtime.ui.RuntimeValidationViewModel
import com.santimattius.basic.skeleton.runtime.validation.ValidationSuite
import io.github.santimattius.structured.runtime.test.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuntimeValidationViewModelTest {

    private val provider = TestDispatcherProvider()
    private lateinit var viewModel: RuntimeValidationViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(provider.scheduler))
        viewModel = RuntimeValidationViewModel(ValidationSuite(provider, FakeMainThreadProbe))
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with every validation pending`() = runTest(StandardTestDispatcher(provider.scheduler)) {
        val state = viewModel.uiState.first()
        assertTrue(state.rows.isNotEmpty())
        assertEquals(0, state.passed + state.failed)
        assertTrue(!state.running)
    }

    @Test
    fun `runAll passes everything and emits Completed once`() = runTest(StandardTestDispatcher(provider.scheduler)) {
        viewModel.runAll()
        val finished = viewModel.events.first()
        val state = viewModel.uiState.first { !it.running && it.passed + it.failed == it.rows.size }

        assertEquals(RuntimeValidationEvent.Completed(state.rows.size, state.rows.size), finished)
        assertEquals(state.rows.filter { !it.passed }.joinToString { "${it.name}: ${it.detail}" }, 0, state.failed)
    }
}
