package com.fossscout.feature.favourites

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.InstalledState
import com.fossscout.core.model.StoreId

@Composable
fun FavouritesScreen(
    state: FavouritesUiState,
    onRemove: (AppKey) -> Unit,
    onEditNote: (Favourite) -> Unit,
    onNoteDraftChange: (String) -> Unit,
    onTagDraftChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onPreferredStoreSelected: (StoreId?) -> Unit,
    onSaveNote: () -> Unit,
    onDismissEditor: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onRefreshInstalled: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    showSharing: Boolean = false,
    onOpenCollections: () -> Unit = {},
    onShareAll: () -> Unit = {},
    onImport: () -> Unit = {},
    onShareFavourite: (AppKey) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        onRefreshInstalled()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.favourites_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.favourites_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenCollections) {
                    Text(stringResource(R.string.collections_title))
                }
                if (showSharing) {
                    TextButton(onClick = onShareAll) {
                        Text(stringResource(R.string.sharing_share_all))
                    }
                    TextButton(onClick = onImport) {
                        Text(stringResource(R.string.sharing_import))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.favourites_search_placeholder)) },
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
                                contentDescription = stringResource(
                                    R.string.favourites_clear_search,
                                ),
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

        if (state.tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedTag == null,
                        onClick = { onTagSelected(null) },
                        label = { Text(stringResource(R.string.favourites_filter_all)) },
                    )
                }
                items(items = state.tags, key = { it }) { tag ->
                    FilterChip(
                        selected = state.selectedTag == tag,
                        onClick = {
                            onTagSelected(if (state.selectedTag == tag) null else tag)
                        },
                        label = { Text(text = "#$tag") },
                    )
                }
            }
        }

        val error = state.error
        if (error != null && error != FavouritesError.READ_FAILED) {
            FavouritesErrorBanner(
                message = stringResource(error.messageRes()),
                onDismiss = onDismissError,
            )
        }

        when {
            state.isLoading -> FavouritesLoading(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            error == FavouritesError.READ_FAILED -> FavouritesReadError(
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            state.favourites.isEmpty() -> EmptyFavourites(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            state.visibleFavourites.isEmpty() -> EmptyFilteredFavourites(
                onClearFilters = onClearFilters,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.visibleFavourites,
                    key = { it.appKey.value },
                ) { favourite ->
                    FavouriteCard(
                        favourite = favourite,
                        installedState = state.installedStates[favourite.appKey],
                        preferredStoreName = favourite.preferredStoreId?.let { id ->
                            state.storeName(id)
                                ?: stringResource(R.string.favourite_store_unknown, id.value)
                        },
                        showSharing = showSharing,
                        onEditNote = { onEditNote(favourite) },
                        onShare = { onShareFavourite(favourite.appKey) },
                        onRemove = { onRemove(favourite.appKey) },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        FavouriteNoteDialog(
            editor = editor,
            storeOptions = state.storeOptions,
            onNoteChange = onNoteDraftChange,
            onTagDraftChange = onTagDraftChange,
            onAddTag = onAddTag,
            onRemoveTag = onRemoveTag,
            onPreferredStoreSelected = onPreferredStoreSelected,
            onSave = onSaveNote,
            onDismiss = onDismissEditor,
        )
    }
}

@StringRes
private fun FavouritesError.messageRes(): Int = when (this) {
    FavouritesError.READ_FAILED -> R.string.favourites_error_read
    FavouritesError.SAVE_FAILED -> R.string.favourites_error_save
    FavouritesError.REMOVE_FAILED -> R.string.favourites_error_remove
}

@Composable
private fun FavouritesErrorBanner(
    message: String,
    onDismiss: () -> Unit,
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
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.favourites_dismiss))
            }
        }
    }
}

@Composable
private fun FavouritesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favourites_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FavouritesReadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.favourites_error_read),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.favourites_error_read_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.favourites_retry))
        }
    }
}

@Composable
private fun FavouriteCard(
    favourite: Favourite,
    installedState: InstalledState?,
    preferredStoreName: String?,
    showSharing: Boolean,
    onEditNote: () -> Unit,
    onShare: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavouriteMonogram(name = favourite.displayName)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = favourite.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = favourite.appKey.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onEditNote) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            R.string.favourite_edit_note,
                            favourite.displayName,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSharing) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(
                                R.string.collections_share,
                                favourite.displayName,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.favourite_remove,
                            favourite.displayName,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (installedState != null) {
                Spacer(modifier = Modifier.height(10.dp))
                InstalledBadge(state = installedState)
            }

            if (favourite.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = favourite.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (preferredStoreName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.favourite_preferred_store,
                        preferredStoreName,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (favourite.note.isNotBlank()) {
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.favourite_note_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = favourite.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InstalledBadge(
    state: InstalledState,
    modifier: Modifier = Modifier,
) {
    val container = when (state) {
        InstalledState.INSTALLED -> MaterialTheme.colorScheme.primaryContainer
        InstalledState.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
        InstalledState.NOT_INSTALLED,
        InstalledState.UNKNOWN,
        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (state) {
        InstalledState.INSTALLED -> MaterialTheme.colorScheme.onPrimaryContainer
        InstalledState.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
        InstalledState.NOT_INSTALLED,
        InstalledState.UNKNOWN,
        -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = container,
        ) {
            Text(
                text = stringResource(state.badgeRes()),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(state.explanationRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@StringRes
private fun InstalledState.badgeRes(): Int = when (this) {
    InstalledState.INSTALLED -> R.string.favourite_installed_badge
    InstalledState.NOT_INSTALLED -> R.string.favourite_not_installed_badge
    InstalledState.UNAVAILABLE -> R.string.favourite_unavailable_badge
    InstalledState.UNKNOWN -> R.string.favourite_unknown_badge
}

@StringRes
private fun InstalledState.explanationRes(): Int = when (this) {
    InstalledState.INSTALLED -> R.string.favourite_installed_explanation
    InstalledState.NOT_INSTALLED -> R.string.favourite_not_installed_explanation
    InstalledState.UNAVAILABLE -> R.string.favourite_unavailable_explanation
    InstalledState.UNKNOWN -> R.string.favourite_unknown_explanation
}

@Composable
private fun FavouriteMonogram(name: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(46.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyFavourites(modifier: Modifier = Modifier) {
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
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favourites_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.favourites_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyFilteredFavourites(
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.favourites_empty_filtered_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.favourites_empty_filtered_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onClearFilters) {
            Text(stringResource(R.string.favourites_clear_filters))
        }
    }
}
