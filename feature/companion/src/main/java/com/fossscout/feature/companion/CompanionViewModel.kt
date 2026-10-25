package com.fossscout.feature.companion

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.handoff.CompanionStoreDetector
import com.fossscout.core.handoff.HandoffPlanner
import com.fossscout.core.handoff.HandoffPreferencesRepository
import com.fossscout.core.handoff.ReplacementAssessmentEngine
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.store.StoreEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Presentation state for F07 guidance and F08 handoff. The view model reads the
 * installed app, detects explicit store clients, and persists the preferred
 * client. It never installs, uninstalls, or deletes anything.
 */
class CompanionViewModel(
    private val appSource: InstalledAppSource,
    private val stores: Flow<List<StoreEntry>>,
    private val detector: CompanionStoreDetector,
    private val preferences: HandoffPreferencesRepository,
    showReplacementGuidance: Boolean = true,
    showHandoff: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CompanionUiState(
            showReplacementGuidance = showReplacementGuidance,
            showHandoff = showHandoff,
        ),
    )
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun open(decision: MatchDecision) {
        loadJob?.cancel()
        val candidate = decision.primary
        if (candidate == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    candidate = null,
                    installed = null,
                    storeDescriptor = null,
                    assessment = null,
                    clients = emptyList(),
                    selectedClientId = null,
                    appHandoff = null,
                    repositoryHandoff = null,
                    fallback = null,
                    error = CompanionError.NO_CANDIDATE,
                )
            }
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    candidate = candidate,
                    installed = null,
                    storeDescriptor = null,
                    assessment = null,
                    clients = emptyList(),
                    selectedClientId = null,
                    appHandoff = null,
                    repositoryHandoff = null,
                    fallback = null,
                    error = null,
                    replaceConfirmed = false,
                    showQr = false,
                    installedLookupFailed = false,
                )
            }
            var error: CompanionError? = null
            var lookupFailed = false
            val installed = try {
                appSource.find(candidate.packageName)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Installed app lookup failed", throwable)
                lookupFailed = true
                error = CompanionError.INSTALLED_LOOKUP_FAILED
                null
            }
            val clients = try {
                detector.detect()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Companion store detection failed", throwable)
                if (error == null) error = CompanionError.DETECTION_FAILED
                emptyList()
            }
            val descriptor = try {
                stores.first()
                    .firstOrNull { it.descriptor.id.value == candidate.storeId }
                    ?.descriptor
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Store descriptor lookup failed", throwable)
                if (error == null) error = CompanionError.STORE_LOOKUP_FAILED
                null
            }
            val preferred = try {
                preferences.preferredStore(candidate.appKey)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Preferred store read failed", throwable)
                if (error == null) error = CompanionError.PREFERENCE_READ_FAILED
                null
            }
            val assessment = if (lookupFailed) {
                null
            } else {
                ReplacementAssessmentEngine.assess(installed, candidate)
            }
            if (!isActive) return@launch
            val plan = HandoffPlanner.plan(
                candidate = candidate,
                descriptor = descriptor,
                clients = clients,
                preferredClientId = preferred,
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    candidate = candidate,
                    installed = installed,
                    storeDescriptor = descriptor,
                    assessment = assessment,
                    clients = clients,
                    selectedClientId = plan.selectedClient?.store?.id,
                    appHandoff = plan.appHandoff,
                    repositoryHandoff = plan.repositoryHandoff,
                    fallback = plan.fallback,
                    error = error,
                    installedLookupFailed = lookupFailed,
                )
            }
        }
    }

    fun selectClient(clientId: String) {
        val state = _uiState.value
        val candidate = state.candidate ?: return
        val client = state.clients.firstOrNull { it.store.id == clientId } ?: return
        if (!client.enabled) return
        val plan = HandoffPlanner.plan(
            candidate = candidate,
            descriptor = state.storeDescriptor,
            clients = state.clients,
            preferredClientId = clientId,
        )
        _uiState.update {
            it.copy(
                selectedClientId = plan.selectedClient?.store?.id,
                appHandoff = plan.appHandoff,
                repositoryHandoff = plan.repositoryHandoff,
                fallback = plan.fallback,
                replaceConfirmed = false,
            )
        }
        viewModelScope.launch {
            try {
                preferences.setPreferredStore(candidate.appKey, clientId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Preferred store write failed", throwable)
                _uiState.update { it.copy(error = CompanionError.PREFERENCE_WRITE_FAILED) }
            }
        }
    }

    /** Keeps the open screen in sync with live feature toggles. */
    fun setFeatureVisibility(
        showReplacementGuidance: Boolean,
        showHandoff: Boolean,
    ) {
        _uiState.update {
            it.copy(
                showReplacementGuidance = showReplacementGuidance,
                showHandoff = showHandoff,
            )
        }
    }

    fun setReplaceConfirmed(confirmed: Boolean) {
        if (!_uiState.value.requiresConfirmation) return
        _uiState.update { it.copy(replaceConfirmed = confirmed) }
    }

    fun toggleQr() {
        if (_uiState.value.repositoryHandoff == null) return
        _uiState.update { it.copy(showQr = !it.showQr) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val TAG = "CompanionViewModel"
    }
}
