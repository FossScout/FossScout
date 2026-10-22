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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MAX_NOTE_LENGTH

@Composable
fun FavouriteNoteDialog(
    favourite: Favourite,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var note by rememberSaveable(favourite.appKey.value) {
        mutableStateOf(favourite.note.take(MAX_NOTE_LENGTH))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(text = stringResource(R.string.note_dialog_title, favourite.displayName))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { updated ->
                        if (updated.length <= MAX_NOTE_LENGTH) {
                            note = updated
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.note_dialog_label)) },
                    minLines = 4,
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.note_dialog_counter,
                                note.length,
                                MAX_NOTE_LENGTH,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
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
                onClick = {
                    onSave(normalizeNote(note))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.note_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.note_dialog_cancel))
            }
        },
    )
}
