package com.fossscout.core.data

import com.fossscout.core.feature.Availability
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.Capability
import com.fossscout.core.feature.FEATURE_SETTINGS_SCHEMA_VERSION
import com.fossscout.core.feature.FeatureDescriptor
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSetting
import com.fossscout.core.feature.FeatureSettingSection
import com.fossscout.core.feature.FeatureSettingValidation
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.feature.FeatureState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSettingsRepositoryTest {
    private val notePrefix = FeatureSetting(
        key = "note_prefix",
        section = FeatureSettingSection.BASIC,
        defaultValue = FeatureSettingValue.TextValue(""),
        validation = FeatureSettingValidation.TextConstraints(maxLength = 5),
    )
    private val confirmReset = FeatureSetting(
        key = "confirm_reset",
        section = FeatureSettingSection.ADVANCED,
        defaultValue = FeatureSettingValue.BooleanValue(false),
    )
    private val favourites = FeatureDescriptor(
        id = FeatureId.FAVOURITES,
        availability = Availability.GENERAL,
        requiredCapabilities = emptySet(),
        settings = listOf(notePrefix, confirmReset),
    )
    private val networkFlag = FeatureSetting(
        key = "network_flag",
        section = FeatureSettingSection.BASIC,
        defaultValue = FeatureSettingValue.BooleanValue(false),
    )
    private val sharing = FeatureDescriptor(
        id = FeatureId.SHARING,
        availability = Availability.GENERAL,
        requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
        settings = listOf(networkFlag),
    )
    private val standalone = FeatureDescriptor(
        id = FeatureId.STANDALONE,
        availability = Availability.RESTRICTED,
        requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
    )
    private val descriptors = listOf(favourites, sharing, standalone)
    private val fullProfile = BuildProfile(
        name = "test",
        capabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
    )
    private val restrictedProfile = BuildProfile(name = "playstore", capabilities = emptySet())

    @Test
    fun `snapshot is versioned and starts empty`() = runTest {
        val repository = repository()

        val snapshot = repository.snapshot.first()

        assertEquals(FEATURE_SETTINGS_SCHEMA_VERSION, snapshot.schemaVersion)
        assertTrue(snapshot.disabledFeatureIds.isEmpty())
        assertTrue(snapshot.values.isEmpty())
    }

    @Test
    fun `disabling and re-enabling preserves declared setting values`() = runTest {
        val repository = repository()

        repository.setSettingValue(
            FeatureId.FAVOURITES,
            notePrefix.key,
            FeatureSettingValue.TextValue("home"),
        )
        repository.setEnabled(FeatureId.FAVOURITES, false)
        assertFalse(repository.state(FeatureId.FAVOURITES).enabled)

        repository.setEnabled(FeatureId.FAVOURITES, true)

        val state = repository.state(FeatureId.FAVOURITES)
        assertTrue(state.enabled)
        assertEquals(FeatureSettingValue.TextValue("home"), state.settingValues[notePrefix.key])
    }

    @Test
    fun `individual reset restores defaults and clears values`() = runTest {
        val repository = repository()
        repository.setSettingValue(
            FeatureId.FAVOURITES,
            notePrefix.key,
            FeatureSettingValue.TextValue("home"),
        )
        repository.setSettingValue(
            FeatureId.FAVOURITES,
            confirmReset.key,
            FeatureSettingValue.BooleanValue(true),
        )
        repository.setEnabled(FeatureId.FAVOURITES, false)

        repository.resetFeature(FeatureId.FAVOURITES)

        val state = repository.state(FeatureId.FAVOURITES)
        assertTrue(state.enabled)
        assertEquals(FeatureSettingValue.TextValue(""), state.settingValues[notePrefix.key])
        assertEquals(FeatureSettingValue.BooleanValue(false), state.settingValues[confirmReset.key])
        assertTrue(repository.snapshot.first().values.isEmpty())
    }

    @Test
    fun `global reset restores every declared default`() = runTest {
        val repository = repository()
        repository.setEnabled(FeatureId.SHARING, false)
        repository.setSettingValue(
            FeatureId.FAVOURITES,
            notePrefix.key,
            FeatureSettingValue.TextValue("home"),
        )

        repository.resetAll()

        val snapshot = repository.snapshot.first()
        assertTrue(snapshot.disabledFeatureIds.isEmpty())
        assertTrue(snapshot.values.isEmpty())
        assertTrue(repository.state(FeatureId.SHARING).enabled)
        assertEquals(
            FeatureSettingValue.TextValue(""),
            repository.state(FeatureId.FAVOURITES).settingValues[notePrefix.key],
        )
    }

    @Test
    fun `restricted feature cannot be enabled at runtime`() = runTest {
        val repository = repository(profile = restrictedProfile)

        val error = runCatching {
            repository.setEnabled(FeatureId.STANDALONE, true)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        val state = repository.state(FeatureId.STANDALONE)
        assertFalse(state.available)
        assertFalse(state.enabled)
        assertTrue(Capability.DIRECT_APK_INSTALL in state.missingCapabilities)
    }

    @Test
    fun `restricted feature rejects setting values`() = runTest {
        val repository = repository(profile = restrictedProfile)

        val error = runCatching {
            repository.setSettingValue(
                FeatureId.SHARING,
                networkFlag.key,
                FeatureSettingValue.BooleanValue(true),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `invalid value and unknown setting are rejected`() = runTest {
        val repository = repository()

        val invalid = runCatching {
            repository.setSettingValue(
                FeatureId.FAVOURITES,
                notePrefix.key,
                FeatureSettingValue.TextValue("too long"),
            )
        }.exceptionOrNull()
        val unknown = runCatching {
            repository.setSettingValue(
                FeatureId.FAVOURITES,
                "missing",
                FeatureSettingValue.TextValue("value"),
            )
        }.exceptionOrNull()

        assertTrue(invalid is IllegalArgumentException)
        assertTrue(unknown is IllegalArgumentException)
        assertTrue(repository.snapshot.first().values.isEmpty())
    }

    @Test
    fun `stale invalid stored values fall back to defaults`() = runTest {
        val store = FakeFeatureSettingsStore(
            FeatureSettings(
                values = mapOf(
                    FeatureId.FAVOURITES to mapOf(
                        notePrefix.key to FeatureSettingValue.TextValue("too long"),
                        "removed_setting" to FeatureSettingValue.TextValue("old"),
                    ),
                ),
            ),
        )
        val repository = repository(store = store)

        val state = repository.state(FeatureId.FAVOURITES)

        assertEquals(FeatureSettingValue.TextValue(""), state.settingValues[notePrefix.key])
        assertNull(repository.snapshot.first().values[FeatureId.FAVOURITES])
    }

    @Test
    fun `replace snapshot drops stale settings and saves the rest`() = runTest {
        val store = FakeFeatureSettingsStore()
        val repository = repository(store = store)

        repository.replaceSnapshot(
            FeatureSettings(
                disabledFeatureIds = setOf(FeatureId.SHARING),
                values = mapOf(
                    FeatureId.FAVOURITES to mapOf(
                        notePrefix.key to FeatureSettingValue.TextValue("ok"),
                        "removed_setting" to FeatureSettingValue.TextValue("old"),
                    ),
                ),
            ),
        )

        assertEquals(setOf(FeatureId.SHARING), store.state.value.disabledFeatureIds)
        assertEquals(
            FeatureSettingValue.TextValue("ok"),
            store.state.value.values[FeatureId.FAVOURITES]?.get(notePrefix.key),
        )
        assertNull(store.state.value.values[FeatureId.FAVOURITES]?.get("removed_setting"))
    }

    @Test
    fun `stored state survives profile drift while availability stays authoritative`() = runTest {
        val store = FakeFeatureSettingsStore(
            FeatureSettings(disabledFeatureIds = setOf(FeatureId.SHARING)),
        )
        val restrictedRepository = repository(store = store, profile = restrictedProfile)
        val fullRepository = repository(store = store, profile = fullProfile)

        val unavailable = restrictedRepository.state(FeatureId.SHARING)
        assertFalse(unavailable.available)
        assertFalse(unavailable.enabled)

        val drifted = fullRepository.state(FeatureId.SHARING)
        assertTrue(drifted.available)
        assertFalse(drifted.enabled)

        fullRepository.setEnabled(FeatureId.SHARING, true)
        assertTrue(fullRepository.state(FeatureId.SHARING).enabled)
        assertTrue(store.state.value.disabledFeatureIds.isEmpty())

        val afterDrift = restrictedRepository.state(FeatureId.SHARING)
        assertFalse(afterDrift.available)
        assertFalse(afterDrift.enabled)
    }

    private fun repository(
        store: FakeFeatureSettingsStore = FakeFeatureSettingsStore(),
        profile: BuildProfile = fullProfile,
    ) = FeatureSettingsRepository(
        store = store,
        profile = profile,
        descriptors = descriptors,
    )

    private suspend fun FeatureSettingsRepository.state(id: FeatureId): FeatureState =
        states.first().first { state -> state.descriptor.id == id }
}

private class FakeFeatureSettingsStore(
    initial: FeatureSettings = FeatureSettings(),
    var readError: Throwable? = null,
    var writeError: Throwable? = null,
) : FeatureSettingsStore {
    val state = MutableStateFlow(initial)

    override val settings: Flow<FeatureSettings> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(settings: FeatureSettings) {
        writeError?.let { throw it }
        state.value = settings
    }
}
