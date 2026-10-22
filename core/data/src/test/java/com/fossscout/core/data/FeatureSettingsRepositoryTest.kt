package com.fossscout.core.data

import com.fossscout.core.feature.FeatureId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsRepositoryTest {
    private val store = FakeFeatureSettingsStore()
    private val repository = FeatureSettingsRepository(store)

    @Test
    fun `disabling a feature adds it to the disabled set`() = runTest {
        repository.setEnabled(FeatureId.SHARING, false)

        assertEquals(setOf(FeatureId.SHARING), repository.disabled.first())
    }

    @Test
    fun `re-enabling a feature removes it from the disabled set`() = runTest {
        repository.setEnabled(FeatureId.SHARING, false)
        repository.setEnabled(FeatureId.SHARING, true)

        assertTrue(repository.disabled.first().isEmpty())
    }

    @Test
    fun `unknown stored ids are ignored`() = runTest {
        val store = FakeFeatureSettingsStore(setOf("SHARING", "NOT_A_REAL_FEATURE"))
        val repository = FeatureSettingsRepository(store)

        assertEquals(setOf(FeatureId.SHARING), repository.disabled.first())
    }
}

private class FakeFeatureSettingsStore(
    initial: Set<String> = emptySet(),
) : FeatureSettingsStore {
    private val state = MutableStateFlow(initial)

    override val disabled: Flow<Set<String>> = state

    override suspend fun save(disabled: Set<String>) {
        state.value = disabled
    }
}
