package com.fossscout.feature.favourites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fossscout.core.model.MAX_NOTE_LENGTH

@Composable
fun FavouriteNoteDialog(
    editor: FavouriteEditor,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overLimit = editor.draft.length > MAX_NOTE_LENGTH

    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) onDismiss()
        },
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.note_dialog_title, editor.displayName))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
