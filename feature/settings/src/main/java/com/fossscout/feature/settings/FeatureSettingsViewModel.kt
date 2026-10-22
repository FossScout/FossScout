package com.fossscout.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.FeatureDescriptor
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureRegistry
import com.fossscout.core.feature.FeatureState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeatureSettingsError { READ_FAILED, WRITE_FAILED }

data class FeatureSettingsUiState(
    val states: List<FeatureState> = emptyList(),
    val isLoading: Boolean = true,
    val error: FeatureSettingsError? = null,
)

class FeatureSettingsViewModel(
    private val repository: FeatureSettingsRepository,
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeatureSettingsUiState())
    val uiState: StateFlow<FeatureSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.disabled
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read feature settings", throwable)
                    _uiState.update { state ->
                        state.copy(
                            states = state.states.ifEmpty { statesFor(emptySet()) },
                            isLoading = false,
                            error = FeatureSettingsError.READ_FAILED,
                        )
                    }
                }
                .collect { disabled ->
                    _uiState.update { state ->
                        state.copy(
                            states = statesFor(disabled),
                            isLoading = false,
                            error = state.error
                                .takeUnless { it == FeatureSettingsError.READ_FAILED },
                        )
                    }
                }
        }
    }

    fun setEnabled(id: FeatureId, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.setEnabled(id, enabled)
                _uiState.update { state ->
                    state.copy(
                        error = state.error
                            .takeUnless { it == FeatureSettingsError.WRITE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to update feature ${id.name}", throwable)
                _uiState.update { state ->
                    state.copy(error = FeatureSettingsError.WRITE_FAILED)
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    private fun statesFor(disabled: Set<FeatureId>): List<FeatureState> {
        val registry = FeatureRegistry(
            profile = profile,
            descriptors = descriptors,
            userDisabled = disabled,
        )
        return descriptors.map { registry.state(it.id) }
    }

    private companion object {
        const val TAG = "FeatureSettingsViewModel"
    }
}
