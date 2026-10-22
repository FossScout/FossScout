package com.fossscout.feature.catalog.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.model.AppKey
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectCategory
import com.fossscout.feature.catalog.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val projects: List<Project> = emptyList(),
    val query: String = "",
    val category: ProjectCategory? = null,
    val savedIds: Set<String> = emptySet(),
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

    init {
        viewModelScope.launch {
            favouritesRepository.favourites.collect { favourites ->
                _uiState.update { state ->
                    state.copy(savedIds = favourites.map { it.appKey.value }.toSet())
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onCategorySelected(category: ProjectCategory?) {
        _uiState.update { it.copy(category = category) }
    }

    fun onToggleSaved(project: Project) {
        val appKey = AppKey(project.id)
        val isSaved = project.id in _uiState.value.savedIds
        viewModelScope.launch {
            if (isSaved) {
                favouritesRepository.remove(appKey)
            } else {
                favouritesRepository.add(appKey, project.name)
            }
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(query = "", category = null) }
    }
}
