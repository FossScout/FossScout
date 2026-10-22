package com.fossscout.feature.settings

import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.data.FeatureSettingsStore
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureId
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val profile = BuildProfile(name = "test", capabilities = emptySet())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `write failure is visible and keeps the previous toggle state`() = runTest(dispatcher) {
        val store = FakeFeatureSettingsStore()
        val viewModel = FeatureSettingsViewModel(
            repository = FeatureSettingsRepository(store),
            profile = profile,
            descriptors = FeatureCatalog.all,
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.states.favouritesEnabled())

        store.writeError = IOException("disk full")
        viewModel.setEnabled(FeatureId.FAVOURITES, false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FeatureSettingsError.WRITE_FAILED, state.error)
        assertTrue(state.states.favouritesEnabled())
    }

    @Test
    fun `read failure is visible and shows a safe default state`() = runTest(dispatcher) {
        val store = FakeFeatureSettingsStore(readError = IOException("disk unreadable"))
        val viewModel = FeatureSettingsViewModel(
            repository = FeatureSettingsRepository(store),
            profile = profile,
            descriptors = FeatureCatalog.all,
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FeatureSettingsError.READ_FAILED, state.error)
        assertFalse(state.isLoading)
        assertTrue(state.states.isNotEmpty())
        assertTrue(state.states.favouritesEnabled())
    }

    private fun List<com.fossscout.core.feature.FeatureState>.favouritesEnabled(): Boolean =
        first { it.descriptor.id == FeatureId.FAVOURITES }.enabled
}

private class FakeFeatureSettingsStore(
    initial: Set<String> = emptySet(),
    var readError: Throwable? = null,
    var writeError: Throwable? = null,
) : FeatureSettingsStore {
    private val state = MutableStateFlow(initial)

    override val disabled: Flow<Set<String>> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(disabled: Set<String>) {
        writeError?.let { throw it }
        state.value = disabled
    }
}
