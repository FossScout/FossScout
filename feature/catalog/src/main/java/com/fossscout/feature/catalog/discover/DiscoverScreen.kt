package com.fossscout.feature.catalog.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fossscout.core.ui.theme.FossScoutTheme
import com.fossscout.feature.catalog.R
import com.fossscout.feature.catalog.components.ProjectCard
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectCategory
import com.fossscout.feature.catalog.data.SampleProjects

@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    savedOnly: Boolean,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (ProjectCategory?) -> Unit,
    onToggleSaved: (Project) -> Unit,
    onClearFilters: () -> Unit,
    onOpenProject: (Project) -> Unit,
    modifier: Modifier = Modifier,
    showSaveAction: Boolean = true,
    onRetrySavedState: () -> Unit = {},
) {
    val visibleProjects = remember(state, savedOnly) { state.visibleProjects(savedOnly) }
    val filtersActive = state.query.isNotBlank() || state.category != null
    val savedStateReady = !state.isSavedStateLoading &&
        state.savedStateError != DiscoverSavedError.READ_FAILED

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = stringResource(
                    if (savedOnly) R.string.discover_saved_title else R.string.discover_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(
                    if (savedOnly) {
                        R.string.discover_saved_subtitle
                    } else {
                        R.string.discover_subtitle
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.discover_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
            )
        }

        state.savedStateError?.let { error ->
            SavedStateBanner(
                error = error,
                onRetry = onRetrySavedState,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.category == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text(ProjectCategory.ALL.label) },
                )
            }
            items(
                items = ProjectCategory.entries.filter { it != ProjectCategory.ALL },
                key = { it.name },
            ) { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = {
                        onCategorySelected(if (state.category == category) null else category)
                    },
                    label = { Text(category.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.discover_project_count,
                    visibleProjects.size,
                    visibleProjects.size,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (filtersActive) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.discover_clear_filters))
                }
            }
        }

        if (visibleProjects.isEmpty()) {
            EmptyState(
                savedOnly = savedOnly,
                hasSavedProjects = savedStateReady && state.savedIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = visibleProjects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        isSaved = project.id in state.savedIds,
                        onToggleSaved = { onToggleSaved(project) },
                        onClick = { onOpenProject(project) },
                        showSaveAction = showSaveAction && savedStateReady,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedStateBanner(
    error: DiscoverSavedError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    when (error) {
                        DiscoverSavedError.READ_FAILED ->
                            R.string.discover_saved_state_read_error

                        DiscoverSavedError.WRITE_FAILED ->
                            R.string.discover_saved_state_write_error
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (error == DiscoverSavedError.READ_FAILED) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.discover_retry))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    savedOnly: Boolean,
    hasSavedProjects: Boolean,
    modifier: Modifier = Modifier,
) {
    val nothingSaved = savedOnly && !hasSavedProjects
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (nothingSaved) Icons.Filled.Favorite else Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(
                if (nothingSaved) {
                    R.string.discover_empty_nothing_saved_title
                } else {
                    R.string.discover_empty_no_results_title
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(
                when {
                    nothingSaved -> R.string.discover_empty_nothing_saved_body
                    savedOnly -> R.string.discover_empty_saved_no_match_body
                    else -> R.string.discover_empty_no_match_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DiscoverScreenPreview() {
    FossScoutTheme {
        DiscoverScreen(
            state = DiscoverUiState(
                projects = SampleProjects,
                isSavedStateLoading = false,
            ),
            savedOnly = false,
            onQueryChange = {},
            onCategorySelected = {},
            onToggleSaved = {},
            onClearFilters = {},
            onOpenProject = {},
        )
    }
}
