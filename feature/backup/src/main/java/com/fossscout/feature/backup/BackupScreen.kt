package com.fossscout.feature.backup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.fossscout.core.backup.BACKUP_PASSPHRASE_MIN_LENGTH
import com.fossscout.core.backup.BackupConflictMode
import com.fossscout.core.backup.BackupExclusion
import com.fossscout.core.backup.BackupManifest
import com.fossscout.core.backup.BackupRestorePlan
import com.fossscout.core.backup.BackupSection

@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onToggleEncryption: (Boolean) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onConfirmPassphraseChange: (String) -> Unit,
    onExport: () -> Unit,
    onChooseFile: () -> Unit,
    onImportPassphraseChange: (String) -> Unit,
    onUnlockImport: () -> Unit,
    onModeChange: (BackupConflictMode) -> Unit,
    onRestore: () -> Unit,
    onDismissImport: () -> Unit,
    onDismissError: () -> Unit,
    onAcknowledgeRestore: () -> Unit,
    onRetryExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 12.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack, enabled = !state.isApplying) {
                    Text(stringResource(R.string.backup_back))
                }
                Spacer(Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.backup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { error ->
            item {
                ErrorCard(
                    message = errorMessage(error),
                    onDismiss = onDismissError,
                )
            }
        }

        if (state.restoreCompleted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.backup_restore_done),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAcknowledgeRestore) {
                            Text(stringResource(R.string.backup_dismiss))
                        }
                    }
                }
            }
        }

        item {
            ExportCard(
                state = state,
                onToggleEncryption = onToggleEncryption,
                onPassphraseChange = onPassphraseChange,
                onConfirmPassphraseChange = onConfirmPassphraseChange,
                onExport = onExport,
                onRetry = onRetryExport,
            )
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_auto_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.backup_auto_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ImportCard(
                state = state,
                onChooseFile = onChooseFile,
                onImportPassphraseChange = onImportPassphraseChange,
                onUnlockImport = onUnlockImport,
            )
        }

        state.importPreview?.let { plan ->
            item {
                RestorePreviewCard(
                    plan = plan,
                    fileName = state.importFileName,
                    mode = state.importMode,
                    isApplying = state.isApplying,
                    onModeChange = onModeChange,
                    onRestore = onRestore,
                    onDismiss = onDismissImport,
                )
            }
        }
    }
}

@Composable
private fun ExportCard(
    state: BackupUiState,
    onToggleEncryption: (Boolean) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onConfirmPassphraseChange: (String) -> Unit,
    onExport: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_export_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.backup_export_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                state.isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp))
                        Text(stringResource(R.string.backup_loading))
                    }
                }

                state.exportDraft == null -> {
                    Text(stringResource(R.string.backup_error_read))
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.backup_retry))
                    }
                }

                else -> {
                    ManifestSection(manifest = state.exportDraft.manifest)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backup_encrypt),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.backup_encrypt_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.useEncryption,
                            onCheckedChange = onToggleEncryption,
                        )
                    }

                    if (state.useEncryption) {
                        PasswordField(
                            value = state.passphrase,
                            onValueChange = onPassphraseChange,
                            label = stringResource(R.string.backup_passphrase),
                            hint = stringResource(
                                R.string.backup_passphrase_hint,
                                BACKUP_PASSPHRASE_MIN_LENGTH,
                            ),
                        )
                        PasswordField(
                            value = state.confirmPassphrase,
                            onValueChange = onConfirmPassphraseChange,
                            label = stringResource(R.string.backup_passphrase_confirm),
                        )
                    }

                    Button(
                        onClick = onExport,
                        enabled = !state.isExporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.isExporting) {
                                stringResource(R.string.backup_export_working)
                            } else {
                                stringResource(R.string.backup_export_action)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportCard(
    state: BackupUiState,
    onChooseFile: () -> Unit,
    onImportPassphraseChange: (String) -> Unit,
    onUnlockImport: () -> Unit,
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_import_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.backup_import_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onChooseFile,
                enabled = !state.isApplying && !state.isPreviewing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.backup_choose_file))
            }

            if (state.importNeedsPassphrase) {
                Text(
                    text = stringResource(R.string.backup_unlock_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.backup_unlock_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PasswordField(
                    value = state.importPassphrase,
                    onValueChange = onImportPassphraseChange,
                    label = stringResource(R.string.backup_passphrase),
                )
                Button(
                    onClick = onUnlockImport,
                    enabled = !state.isPreviewing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.backup_unlock_action))
                }
            }

            if (state.isPreviewing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    Text(stringResource(R.string.backup_preview_working))
                }
            }
        }
    }
}

@Composable
private fun RestorePreviewCard(
    plan: BackupRestorePlan,
    fileName: String?,
    mode: BackupConflictMode,
    isApplying: Boolean,
    onModeChange: (BackupConflictMode) -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = plan.preview
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.backup_preview_title),
                style = MaterialTheme.typography.titleLarge,
            )
            fileName?.let {
                Text(
                    text = stringResource(R.string.backup_preview_file, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(
                    R.string.backup_preview_summary,
                    preview.addedFavouriteCount,
                    preview.mergedFavouriteCount,
                    preview.replacedFavouriteCount,
                    preview.skippedFavouriteCount,
                    preview.addedCollectionCount,
                    preview.replacedCollectionCount,
                    preview.addedStoreCount,
                    preview.replacedStoreCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (preview.notesItemCount > 0) {
                Text(
                    text = stringResource(
                        R.string.backup_preview_notes,
                        preview.notesItemCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (preview.removedFavouriteCount > 0 ||
                preview.removedCollectionCount > 0 ||
                preview.removedStoreCount > 0
            ) {
                Text(
                    text = stringResource(
                        R.string.backup_preview_removed,
                        preview.removedFavouriteCount,
                        preview.removedCollectionCount,
                        preview.removedStoreCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (preview.hasConflicts) {
                Text(
                    text = stringResource(
                        R.string.backup_preview_conflicts,
                        preview.conflictFavouriteCount,
                        preview.stores.count { it.action != com.fossscout.core.backup.BackupChangeAction.ADD },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (preview.settings.changed || preview.handoff.changed) {
                Text(
                    text = stringResource(R.string.backup_preview_settings_changed),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.backup_preview_manifest),
                style = MaterialTheme.typography.titleSmall,
            )
            ManifestList(manifest = preview.manifest)

            Text(
                text = stringResource(R.string.backup_mode),
                style = MaterialTheme.typography.titleSmall,
            )
            ModeRow(
                mode = BackupConflictMode.MERGE,
                selected = mode == BackupConflictMode.MERGE,
                label = stringResource(R.string.backup_mode_merge),
                enabled = !isApplying,
                onSelect = onModeChange,
            )
            ModeRow(
                mode = BackupConflictMode.REPLACE,
                selected = mode == BackupConflictMode.REPLACE,
                label = stringResource(R.string.backup_mode_replace),
                enabled = !isApplying,
                onSelect = onModeChange,
            )
            ModeRow(
                mode = BackupConflictMode.SKIP,
                selected = mode == BackupConflictMode.SKIP,
                label = stringResource(R.string.backup_mode_skip),
                enabled = !isApplying,
                onSelect = onModeChange,
            )
            Text(
                text = when (mode) {
                    BackupConflictMode.MERGE ->
                        stringResource(R.string.backup_mode_merge_desc)

                    BackupConflictMode.REPLACE ->
                        stringResource(R.string.backup_mode_replace_desc)

                    BackupConflictMode.SKIP ->
                        stringResource(R.string.backup_mode_skip_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isApplying,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.backup_dismiss_preview))
                }
                Button(
                    onClick = onRestore,
                    enabled = !isApplying,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (isApplying) {
                            stringResource(R.string.backup_restore_working)
                        } else {
                            stringResource(R.string.backup_restore_action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: BackupConflictMode,
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onSelect: (BackupConflictMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onSelect(mode) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { onSelect(mode) },
            enabled = enabled,
        )
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ManifestSection(manifest: BackupManifest) {
    Text(
        text = stringResource(R.string.backup_manifest_included_title),
        style = MaterialTheme.typography.titleSmall,
    )
    ManifestList(manifest)
}

@Composable
private fun ManifestList(manifest: BackupManifest) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        manifest.included.forEach { section ->
            Text(
                text = "• ${includedLabel(section)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Text(
        text = stringResource(R.string.backup_manifest_excluded_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        manifest.excluded.forEach { exclusion ->
            Text(
                text = "• ${excludedLabel(exclusion)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.backup_dismiss))
            }
        }
    }
}

@Composable
private fun errorMessage(error: BackupError): String = when (error) {
    BackupError.READ_FAILED -> stringResource(R.string.backup_error_read)
    BackupError.EXPORT_FAILED -> stringResource(R.string.backup_error_export)
    BackupError.IMPORT_MALFORMED -> stringResource(R.string.backup_error_malformed)
    BackupError.IMPORT_UNSUPPORTED_VERSION -> stringResource(R.string.backup_error_unsupported)
    BackupError.IMPORT_UNSUPPORTED_FORMAT -> stringResource(R.string.backup_error_unsupported)
    BackupError.IMPORT_CHECKSUM_MISMATCH -> stringResource(R.string.backup_error_checksum)
    BackupError.IMPORT_WRONG_PASSPHRASE -> stringResource(R.string.backup_error_wrong_passphrase)
    BackupError.IMPORT_PASSPHRASE_REQUIRED ->
        stringResource(R.string.backup_error_passphrase_required)

    BackupError.IMPORT_READ_FAILED -> stringResource(R.string.backup_error_import_read)
    BackupError.IMPORT_APPLY_FAILED -> stringResource(R.string.backup_error_apply)
    BackupError.PASSPHRASE_TOO_SHORT -> stringResource(
        R.string.backup_error_passphrase_short,
        BACKUP_PASSPHRASE_MIN_LENGTH,
    )

    BackupError.PASSPHRASE_MISMATCH -> stringResource(R.string.backup_error_passphrase_mismatch)
}

@Composable
private fun includedLabel(section: BackupSection): String = when (section) {
    BackupSection.FAVOURITES -> stringResource(R.string.backup_included_favourites)
    BackupSection.NOTES -> stringResource(R.string.backup_included_notes)
    BackupSection.COLLECTIONS -> stringResource(R.string.backup_included_collections)
    BackupSection.CUSTOM_STORES -> stringResource(R.string.backup_included_stores)
    BackupSection.FEATURE_SETTINGS -> stringResource(R.string.backup_included_settings)
    BackupSection.PREFERRED_STORE_CLIENTS -> stringResource(R.string.backup_included_handoff)
}

@Composable
private fun excludedLabel(exclusion: BackupExclusion): String = when (exclusion) {
    BackupExclusion.INDEX_SNAPSHOTS -> stringResource(R.string.backup_excluded_index)
    BackupExclusion.APK_FILES -> stringResource(R.string.backup_excluded_apk)
    BackupExclusion.INSTALLED_APP_INVENTORY -> stringResource(R.string.backup_excluded_inventory)
    BackupExclusion.MATCH_OVERRIDES -> stringResource(R.string.backup_excluded_overrides)
    BackupExclusion.SECRETS -> stringResource(R.string.backup_excluded_secrets)
}
