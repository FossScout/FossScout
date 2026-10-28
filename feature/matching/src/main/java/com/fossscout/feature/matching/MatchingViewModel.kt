package com.fossscout.feature.matching

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InstalledAppSource
import com.fossscout.core.matching.CatalogSearchResult
import com.fossscout.core.matching.MatchQuery
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.forInput
import com.fossscout.core.matching.from
import com.fossscout.core.matching.looksLikePackageName
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.MatchOverrideTarget
import com.fossscout.core.model.StoreId
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
    private val setCorrection: (suspend (AppKey, MatchOverrideTarget?, String) -> Unit)? = null,
    private val removeCorrection: (suspend (AppKey) -> Unit)? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MatchUiState())
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()
    private var includeAlternativesEnabled: Boolean = includeAlternatives
    private var lastQuery: MatchQuery? = null

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

    fun startCorrection() {
        val decision = _uiState.value.decision ?: return
        if (setCorrection == null || decision.packageName == null) return
        _uiState.update {
            it.copy(
                correctionEditor = MatchCorrectionUiState(
                    installedAppKey = AppKey.from(decision.appKey),
                    installedLabel = decision.label,
                    query = decision.label,
                ),
            )
        }
        searchCorrectionTargets()
    }

    fun onCorrectionQueryChange(value: String) {
        updateCorrectionEditor {
            it.copy(
                query = value,
                results = emptyList(),
                selected = null,
                isSearching = false,
                hasSearched = false,
                error = null,
            )
        }
    }

    fun searchCorrectionTargets() {
        val editor = _uiState.value.correctionEditor ?: return
        val query = editor.query.trim()
        if (query.isEmpty()) {
            updateCorrectionEditor { it.copy(error = MatchCorrectionError.QUERY_REQUIRED) }
            return
        }
        updateCorrectionEditor { it.copy(isSearching = true, hasSearched = false, error = null) }
        viewModelScope.launch {
            try {
                val results = repository.searchCatalog(query)
                updateCorrectionEditor { current ->
                    if (current.query.trim() != query) {
                        current
                    } else {
                        current.copy(
                            isSearching = false,
                            hasSearched = true,
                            results = results,
                            selected = null,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Correction target search failed", throwable)
                updateCorrectionEditor { current ->
                    if (current.query.trim() != query) {
                        current
                    } else {
                        current.copy(
                            isSearching = false,
                            error = MatchCorrectionError.SEARCH_FAILED,
                        )
                    }
                }
            }
        }
    }

    fun selectCorrectionTarget(result: CatalogSearchResult) {
        updateCorrectionEditor { it.copy(selected = result, error = null) }
    }

    fun onCorrectionNoteChange(value: String) {
        updateCorrectionEditor { it.copy(note = value, error = null) }
    }

    fun saveCorrection() {
        val editor = _uiState.value.correctionEditor ?: return
        if (editor.isSaving) return
        val selected = editor.selected
        if (selected == null) {
            updateCorrectionEditor { it.copy(error = MatchCorrectionError.TARGET_REQUIRED) }
            return
        }
        val save = setCorrection ?: return
        viewModelScope.launch {
            updateCorrectionEditor { it.copy(isSaving = true, error = null) }
            try {
                save(
                    editor.installedAppKey,
                    MatchOverrideTarget(
                        appKey = AppKey.from(selected.entry.packageName),
                        displayName = selected.entry.name,
                        storeId = StoreId(selected.storeId),
                        storeName = selected.storeName,
                    ),
                    editor.note,
                )
                refreshAfterCorrection()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Saving match correction failed", throwable)
                updateCorrectionEditor {
                    it.copy(isSaving = false, error = MatchCorrectionError.SAVE_FAILED)
                }
            }
        }
    }

    fun saveNoMatch() {
        val editor = _uiState.value.correctionEditor ?: return
        if (editor.isSaving) return
        val save = setCorrection ?: return
        viewModelScope.launch {
            updateCorrectionEditor { it.copy(isSaving = true, error = null) }
            try {
                save(editor.installedAppKey, null, editor.note)
                refreshAfterCorrection()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Saving no-match correction failed", throwable)
                updateCorrectionEditor {
                    it.copy(isSaving = false, error = MatchCorrectionError.SAVE_FAILED)
                }
            }
        }
    }

    fun undoCorrection() {
        val decision = _uiState.value.decision ?: return
        val remove = removeCorrection ?: return
        viewModelScope.launch {
            try {
                remove(AppKey.from(decision.appKey))
                refreshAfterCorrection()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Removing match correction failed", throwable)
                _uiState.update { it.copy(error = MatchError.CORRECTION_FAILED) }
            }
        }
    }

    fun dismissCorrection() {
        _uiState.update { it.copy(correctionEditor = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateCorrectionEditor(
        transform: (MatchCorrectionUiState) -> MatchCorrectionUiState,
    ) {
        _uiState.update { state ->
            val editor = state.correctionEditor ?: return@update state
            state.copy(correctionEditor = transform(editor))
        }
    }

    private suspend fun refreshAfterCorrection() {
        val query = lastQuery ?: return
        beginMatch()
        runMatch(query)
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
                correctionEditor = null,
            )
        }
    }

    private suspend fun runMatch(query: MatchQuery) {
        lastQuery = query
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
