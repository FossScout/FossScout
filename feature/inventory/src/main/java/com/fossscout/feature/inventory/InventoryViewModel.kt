package com.fossscout.feature.inventory

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InventoryEntry
import com.fossscout.core.inventory.InventoryFilters
import com.fossscout.core.inventory.InventoryRepository
import com.fossscout.core.inventory.InventorySnapshot
import com.fossscout.core.inventory.PackageIdentityParser
import com.fossscout.core.inventory.filteredEntries
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InventoryError {
    READ_FAILED,
    CHECK_FAILED,
    SEARCH_FAILED,
    SCAN_FAILED,
    UPDATE_FAILED,
}

data class InventoryUiState(
    val snapshot: InventorySnapshot? = null,
    val hiddenPackages: Set<String> = emptySet(),
    val filters: InventoryFilters = InventoryFilters(),
    val query: String = "",
    val candidates: List<InstalledApp> = emptyList(),
    val isSearching: Boolean = false,
    val isChecking: Boolean = false,
    val isScanning: Boolean = false,
    val canFullScan: Boolean = false,
    val isLoading: Boolean = true,
    val isStale: Boolean = false,
    val error: InventoryError? = null,
) {
    val entries: List<InventoryEntry>
        get() = snapshot?.filteredEntries(filters, hiddenPackages).orEmpty()

    val hiddenCount: Int
        get() = snapshot?.entries?.count { entry ->
            val packageName = entry.app?.packageName
            packageName != null && packageName in hiddenPackages
        } ?: 0
}

/**
 * UI state for the local inventory. Manual checks work in every profile;
 * [InventoryUiState.canFullScan] is false on Play and controls the scan action.
 */
class InventoryViewModel(
    private val repository: InventoryRepository,
    initialQuery: String? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        InventoryUiState(canFullScan = repository.canFullScan),
    )
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        observeState()
        val shared = initialQuery?.trim().orEmpty()
        if (shared.isNotEmpty()) {
            _uiState.update { it.copy(query = shared) }
            checkInput()
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    /** Applies a shared store link or package identity received from another app. */
    fun submitSharedInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(query = trimmed, candidates = emptyList()) }
        checkInput()
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            try {
                val results = repository.search(query)
                _uiState.update { state ->
                    state.copy(
                        candidates = results,
                        isSearching = false,
                        error = state.error.takeUnless {
                            it == InventoryError.SEARCH_FAILED
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Inventory search failed", throwable)
                _uiState.update {
                    it.copy(isSearching = false, error = InventoryError.SEARCH_FAILED)
                }
            }
        }
    }

    /**
     * Package names and store links are checked directly. Free text is searched
     * first: one match is checked, several become a picker, none is recorded as
     * UNAVAILABLE so the user sees why nothing was found.
     */
    fun checkInput() {
        val input = _uiState.value.query.trim()
        if (input.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null) }
            try {
                val identity = PackageIdentityParser.extract(input)
                if (identity != null) {
                    repository.checkPackage(identity)
                    _uiState.update { it.copy(candidates = emptyList()) }
                } else {
                    val results = repository.search(input)
                    when {
                        results.isEmpty() -> {
                            repository.recordUnavailable(input)
                            _uiState.update { it.copy(candidates = emptyList()) }
                        }

                        results.size == 1 -> {
                            repository.checkPackage(results.single().packageName)
                            _uiState.update { it.copy(candidates = emptyList()) }
                        }

                        else -> _uiState.update { it.copy(candidates = results) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Inventory check failed", throwable)
                _uiState.update { it.copy(error = InventoryError.CHECK_FAILED) }
            } finally {
                _uiState.update { it.copy(isChecking = false) }
            }
        }
    }

    fun selectCandidate(app: InstalledApp) {
        _uiState.update { it.copy(query = app.packageName, candidates = emptyList()) }
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null) }
            try {
                repository.checkPackage(app.packageName)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Inventory check failed", throwable)
                _uiState.update { it.copy(error = InventoryError.CHECK_FAILED) }
            } finally {
                _uiState.update { it.copy(isChecking = false) }
            }
        }
    }

    fun startFullScan() {
        if (!_uiState.value.canFullScan || _uiState.value.isScanning) return
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                repository.scanFull()
            } catch (cancellation: CancellationException) {
                // Cancellation keeps the previous complete snapshot.
            } catch (throwable: Throwable) {
                Log.e(TAG, "Full inventory scan failed", throwable)
                _uiState.update { it.copy(error = InventoryError.SCAN_FAILED) }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
                scanJob = null
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun setIncludeSystemApps(value: Boolean) = updateFilters {
        it.copy(includeSystemApps = value)
    }

    fun setIncludeDisabledApps(value: Boolean) = updateFilters {
        it.copy(includeDisabledApps = value)
    }

    fun setIncludeWorkProfileApps(value: Boolean) = updateFilters {
        it.copy(includeWorkProfileApps = value)
    }

    fun setIncludeHiddenApps(value: Boolean) = updateFilters {
        it.copy(includeHiddenApps = value)
    }

    fun hide(packageName: String) = updateHidden { repository.hide(packageName) }

    fun unhide(packageName: String) = updateHidden { repository.unhide(packageName) }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun observeState() {
        viewModelScope.launch {
            repository.state
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read inventory", throwable)
                    _uiState.update {
                        it.copy(isLoading = false, error = InventoryError.READ_FAILED)
                    }
                }
                .collect { state ->
                    _uiState.update {
                        it.copy(
                            snapshot = state.snapshot,
                            hiddenPackages = state.hiddenPackages,
                            isLoading = false,
                            isStale = state.snapshot?.let(repository::isStale) == true,
                            error = it.error?.takeUnless { error ->
                                error == InventoryError.READ_FAILED
                            },
                        )
                    }
                }
        }
    }

    private fun updateFilters(transform: (InventoryFilters) -> InventoryFilters) {
        _uiState.update { it.copy(filters = transform(it.filters)) }
    }

    private fun updateHidden(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _uiState.update { it.copy(error = it.error.takeUnless { e ->
                    e == InventoryError.UPDATE_FAILED
                }) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to update hidden apps", throwable)
                _uiState.update { it.copy(error = InventoryError.UPDATE_FAILED) }
            }
        }
    }

    private companion object {
        const val TAG = "InventoryViewModel"
    }
}
