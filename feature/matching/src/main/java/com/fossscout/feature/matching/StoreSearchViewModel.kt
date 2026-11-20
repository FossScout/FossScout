package com.fossscout.feature.matching

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.index.ReproducibilityCheck
import com.fossscout.core.index.ReproducibilityChecker
import com.fossscout.core.index.ReproducibilityKey
import com.fossscout.core.index.ReproducibilityStatus
import com.fossscout.core.matching.MatchingRepository
import com.fossscout.core.matching.StoreSearchAvailabilityFilter
import com.fossscout.core.matching.StoreSearchGroup
import com.fossscout.core.matching.StoreSearchHistoryRepository
import com.fossscout.core.matching.StoreSearchLinkOnly
import com.fossscout.core.matching.StoreSearchQuery
import com.fossscout.core.matching.StoreSearchStoreOption
import com.fossscout.core.matching.StoreSearchTrustFilter
import com.fossscout.core.matching.StoreSearchUnavailable
import com.fossscout.core.store.BuiltInStores
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StoreSearchError {
    QUERY_REQUIRED,
    SEARCH_FAILED,
}

data class StoreSearchUiState(
    val query: String = "",
    val groups: List<StoreSearchGroup> = emptyList(),
    val linkOnlyStores: List<StoreSearchLinkOnly> = emptyList(),
    val unavailableStores: List<StoreSearchUnavailable> = emptyList(),
    val storeOptions: List<StoreSearchStoreOption> = emptyList(),
    val selectedStoreIds: Set<String> = emptySet(),
    val trustFilter: StoreSearchTrustFilter = StoreSearchTrustFilter.ALL,
    val availabilityFilter: StoreSearchAvailabilityFilter =
        StoreSearchAvailabilityFilter.ALL,
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val searchedAtEpochMillis: Long? = null,
    val error: StoreSearchError? = null,
    val detailPackageName: String? = null,
    val recentSearches: List<String> = emptyList(),
    val historyEnabled: Boolean = true,
    val reproducibility: Map<ReproducibilityKey, ReproducibilityCheck> = emptyMap(),
    val checkingReproducibility: Set<ReproducibilityKey> = emptySet(),
    val reproducibilityErrors: Set<ReproducibilityKey> = emptySet(),
) {
    val detailGroup: StoreSearchGroup?
        get() = detailPackageName?.let { packageName ->
            groups.firstOrNull { it.packageName == packageName }
        }

    val hasActiveFilters: Boolean
        get() = trustFilter != StoreSearchTrustFilter.ALL ||
            availabilityFilter != StoreSearchAvailabilityFilter.ALL ||
            (storeOptions.isNotEmpty() && selectedStoreIds.size != storeOptions.size)
}

class StoreSearchViewModel(
    private val repository: MatchingRepository,
    private val historyRepository: StoreSearchHistoryRepository,
    private val reproducibilityChecker: ReproducibilityChecker? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StoreSearchUiState())
    val uiState: StateFlow<StoreSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadStoreOptions()
        observeSearchHistory()
    }

    fun onQueryChange(value: String) {
        _uiState.update { state ->
            state.copy(
                query = value,
                error = state.error?.takeUnless { it == StoreSearchError.QUERY_REQUIRED },
            )
        }
    }

    fun useRecentSearch(query: String) {
        onQueryChange(query)
        search()
    }

    fun clearSearchHistory() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
        viewModelScope.launch {
            try {
                historyRepository.clear()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not clear search history", throwable)
            }
        }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                historyEnabled = enabled,
                recentSearches = if (enabled) it.recentSearches else emptyList(),
            )
        }
        viewModelScope.launch {
            try {
                historyRepository.setEnabled(enabled)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not update search history", throwable)
            }
        }
    }

    fun search() {
        val state = _uiState.value
        val query = state.query.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(error = StoreSearchError.QUERY_REQUIRED) }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null)
            }
            try {
                val response = repository.searchStores(
                    StoreSearchQuery(
                        text = query,
                        storeIds = state.selectedStoreIds,
                        trustFilter = state.trustFilter,
                        availabilityFilter = state.availabilityFilter,
                    ),
                )
                _uiState.update { current ->
                    val availableIds = response.storeOptions
                        .mapTo(mutableSetOf(), StoreSearchStoreOption::storeId)
                    val selected = current.selectedStoreIds
                        .intersect(availableIds)
                        .ifEmpty { availableIds }
                    current.copy(
                        groups = response.groups,
                        linkOnlyStores = response.linkOnlyStores,
                        unavailableStores = response.unavailableStores,
                        storeOptions = response.storeOptions,
                        selectedStoreIds = selected,
                        isLoading = false,
                        hasSearched = true,
                        searchedAtEpochMillis = response.searchedAtEpochMillis,
                        error = null,
                        detailPackageName = current.detailPackageName?.takeIf { packageName ->
                            response.groups.any { it.packageName == packageName }
                        },
                    )
                }
                recordSearchHistory(query)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Store search failed", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = StoreSearchError.SEARCH_FAILED,
                    )
                }
            }
        }
    }

    fun toggleStore(storeId: String) {
        val state = _uiState.value
        val available = state.storeOptions.mapTo(mutableSetOf(), StoreSearchStoreOption::storeId)
        if (storeId !in available) return
        val selected = state.selectedStoreIds.ifEmpty { available }
        val updated = if (storeId in selected) selected - storeId else selected + storeId
        if (updated.isEmpty()) return
        _uiState.update { it.copy(selectedStoreIds = updated) }
        refreshIfSearched()
    }

    fun setTrustFilter(filter: StoreSearchTrustFilter) {
        _uiState.update { it.copy(trustFilter = filter) }
        refreshIfSearched()
    }

    fun setAvailabilityFilter(filter: StoreSearchAvailabilityFilter) {
        _uiState.update { it.copy(availabilityFilter = filter) }
        refreshIfSearched()
    }

    fun clearFilters() {
        _uiState.update { state ->
            state.copy(
                selectedStoreIds = state.storeOptions
                    .mapTo(mutableSetOf(), StoreSearchStoreOption::storeId),
                trustFilter = StoreSearchTrustFilter.ALL,
                availabilityFilter = StoreSearchAvailabilityFilter.ALL,
            )
        }
        refreshIfSearched()
    }

    fun openDetails(packageName: String) {
        _uiState.update { it.copy(detailPackageName = packageName) }
    }

    fun dismissDetails() {
        _uiState.update { it.copy(detailPackageName = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun checkReproducibility(packageName: String, storeId: String, versionCode: Long) {
        val checker = reproducibilityChecker ?: return
        if (storeId != BuiltInStores.FDROID_ID || versionCode <= 0L) return
        val key = ReproducibilityKey(packageName, storeId, versionCode)
        val state = _uiState.value
        if (key in state.reproducibility || key in state.checkingReproducibility) return

        _uiState.update {
            it.copy(
                checkingReproducibility = it.checkingReproducibility + key,
                reproducibilityErrors = it.reproducibilityErrors - key,
            )
        }
        viewModelScope.launch {
            try {
                val result = checker.check(packageName, versionCode)
                _uiState.update { current ->
                    current.copy(
                        checkingReproducibility = current.checkingReproducibility - key,
                        reproducibility = if (result.status == ReproducibilityStatus.UNAVAILABLE) {
                            current.reproducibility
                        } else {
                            current.reproducibility + (key to result)
                        },
                        reproducibilityErrors = if (
                            result.status == ReproducibilityStatus.UNAVAILABLE
                        ) {
                            current.reproducibilityErrors + key
                        } else {
                            current.reproducibilityErrors - key
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not check reproducibility", throwable)
                _uiState.update {
                    it.copy(
                        checkingReproducibility = it.checkingReproducibility - key,
                        reproducibilityErrors = it.reproducibilityErrors + key,
                    )
                }
            }
        }
    }

    private fun refreshIfSearched() {
        if (_uiState.value.hasSearched) search()
    }

    private fun observeSearchHistory() {
        viewModelScope.launch {
            try {
                historyRepository.history.collect { history ->
                    _uiState.update {
                        it.copy(
                            recentSearches = history.queries,
                            historyEnabled = history.enabled,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not read search history", throwable)
            }
        }
    }

    private fun recordSearchHistory(query: String) {
        viewModelScope.launch {
            try {
                historyRepository.record(query)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.w(TAG, "Could not save search history", throwable)
            }
        }
    }

    private fun loadStoreOptions() {
        viewModelScope.launch {
            try {
                val options = repository.storeSearchOptions()
                _uiState.update { state ->
                    val available = options.mapTo(
                        mutableSetOf(),
                        StoreSearchStoreOption::storeId,
                    )
                    val selected = state.selectedStoreIds
                        .intersect(available)
                        .ifEmpty { available }
                    state.copy(
                        storeOptions = options,
                        selectedStoreIds = selected,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not list stores for search", throwable)
            }
        }
    }

    private companion object {
        const val TAG = "StoreSearchViewModel"
    }
}
