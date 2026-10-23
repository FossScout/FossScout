package com.fossscout.feature.settings

import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.data.FeatureSettingsStore
import com.fossscout.core.feature.Availability
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.Capability
import com.fossscout.core.feature.FeatureDescriptor
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSetting
import com.fossscout.core.feature.FeatureSettingSection
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
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
    private val prefixSetting = FeatureSetting(
        key = "prefix",
        section = FeatureSettingSection.BASIC,
        defaultValue = FeatureSettingValue.TextValue(""),
    )
    private val confirmSetting = FeatureSetting(
        key = "confirm",
        section = FeatureSettingSection.ADVANCED,
        defaultValue = FeatureSettingValue.BooleanValue(false),
    )
    private val favourites = FeatureDescriptor(
        id = FeatureId.FAVOURITES,
        availability = Availability.GENERAL,
        requiredCapabilities = emptySet(),
        settings = listOf(prefixSetting, confirmSetting),
    )
    private val sharing = FeatureDescriptor(
        id = FeatureId.SHARING,
        availability = Availability.GENERAL,
        requiredCapabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
    )
    private val standalone = FeatureDescriptor(
        id = FeatureId.STANDALONE,
        availability = Availability.RESTRICTED,
        requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
    )
    private val descriptors = listOf(favourites, sharing, standalone)
    private val profile = BuildProfile(
        name = "test",
        capabilities = setOf(Capability.SHARE_IMPORT_EXPORT),
    )

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
        val viewModel = viewModel(store)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.basicItem(FeatureId.FAVOURITES).state.enabled)

        store.writeError = IOException("disk full")
        viewModel.setEnabled(FeatureId.FAVOURITES, false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FeatureSettingsError.WRITE_FAILED, state.error)
        assertTrue(state.basicItem(FeatureId.FAVOURITES).state.enabled)
    }

    @Test
    fun `read failure is visible and shows a safe default state`() = runTest(dispatcher) {
        val store = FakeFeatureSettingsStore(readError = IOException("disk unreadable"))
        val viewModel = viewModel(store)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(FeatureSettingsError.READ_FAILED, state.error)
        assertFalse(state.isLoading)
        assertTrue(state.states.isNotEmpty())
        assertTrue(state.basicItem(FeatureId.FAVOURITES).state.enabled)
    }

    @Test
    fun `basic and advanced sections are generated from descriptors`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFeatureSettingsStore())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val favouritesItem = state.basicItem(FeatureId.FAVOURITES)
        val advancedItem = state.advancedItems.first {
            it.state.descriptor.id == FeatureId.FAVOURITES
        }

        assertEquals(listOf(prefixSetting.key), favouritesItem.settings.map { it.setting.key })
        assertEquals(listOf(confirmSetting.key), advancedItem.settings.map { it.setting.key })
        assertTrue(favouritesItem.showToggle)
        assertFalse(advancedItem.showToggle)
        assertFalse(state.showAdvanced)

        viewModel.toggleAdvanced()

        assertTrue(viewModel.uiState.value.showAdvanced)
    }

    @Test
    fun `individual reset restores declared defaults`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFeatureSettingsStore())
        advanceUntilIdle()
        viewModel.setSettingValue(
            FeatureId.FAVOURITES,
            prefixSetting.key,
            FeatureSettingValue.TextValue("home"),
        )
        viewModel.setEnabled(FeatureId.FAVOURITES, false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.basicItem(FeatureId.FAVOURITES).isModified)

        viewModel.resetFeature(FeatureId.FAVOURITES)
        advanceUntilIdle()

        val item = viewModel.uiState.value.basicItem(FeatureId.FAVOURITES)
        assertTrue(item.state.enabled)
        assertEquals(
            FeatureSettingValue.TextValue(""),
            item.settings.first { it.setting.key == prefixSetting.key }.value,
        )
        assertFalse(item.isModified)
    }

    @Test
    fun `global reset restores every declared default`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFeatureSettingsStore())
        advanceUntilIdle()
        viewModel.setEnabled(FeatureId.SHARING, false)
        viewModel.setSettingValue(
            FeatureId.FAVOURITES,
            prefixSetting.key,
            FeatureSettingValue.TextValue("home"),
        )
        advanceUntilIdle()

        viewModel.resetAll()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.basicItem(FeatureId.SHARING).state.enabled)
        assertEquals(
            FeatureSettingValue.TextValue(""),
            state.basicItem(FeatureId.FAVOURITES)
                .settings.first { it.setting.key == prefixSetting.key }
                .value,
        )
        assertFalse(state.basicItem(FeatureId.FAVOURITES).isModified)
    }

    @Test
    fun `restricted feature is explained and cannot be enabled`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFeatureSettingsStore())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.basicItems.any { it.state.descriptor.id == FeatureId.STANDALONE })
        val unavailable = state.unavailableStates.first {
            it.descriptor.id == FeatureId.STANDALONE
        }
        assertFalse(unavailable.available)
        assertTrue(Capability.DIRECT_APK_INSTALL in unavailable.missingCapabilities)

        viewModel.setEnabled(FeatureId.STANDALONE, true)
        advanceUntilIdle()

        assertEquals(FeatureSettingsError.WRITE_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.basicItems.any {
            it.state.descriptor.id == FeatureId.STANDALONE
        })
    }

    @Test
    fun `setting values survive disable and re-enable`() = runTest(dispatcher) {
        val viewModel = viewModel(FakeFeatureSettingsStore())
        advanceUntilIdle()
        viewModel.setSettingValue(
            FeatureId.FAVOURITES,
            prefixSetting.key,
            FeatureSettingValue.TextValue("home"),
        )
        viewModel.setEnabled(FeatureId.FAVOURITES, false)
        advanceUntilIdle()
        viewModel.setEnabled(FeatureId.FAVOURITES, true)
        advanceUntilIdle()

        val item = viewModel.uiState.value.basicItem(FeatureId.FAVOURITES)
        assertTrue(item.state.enabled)
        assertEquals(
            FeatureSettingValue.TextValue("home"),
            item.settings.first { it.setting.key == prefixSetting.key }.value,
        )
    }

    private fun viewModel(store: FakeFeatureSettingsStore) = FeatureSettingsViewModel(
        repository = FeatureSettingsRepository(
            store = store,
            profile = profile,
            descriptors = descriptors,
        ),
    )

    private fun FeatureSettingsUiState.basicItem(id: FeatureId): FeatureSettingsItem =
        basicItems.first { item -> item.state.descriptor.id == id }
}

private class FakeFeatureSettingsStore(
    initial: FeatureSettings = FeatureSettings(),
    var readError: Throwable? = null,
    var writeError: Throwable? = null,
) : FeatureSettingsStore {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<FeatureSettings> = flow {
        readError?.let { throw it }
        emitAll(state)
    }

    override suspend fun save(settings: FeatureSettings) {
        writeError?.let { throw it }
        state.value = settings
    }
}
