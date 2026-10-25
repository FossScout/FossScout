package com.fossscout.core.handoff

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HandoffPreferencesRepositoryTest {
    private val store = FakeHandoffPreferencesStore()

    @Test
    fun `preferred client round trips per app`() = runTest {
        val repository = HandoffPreferencesRepository(store)

        repository.setPreferredStore("org.example.app", CompanionStores.fdroid.id)

        assertEquals(
            CompanionStores.fdroid.id,
            repository.preferredStore("org.example.app"),
        )
        assertNull(repository.preferredStore("org.example.other"))
    }

    @Test
    fun `clear removes only one app preference`() = runTest {
        val repository = HandoffPreferencesRepository(store)
        repository.setPreferredStore("org.example.app", CompanionStores.fdroid.id)
        repository.setPreferredStore("org.example.other", CompanionStores.droidify.id)

        repository.clearPreferredStore("org.example.app")

        assertNull(repository.preferredStore("org.example.app"))
        assertEquals(
            CompanionStores.droidify.id,
            repository.preferredStore("org.example.other"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown client id is rejected`() = runTest {
        HandoffPreferencesRepository(store)
            .setPreferredStore("org.example.app", "unknown-client")
    }

    @Test
    fun `replace restores known clients and drops unknown ones`() = runTest {
        val repository = HandoffPreferencesRepository(store)

        repository.replace(
            HandoffPreferences(
                preferredStores = mapOf(
                    " org.example.app " to CompanionStores.fdroid.id,
                    "org.example.other" to "unknown-client",
                ),
            ),
        )

        assertEquals(
            CompanionStores.fdroid.id,
            repository.preferredStore("org.example.app"),
        )
        assertNull(repository.preferredStore("org.example.other"))
    }

    @Test
    fun `unchanged preference does not write again`() = runTest {
        val repository = HandoffPreferencesRepository(store)
        repository.setPreferredStore("org.example.app", CompanionStores.fdroid.id)
        val writes = store.writes

        repository.setPreferredStore("org.example.app", CompanionStores.fdroid.id)

        assertEquals(writes, store.writes)
    }

    private class FakeHandoffPreferencesStore : HandoffPreferencesStore {
        private val state = MutableStateFlow(HandoffPreferences())
        override val preferences: Flow<HandoffPreferences> = state.asStateFlow()
        var writes: Int = 0
            private set

        override suspend fun save(preferences: HandoffPreferences) {
            writes += 1
            state.value = preferences
        }
    }
}
