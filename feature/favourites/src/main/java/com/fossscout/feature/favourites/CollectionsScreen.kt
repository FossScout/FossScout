package com.fossscout.feature.favourites

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite

@Composable
fun CollectionsScreen(
    state: CollectionsUiState,
    showSharing: Boolean,
    onBack: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onCloseCollection: () -> Unit,
    onStartCreate: () -> Unit,
    onStartEdit: (AppCollection) -> Unit,
    onEditorNameChange: (String) -> Unit,
    onEditorDescriptionChange: (String) -> Unit,
    onSaveEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onRequestDelete: (AppCollection) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onMoveItem: (String, AppKey, Int) -> Unit,
    onRemoveItem: (String, AppKey) -> Unit,
    onOpenPicker: () -> Unit,
    onDismissPicker: () -> Unit,
    onAddItem: (String, AppKey) -> Unit,
    onShareCollection: (String) -> Unit,
    onShareAll: () -> Unit,
    onImport: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selectedCollection
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (selected == null) {
            CollectionListHeader(
                onBack = onBack,
                onCreate = onStartCreate,
                showSharing = showSharing,
                onShareAll = onShareAll,
                onImport = onImport,
            )
        } else {
            CollectionDetailHeader(
                collection = selected,
                onBack = onCloseCollection,
                onEdit = { onStartEdit(selected) },
                onShare = { onShareCollection(selected.id) },
                showSharing = showSharing,
            )
        }

        val error = state.error
        if (error != null && error != CollectionsError.READ_FAILED) {
            CollectionErrorBanner(
                message = stringResource(error.messageRes()),
                onDismiss = onDismissError,
            )
        }

        when {
            state.isLoading -> CollectionLoading(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            error == CollectionsError.READ_FAILED -> CollectionReadError(
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            selected != null -> CollectionDetail(
                state = state,
                collection = selected,
                onMoveItem = onMoveItem,
                onRemoveItem = onRemoveItem,
                onOpenPicker = onOpenPicker,
                onRequestDelete = onRequestDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            state.collections.isEmpty() -> EmptyCollections(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = state.collections, key = AppCollection::id) { collection ->
                    CollectionCard(
                        collection = collection,
                        onOpen = { onOpenCollection(collection.id) },
                        onEdit = { onStartEdit(collection) },
                        onShare = { onShareCollection(collection.id) },
                        onDelete = { onRequestDelete(collection) },
                        showSharing = showSharing,
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        CollectionEditorDialog(
            editor = editor,
            onNameChange = onEditorNameChange,
            onDescriptionChange = onEditorDescriptionChange,
            onSave = onSaveEditor,
            onDismiss = onDismissEditor,
        )
    }

    state.deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.collections_delete_title, request.name)) },
            text = { Text(stringResource(R.string.collections_delete_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.collections_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.collections_cancel))
                }
            },
        )
    }

    state.picker?.let { picker ->
        CollectionPickerDialog(
            picker = picker,
            favourites = state.availableFavourites,
            onAdd = onAddItem,
            onDismiss = onDismissPicker,
        )
    }
}

@Composable
private fun CollectionListHeader(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    showSharing: Boolean,
    onShareAll: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.collections_back),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.collections_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.collections_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCreate) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.collections_new))
            }
            if (showSharing) {
                TextButton(onClick = onShareAll) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.sharing_share_all))
                }
                TextButton(onClick = onImport) {
                    Text(stringResource(R.string.sharing_import))
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailHeader(
    collection: AppCollection,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    showSharing: Boolean,
) {
    Row(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collections_back),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showSharing) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(
                        R.string.collections_share,
                        collection.name,
                    ),
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.collections_edit, collection.name),
            )
        }
    }
}

@Composable
private fun CollectionCard(
    collection: AppCollection,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    showSharing: Boolean,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.collections_item_count,
                            collection.itemKeys.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSharing) {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(
                                R.string.collections_share,
                                collection.name,
                            ),
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            R.string.collections_edit,
                            collection.name,
                        ),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.collections_delete,
                            collection.name,
                        ),
                    )
                }
            }
            if (collection.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = collection.description,
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
private fun CollectionDetail(
    state: CollectionsUiState,
    collection: AppCollection,
    onMoveItem: (String, AppKey, Int) -> Unit,
    onRemoveItem: (String, AppKey) -> Unit,
    onOpenPicker: () -> Unit,
    onRequestDelete: (AppCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.collections_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (collection.itemKeys.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.collections_empty_items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            itemsIndexed(
                items = collection.itemKeys,
                key = { _, key -> key.value },
            ) { index, key ->
                CollectionItemRow(
                    name = state.favouriteName(key),
                    appKey = key,
                    canMoveUp = index > 0,
                    canMoveDown = index < collection.itemKeys.lastIndex,
                    onMoveUp = { onMoveItem(collection.id, key, -1) },
                    onMoveDown = { onMoveItem(collection.id, key, 1) },
                    onRemove = { onRemoveItem(collection.id, key) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onOpenPicker) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.collections_add_item))
                }
                TextButton(onClick = { onRequestDelete(collection) }) {
                    Text(stringResource(R.string.collections_delete_confirm))
                }
            }
        }
    }
}

@Composable
private fun CollectionItemRow(
    name: String,
    appKey: AppKey,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = appKey.value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.collections_move_up, name),
                )
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.collections_move_down, name),
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.collections_remove_item, name),
                )
            }
        }
    }
}

@Composable
private fun CollectionEditorDialog(
    editor: CollectionEditor,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) onDismiss()
        },
        title = {
            Text(
                stringResource(
                    if (editor.isEditing) {
                        R.string.collections_editor_edit_title
                    } else {
                        R.string.collections_editor_create_title
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.isSaving,
                    singleLine = true,
                    isError = editor.saveFailed,
                    label = { Text(stringResource(R.string.collections_name_label)) },
                )
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    enabled = !editor.isSaving,
                    minLines = 2,
                    label = { Text(stringResource(R.string.collections_description_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = editor.canSave,
            ) {
                Text(
                    stringResource(
                        if (editor.isSaving) {
                            R.string.collections_saving
                        } else {
                            R.string.collections_save
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Composable
private fun CollectionPickerDialog(
    picker: CollectionPicker,
    favourites: List<Favourite>,
    onAdd: (String, AppKey) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collections_picker_title, picker.collectionName)) },
        text = {
            if (favourites.isEmpty()) {
                Text(stringResource(R.string.collections_picker_empty))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = favourites, key = { it.appKey.value }) { favourite ->
                        TextButton(
                            onClick = { onAdd(picker.collectionId, favourite.appKey) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start,
                            ) {
                                Text(
                                    text = favourite.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = favourite.appKey.value,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Composable
private fun EmptyCollections(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.collections_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.collections_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CollectionLoading(modifier: Modifier = Modifier) {
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
private fun CollectionReadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.collections_error_read),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.collections_retry))
        }
    }
}

@Composable
private fun CollectionErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
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
                Text(stringResource(R.string.collections_dismiss))
            }
        }
    }
}

private fun CollectionsError.messageRes(): Int = when (this) {
    CollectionsError.READ_FAILED -> R.string.collections_error_read
    CollectionsError.SAVE_FAILED -> R.string.collections_error_save
    CollectionsError.DELETE_FAILED -> R.string.collections_error_delete
}
