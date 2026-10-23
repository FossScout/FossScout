package com.fossscout.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.fossscout.core.feature.FEATURE_SETTINGS_SCHEMA_VERSION
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreFeatureSettingsStoreTest {
    private val envelopeKey = stringPreferencesKey("feature_settings")
    private val legacyKey = stringSetPreferencesKey("disabled_features")

    @Test
    fun `legacy disabled preference is migrated into the versioned envelope`() = runTest {
        val dataStore = FakePreferencesDataStore(
            emptyPreferences()
                .toMutablePreferences()
                .apply {
                    this[legacyKey] = setOf("SHARING", "NOT_A_FEATURE")
                },
        )
        val store = DataStoreFeatureSettingsStore(dataStore)

        val settings = store.settings.first()

        assertEquals(FEATURE_SETTINGS_SCHEMA_VERSION, settings.schemaVersion)
        assertEquals(setOf(FeatureId.SHARING), settings.disabledFeatureIds)
        val preferences = dataStore.data.first()
        assertNull(preferences[legacyKey])
        val encoded = preferences[envelopeKey]
        assertNotNull(encoded)
        assertEquals(
            setOf(FeatureId.SHARING),
            FeatureSettingsCodec().decode(encoded!!).disabledFeatureIds,
        )
    }

    @Test
    fun `fresh settings get an empty versioned envelope`() = runTest {
        val dataStore = FakePreferencesDataStore(emptyPreferences())
        val store = DataStoreFeatureSettingsStore(dataStore)

        val settings = store.settings.first()

        assertEquals(FeatureSettings(), settings)
        val encoded = dataStore.data.first()[envelopeKey]
        assertNotNull(encoded)
        assertEquals(FeatureSettings(), FeatureSettingsCodec().decode(encoded!!))
    }

    @Test
    fun `save replaces the envelope and removes the legacy preference`() = runTest {
        val dataStore = FakePreferencesDataStore(
            emptyPreferences()
                .toMutablePreferences()
                .apply {
                    this[legacyKey] = setOf("SHARING")
                },
        )
        val store = DataStoreFeatureSettingsStore(dataStore)

        store.save(FeatureSettings(disabledFeatureIds = setOf(FeatureId.BACKUP)))

        val preferences = dataStore.data.first()
        assertNull(preferences[legacyKey])
        val encoded = preferences[envelopeKey]
        assertNotNull(encoded)
        val decoded = FeatureSettingsCodec().decode(encoded!!)
        assertEquals(setOf(FeatureId.BACKUP), decoded.disabledFeatureIds)
        assertTrue(decoded.values.isEmpty())
    }
}

private class FakePreferencesDataStore(
    initial: Preferences,
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
