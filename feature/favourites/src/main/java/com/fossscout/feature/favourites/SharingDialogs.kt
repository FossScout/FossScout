package com.fossscout.feature.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.data.ImportCollectionAction
import com.fossscout.core.data.ImportConflictMode
import com.fossscout.core.data.ImportPreview

@Composable
fun ShareExportDialog(
    draft: ShareDraft,
    onIncludeNotesChange: (Boolean) -> Unit,
    onShareJson: () -> Unit,
    onShareText: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.sharing_share_title, draft.envelope.name))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.sharing_share_item_count,
                        draft.envelope.items.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sharing_include_notes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.sharing_include_notes_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.includeNotes,
                        onCheckedChange = onIncludeNotesChange,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onShareText) {
                    Text(stringResource(R.string.sharing_share_text))
                }
                TextButton(onClick = onShareJson) {
                    Text(stringResource(R.string.sharing_share_json_file))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sharing_cancel))
            }
        },
    )
}

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    keepImportedNotes: Boolean,
    isApplying: Boolean,
    onModeChange: (ImportConflictMode) -> Unit,
    onKeepNotesChange: (Boolean) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isApplying) onDismiss()
        },
        title = {
            Text(stringResource(R.string.sharing_import_title, preview.envelope.name))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(
                        R.string.sharing_import_summary,
                        preview.items.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = preview.mode == ImportConflictMode.MERGE,
                        onClick = { onModeChange(ImportConflictMode.MERGE) },
                        label = { Text(stringResource(R.string.sharing_mode_merge)) },
                    )
                    FilterChip(
                        selected = preview.mode == ImportConflictMode.REPLACE,
                        onClick = { onModeChange(ImportConflictMode.REPLACE) },
                        label = { Text(stringResource(R.string.sharing_mode_replace)) },
                    )
                    FilterChip(
                        selected = preview.mode == ImportConflictMode.SKIP,
                        onClick = { onModeChange(ImportConflictMode.SKIP) },
                        label = { Text(stringResource(R.string.sharing_mode_skip)) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.sharing_import_new,
                        preview.addedFavouriteCount,
                    ) + "  " + stringResource(
                        R.string.sharing_import_conflicts,
                        preview.conflictCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview.removedFavouriteCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.sharing_import_removed,
                            preview.removedFavouriteCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                collectionActionMessage(preview.collectionAction)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (preview.unknownStores.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.sharing_import_unknown_stores,
                            preview.unknownStores.joinToString { it.value },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (preview.hasNotes) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sharing_import_notes_switch),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(
                                    R.string.sharing_import_notes_hint,
                                    preview.notesItemCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = keepImportedNotes,
                            onCheckedChange = onKeepNotesChange,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                preview.items.take(8).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.unknownStore) {
                            Text(
                                text = stringResource(
                                    R.string.sharing_import_unknown_store_short,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (preview.items.size > 8) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onApply,
                enabled = !isApplying && preview.items.isNotEmpty(),
            ) {
                Text(
                    stringResource(
                        if (isApplying) {
                            R.string.sharing_importing
                        } else {
                            R.string.sharing_import_apply
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isApplying,
            ) {
                Text(stringResource(R.string.sharing_import_cancel))
            }
        },
    )
}

@Composable
fun SharingErrorDialog(
    error: SharingError,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_title)) },
        text = {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sharing_dismiss))
            }
        },
    )
}

@Composable
fun ImportCompleteDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sharing_import)) },
        text = {
            Text(
                text = stringResource(R.string.sharing_import_done),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sharing_dismiss))
            }
        },
    )
}

@Composable
private fun collectionActionMessage(action: ImportCollectionAction): String? = when (action) {
    ImportCollectionAction.NONE -> null
    ImportCollectionAction.CREATE ->
        stringResource(R.string.sharing_import_collection_new)
    ImportCollectionAction.MERGE ->
        stringResource(R.string.sharing_import_collection_merge)
    ImportCollectionAction.REPLACE ->
        stringResource(R.string.sharing_import_collection_replace)
    ImportCollectionAction.SKIP ->
        stringResource(R.string.sharing_import_collection_skip)
}

private fun SharingError.messageRes(): Int = when (this) {
    SharingError.NOTHING_TO_SHARE -> R.string.sharing_error_nothing
    SharingError.COLLECTION_MISSING -> R.string.sharing_error_collection_missing
    SharingError.IMPORT_MALFORMED -> R.string.sharing_error_malformed
    SharingError.IMPORT_UNSUPPORTED_VERSION -> R.string.sharing_error_unsupported
    SharingError.IMPORT_READ_FAILED -> R.string.sharing_error_read
    SharingError.IMPORT_APPLY_FAILED -> R.string.sharing_error_apply
}
