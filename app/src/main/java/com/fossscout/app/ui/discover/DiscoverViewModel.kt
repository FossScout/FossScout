package com.fossscout.app.ui.discover

import androidx.lifecycle.ViewModel
import com.fossscout.app.data.Project
import com.fossscout.app.data.ProjectCategory
import com.fossscout.app.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class DiscoverViewModel : ViewModel() {
    private val repository = ProjectRepository()

    private val _uiState = MutableStateFlow(
        DiscoverUiState(projects = repository.getAll()),
    )
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onCategorySelected(category: ProjectCategory?) {
        _uiState.update { it.copy(category = category) }
    }

    fun onToggleSaved(projectId: String) {
        _uiState.update { state ->
            val savedIds = if (projectId in state.savedIds) {
                state.savedIds - projectId
            } else {
                state.savedIds + projectId
            }
            state.copy(savedIds = savedIds)
        }
    }

    fun clearFilters() {
        _uiState.update { it.copy(query = "", category = null) }
    }
}
