package com.fossscout.feature.favourites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fossscout.core.model.MAX_NOTE_LENGTH
import com.fossscout.core.model.MAX_TAG_LENGTH
import com.fossscout.core.model.MAX_TAGS
import com.fossscout.core.model.StoreId

@Composable
fun FavouriteNoteDialog(
    editor: FavouriteEditor,
    storeOptions: List<FavouriteStoreOption>,
    onNoteChange: (String) -> Unit,
    onTagDraftChange: (String) -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onPreferredStoreSelected: (StoreId?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overLimit = editor.draft.length > MAX_NOTE_LENGTH
    var storeMenuExpanded by remember { mutableStateOf(false) }
    val selectedStoreName = storeOptions
        .firstOrNull { it.id == editor.preferredStoreId }
        ?.name

    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) onDismiss()
        },
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.note_dialog_title, editor.displayName))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = editor.draft,
                    onValueChange = onNoteChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.isSaving,
                    isError = overLimit || editor.saveFailed,
                    label = { Text(stringResource(R.string.note_dialog_label)) },
                    minLines = 4,
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.note_dialog_counter,
                                editor.draft.length,
                                MAX_NOTE_LENGTH,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
                if (overLimit) {
                    Text(
                        text = stringResource(R.string.note_dialog_too_long, MAX_NOTE_LENGTH),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (editor.saveFailed) {
                    Text(
                        text = stringResource(R.string.note_dialog_save_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.note_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Text(
                    text = stringResource(R.string.favourite_tags_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                if (editor.tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favourite_tags_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = editor.tags, key = { it }) { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveTag(tag) },
                                enabled = !editor.isSaving,
                                label = { Text(text = "#$tag") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(
                                            R.string.favourite_tag_remove,
                                            tag,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = editor.tagDraft,
                    onValueChange = onTagDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    enabled = !editor.isSaving,
                    singleLine = true,
                    isError = editor.tagError != null,
                    label = { Text(stringResource(R.string.favourite_tag_add_label)) },
                    supportingText = {
                        when (editor.tagError) {
                            FavouriteTagError.TOO_LONG -> Text(
                                stringResource(
                                    R.string.favourite_tag_too_long,
                                    MAX_TAG_LENGTH,
                                ),
                            )

                            FavouriteTagError.TOO_MANY -> Text(
                                stringResource(R.string.favourite_tag_too_many, MAX_TAGS),
                            )

                            null -> Text(
                                stringResource(
                                    R.string.favourite_tag_hint,
                                    MAX_TAGS,
                                    MAX_TAG_LENGTH,
                                ),
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = onAddTag,
                            enabled = !editor.isSaving,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.favourite_tag_add),
                            )
                        }
                    },
                )

                Text(
                    text = stringResource(R.string.favourite_store_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                Box {
                    OutlinedButton(
                        onClick = { storeMenuExpanded = true },
                        enabled = !editor.isSaving && storeOptions.isNotEmpty(),
                    ) {
                        Text(
                            text = selectedStoreName
                                ?: stringResource(R.string.favourite_store_none),
                        )
                    }
                    DropdownMenu(
                        expanded = storeMenuExpanded,
                        onDismissRequest = { storeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.favourite_store_none)) },
                            onClick = {
                                onPreferredStoreSelected(null)
                                storeMenuExpanded = false
                            },
                        )
                        storeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.name) },
                                onClick = {
                                    onPreferredStoreSelected(option.id)
                                    storeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (storeOptions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.favourite_store_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !editor.isSaving && !overLimit,
            ) {
                Text(
                    text = if (editor.isSaving) {
                        stringResource(R.string.note_dialog_saving)
                    } else {
                        stringResource(R.string.note_dialog_save)
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !editor.isSaving,
            ) {
                Text(stringResource(R.string.note_dialog_cancel))
            }
        },
    )
}
