package com.fossscout.feature.catalog.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.model.AppKey
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectCategory
import com.fossscout.feature.catalog.data.ProjectRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverSavedError { READ_FAILED, WRITE_FAILED }

data class DiscoverUiState(
    val projects: List<Project> = emptyList(),
    val query: String = "",
    val category: ProjectCategory? = null,
    val savedIds: Set<String> = emptySet(),
    val isSavedStateLoading: Boolean = true,
    val savedStateError: DiscoverSavedError? = null,
) {
    fun visibleProjects(savedOnly: Boolean): List<Project> {
        val normalizedQuery = query.trim()
        return projects.filter { project ->
            val matchesTab = !savedOnly || project.id in savedIds
            val matchesCategory = category == null || project.category == category
            val matchesQuery = normalizedQuery.isEmpty() ||
                project.name.contains(normalizedQuery, ignoreCase = true) ||
                project.tagline.contains(normalizedQuery, ignoreCase = true) ||
                project.tags.any { it.contains(normalizedQuery, ignoreCase = true) }

            matchesTab && matchesCategory && matchesQuery
        }
    }
}

class DiscoverViewModel(
    private val favouritesRepository: FavouritesRepository,
    private val projectRepository: ProjectRepository = ProjectRepository(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DiscoverUiState(projects = projectRepository.getAll()),
    )
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private var savedStateJob: Job? = null

    init {
        collectSavedState()
    }

    fun retrySavedState() {
        collectSavedState()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onCategorySelected(category: ProjectCategory?) {
        _uiState.update { it.copy(category = category) }
    }

    fun onToggleSaved(project: Project) {
        val current = _uiState.value
        if (current.isSavedStateLoading || current.savedStateError == DiscoverSavedError.READ_FAILED) {
            return
        }
        viewModelScope.launch {
            try {
                val appKey = AppKey(project.id)
                val isSaved = project.id in _uiState.value.savedIds
                if (isSaved) {
                    favouritesRepository.remove(appKey)
                } else {
                    favouritesRepository.add(appKey, project.name)
                }
                _uiState.update { state ->
                    state.copy(
                        savedStateError = state.savedStateError
                            .takeUnless { it == DiscoverSavedError.WRITE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to toggle saved state for ${project.id}", throwable)
                _uiState.update { state ->
                    state.copy(savedStateError = DiscoverSavedError.WRITE_FAILED)
                }
            }
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(query = "", category = null) }
    }

    private fun collectSavedState() {
        savedStateJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isSavedStateLoading = true,
                savedStateError = state.savedStateError
                    .takeUnless { it == DiscoverSavedError.READ_FAILED },
            )
        }
        savedStateJob = viewModelScope.launch {
            favouritesRepository.favourites
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read favourites", throwable)
                    _uiState.update { state ->
                        state.copy(
                            savedIds = emptySet(),
                            isSavedStateLoading = false,
                            savedStateError = DiscoverSavedError.READ_FAILED,
                        )
                    }
                }
                .collect { favourites ->
                    _uiState.update { state ->
                        state.copy(
                            savedIds = favourites.map { it.appKey.value }.toSet(),
                            isSavedStateLoading = false,
                            savedStateError = state.savedStateError
                                .takeUnless { it == DiscoverSavedError.READ_FAILED },
                        )
                    }
                }
        }
    }

    private companion object {
        const val TAG = "DiscoverViewModel"
    }
}
