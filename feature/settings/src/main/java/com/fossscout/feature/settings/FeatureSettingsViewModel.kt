package com.fossscout.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSetting
import com.fossscout.core.feature.FeatureSettingSection
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeatureSettingsError { READ_FAILED, WRITE_FAILED }

data class FeatureSettingValueState(
    val setting: FeatureSetting,
    val value: FeatureSettingValue,
)

data class FeatureSettingsItem(
    val state: FeatureState,
    val settings: List<FeatureSettingValueState>,
    val showToggle: Boolean,
    val isModified: Boolean,
)

data class FeatureSettingsUiState(
    val states: List<FeatureState> = emptyList(),
    val basicItems: List<FeatureSettingsItem> = emptyList(),
    val advancedItems: List<FeatureSettingsItem> = emptyList(),
    val unavailableStates: List<FeatureState> = emptyList(),
    val showAdvanced: Boolean = false,
    val isLoading: Boolean = true,
    val error: FeatureSettingsError? = null,
)

class FeatureSettingsViewModel(
    private val repository: FeatureSettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeatureSettingsUiState())
    val uiState: StateFlow<FeatureSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.states
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read feature settings", throwable)
                    _uiState.update { state ->
                        buildUiState(
                            states = repository.defaultStates(),
                            showAdvanced = state.showAdvanced,
                            isLoading = false,
                            error = FeatureSettingsError.READ_FAILED,
                        )
                    }
                }
                .collect { states ->
                    _uiState.update { state ->
                        buildUiState(
                            states = states,
                            showAdvanced = state.showAdvanced,
                            isLoading = false,
                            error = state.error
                                ?.takeUnless { it == FeatureSettingsError.READ_FAILED },
                        )
                    }
                }
        }
    }

    fun setEnabled(id: FeatureId, enabled: Boolean) = write {
        repository.setEnabled(id, enabled)
    }

    fun setSettingValue(id: FeatureId, key: String, value: FeatureSettingValue) = write {
        repository.setSettingValue(id, key, value)
    }

    fun resetFeature(id: FeatureId) = write {
        repository.resetFeature(id)
    }

    fun resetAll() = write {
        repository.resetAll()
    }

    fun toggleAdvanced() {
        _uiState.update { state ->
            if (state.advancedItems.isEmpty()) {
                state
            } else {
                state.copy(showAdvanced = !state.showAdvanced)
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _uiState.update { state ->
                    state.copy(
                        error = state.error
                            ?.takeUnless { it == FeatureSettingsError.WRITE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to update feature settings", throwable)
                _uiState.update { state ->
                    state.copy(error = FeatureSettingsError.WRITE_FAILED)
                }
            }
        }
    }

    private fun buildUiState(
        states: List<FeatureState>,
        showAdvanced: Boolean,
        isLoading: Boolean,
        error: FeatureSettingsError?,
    ): FeatureSettingsUiState {
        val availableStates = states.filter(FeatureState::available)
        val basicItems = availableStates.map { state ->
            FeatureSettingsItem(
                state = state,
                settings = settingValuesFor(state, FeatureSettingSection.BASIC),
                showToggle = state.descriptor.id != FeatureId.SETTINGS,
                isModified = isModified(state),
            )
        }
        val advancedItems = availableStates.mapNotNull { state ->
            val settings = settingValuesFor(state, FeatureSettingSection.ADVANCED)
            if (settings.isEmpty()) {
                null
            } else {
                FeatureSettingsItem(
                    state = state,
                    settings = settings,
                    showToggle = false,
                    isModified = isModified(state),
                )
            }
        }

        return FeatureSettingsUiState(
            states = states,
            basicItems = basicItems,
            advancedItems = advancedItems,
            unavailableStates = states.filterNot(FeatureState::available),
            showAdvanced = showAdvanced && advancedItems.isNotEmpty(),
            isLoading = isLoading,
            error = error,
        )
    }

    private fun settingValuesFor(
        state: FeatureState,
        section: FeatureSettingSection,
    ): List<FeatureSettingValueState> =
        state.descriptor.settings
            .filter { setting -> setting.section == section }
            .map { setting ->
                FeatureSettingValueState(
                    setting = setting,
                    value = state.settingValues[setting.key] ?: setting.defaultValue,
                )
            }

    private fun isModified(state: FeatureState): Boolean =
        state.enabled != state.descriptor.defaultEnabled ||
            state.descriptor.settings.any { setting ->
                (state.settingValues[setting.key] ?: setting.defaultValue) != setting.defaultValue
            }

    private companion object {
        const val TAG = "FeatureSettingsViewModel"
    }
}
