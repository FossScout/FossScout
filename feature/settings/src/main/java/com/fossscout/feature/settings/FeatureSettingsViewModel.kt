package com.fossscout.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FeatureSettingsRepository
import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.FeatureDescriptor
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureRegistry
import com.fossscout.core.feature.FeatureState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeatureSettingsViewModel(
    private val repository: FeatureSettingsRepository,
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor>,
) : ViewModel() {
    val states: StateFlow<List<FeatureState>> = repository.disabled
        .map(::statesFor)
        .catch { emit(statesFor(emptySet())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = statesFor(emptySet()),
        )

    fun setEnabled(id: FeatureId, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(id, enabled)
        }
    }

    private fun statesFor(disabled: Set<FeatureId>): List<FeatureState> {
        val registry = FeatureRegistry(
            profile = profile,
            descriptors = descriptors,
            userDisabled = disabled,
        )
        return descriptors.map { registry.state(it.id) }
    }
}
