package com.fossscout.feature.matching

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.matching.MatchQuery
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.forInput
import com.fossscout.core.matching.from
import com.fossscout.core.matching.looksLikePackageName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the FOSS match screen. Matching itself stays in core:matching; this
 * class only resolves user input to an installed app and owns presentation
 * state such as hidden alternatives.
 */
class MatchingViewModel(
    private val repository: MatchingRepository,
    private val appSource: InstalledAppSource,
    includeAlternatives: Boolean = true,
    initialQuery: String? = null,
    initialApp: InstalledApp? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()
    private var includeAlternativesEnabled: Boolean = includeAlternatives

    init {
        when {
            initialApp != null -> matchApp(initialApp)
            !initialQuery.isNullOrBlank() -> {
                _uiState.update { it.copy(query = initialQuery.trim()) }
                checkInput()
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    /** Keeps the existing view model in sync when the feature toggle changes. */
    fun setIncludeAlternatives(enabled: Boolean) {
        includeAlternativesEnabled = enabled
    }

    fun checkInput() {
        val input = _uiState.value.query.trim()
        if (input.isEmpty()) {
            _uiState.update { it.copy(error = MatchError.QUERY_REQUIRED) }
            return
        }
        viewModelScope.launch {
            beginMatch()
            val apps = try {
                if (looksLikePackageName(input)) {
                    emptyList()
                } else {
                    appSource.search(input, limit = 10)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Installed app search failed", throwable)
                _uiState.update {
                    it.copy(isLoading = false, error = MatchError.SEARCH_FAILED)
                }
                return@launch
            }

            when {
                apps.isEmpty() -> runMatch(MatchQuery.forInput(input))
                apps.size == 1 -> {
                    val app = apps.single()
                    _uiState.update { it.copy(query = app.packageName) }
                    runMatch(MatchQuery.from(app))
                }
                else -> _uiState.update {
                    it.copy(isLoading = false, candidates = apps)
                }
            }
        }
    }

    fun selectCandidate(app: InstalledApp) = matchApp(app)

    fun matchApp(app: InstalledApp) {
        _uiState.update {
            it.copy(query = app.packageName, candidates = emptyList())
        }
        viewModelScope.launch {
            beginMatch()
            runMatch(MatchQuery.from(app))
        }
    }

    /** Opens a decision for an inventory entry, installed or only a lookup. */
    fun matchEntry(identity: String, app: InstalledApp?) {
        if (app != null) {
            matchApp(app)
            return
        }
        _uiState.update { it.copy(query = identity.trim()) }
        checkInput()
    }

    fun hideAlternative(packageName: String) {
        val suggestion = _uiState.value.decision
            ?.alternatives
            ?.firstOrNull { it.packageName == packageName }
            ?: return
        _uiState.update {
            it.copy(
                hiddenAlternatives = it.hiddenAlternatives + packageName,
                lastHiddenAlternative = suggestion,
            )
        }
    }

    fun undoHide() {
        val suggestion = _uiState.value.lastHiddenAlternative ?: return
        _uiState.update {
            it.copy(
                hiddenAlternatives = it.hiddenAlternatives - suggestion.packageName,
                lastHiddenAlternative = null,
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun beginMatch() {
        _uiState.update {
            it.copy(
                isLoading = true,
                decision = null,
                candidates = emptyList(),
                error = null,
                hiddenAlternatives = emptySet(),
                lastHiddenAlternative = null,
            )
        }
    }

    private suspend fun runMatch(query: MatchQuery) {
        try {
            val decision = repository.match(
                query = query,
                includeAlternatives = includeAlternativesEnabled,
            )
            _uiState.update { it.copy(isLoading = false, decision = decision) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Matching failed", throwable)
            _uiState.update {
                it.copy(isLoading = false, error = MatchError.MATCH_FAILED)
            }
        }
    }

    private companion object {
        const val TAG = "MatchingViewModel"
    }
}
