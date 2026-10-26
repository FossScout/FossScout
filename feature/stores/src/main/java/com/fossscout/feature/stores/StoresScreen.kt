package com.fossscout.feature.stores

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.StorageStatus
import com.fossscout.core.index.SyncFailureKind
import com.fossscout.core.index.TrustRecord
import com.fossscout.core.index.VerificationFailure
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.LinkOnlyReason
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreHealthStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StoresScreen(
    state: StoresUiState,
    onAdd: () -> Unit,
    onEdit: (StoreDescriptor) -> Unit,
    onEditorChange: (StoreEditor) -> Unit,
    onSaveEditor: () -> Unit,
    onDismissEditor: () -> Unit,
    onToggle: (StoreId, Boolean) -> Unit,
    onMoveUp: (StoreId) -> Unit,
    onMoveDown: (StoreId) -> Unit,
    onRequestDelete: (StoreDescriptor) -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onExport: (StoreId) -> Unit,
    onShareExport: (StoreExportRequest) -> Unit,
    onDismissExport: () -> Unit,
    onOpenHomepage: (String) -> Unit,
    onSync: (StoreId) -> Unit,
    onCancelSync: (StoreId) -> Unit,
    onOpenSecurityDetails: (StoreId) -> Unit,
    onDismissSecurityDetails: () -> Unit,
    onApproveFingerprint: (StoreId, String) -> Unit,
    onCleanupStorage: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onReportProblem: ((StoreEditor?) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stores_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stores_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onReportProblem != null) {
                TextButton(onClick = { onReportProblem(null) }) {
                    Text(stringResource(R.string.stores_report_problem))
                }
            }
            Button(onClick = onAdd) {
                Text(stringResource(R.string.stores_add))
            }
        }

        state.error?.let { error ->
            StoresErrorBanner(
                error = error,
                onRetry = onRetry,
                onDismiss = onDismissError,
            )
        }

        state.storage?.let { storage ->
            StorageSummary(
                storage = storage,
                onCleanup = onCleanupStorage,
            )
        }

        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.stores_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.stores_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                items(
                    items = state.entries,
                    key = { entry -> entry.descriptor.id.value },
                ) { entry ->
                    StoreCard(
                        entry = entry,
                        syncStatus = state.syncStatuses[entry.descriptor.id.value],
                        canMoveUp = state.entries.indexOf(entry) > 0,
                        canMoveDown = state.entries.indexOf(entry) < state.entries.lastIndex,
                        onToggle = onToggle,
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onEdit = onEdit,
                        onRequestDelete = onRequestDelete,
                        onExport = onExport,
                        onOpenHomepage = onOpenHomepage,
                        onSync = onSync,
                        onCancelSync = onCancelSync,
                        onOpenSecurityDetails = onOpenSecurityDetails,
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        StoreEditorDialog(
            editor = editor,
            onChange = onEditorChange,
            onSave = onSaveEditor,
            onDismiss = onDismissEditor,
            onReportProblem = onReportProblem,
        )
    }

    state.deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(R.string.stores_delete_title)) },
            text = {
                Text(stringResource(R.string.stores_delete_message, request.name))
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text(stringResource(R.string.stores_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(R.string.stores_editor_cancel))
                }
            },
        )
    }

    state.exportRequest?.let { request ->
        AlertDialog(
            onDismissRequest = onDismissExport,
            title = { Text(stringResource(R.string.stores_export_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.stores_export_message),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = request.json,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onShareExport(request) }) {
                    Text(stringResource(R.string.stores_export_share))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExport) {
                    Text(stringResource(R.string.stores_export_close))
                }
            },
        )
    }

    state.securityDetails?.let { details ->
        StoreSecurityDetailsDialog(
            details = details,
            onApproveFingerprint = { fingerprint ->
                onApproveFingerprint(details.storeId, fingerprint)
            },
            onRemoveStore = {
                state.entries
                    .firstOrNull { it.descriptor.id == details.storeId }
                    ?.descriptor
                    ?.let(onRequestDelete)
                onDismissSecurityDetails()
            },
            onDismiss = onDismissSecurityDetails,
        )
    }
}

@Composable
private fun StoresErrorBanner(
    error: StoresError,
    onRetry: () -> Unit,
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
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            if (error == StoresError.READ_FAILED) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.stores_retry))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.stores_dismiss))
                }
            }
        }
    }
}

@StringRes
private fun StoresError.messageRes(): Int = when (this) {
    StoresError.READ_FAILED -> R.string.stores_error_read
    StoresError.WRITE_FAILED -> R.string.stores_error_write
    StoresError.INVALID_STORE -> R.string.stores_error_invalid
    StoresError.HTTP_OPT_IN_REQUIRED -> R.string.stores_error_http
    StoresError.SYNC_FAILED -> R.string.stores_error_sync
    StoresError.SYNC_UNAVAILABLE -> R.string.stores_error_sync_unavailable
    StoresError.STORAGE_FAILED -> R.string.stores_error_storage
}

@Composable
private fun StorageSummary(
    storage: StorageStatus,
    onCleanup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (storage.overBudget) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.stores_storage_usage,
                        formatMegabytes(storage.usageBytes),
                        formatMegabytes(storage.budgetBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (storage.overBudget) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (storage.overBudget) {
                    Text(
                        text = stringResource(R.string.stores_storage_over_budget),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            TextButton(onClick = onCleanup) {
                Text(stringResource(R.string.stores_storage_cleanup))
            }
        }
    }
}

private fun formatMegabytes(bytes: Long): String =
    "%.1f".format(bytes.toDouble() / (1024.0 * 1024.0))

@Composable
private fun StoreCard(
    entry: StoreEntry,
    syncStatus: StoreSyncStatus?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (StoreId, Boolean) -> Unit,
    onMoveUp: (StoreId) -> Unit,
    onMoveDown: (StoreId) -> Unit,
    onEdit: (StoreDescriptor) -> Unit,
    onRequestDelete: (StoreDescriptor) -> Unit,
    onExport: (StoreId) -> Unit,
    onOpenHomepage: (String) -> Unit,
    onSync: (StoreId) -> Unit,
    onCancelSync: (StoreId) -> Unit,
    onOpenSecurityDetails: (StoreId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descriptor = entry.descriptor
    val name = descriptor.name
    val security = StoreSecurityDetails.from(descriptor, syncStatus)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (descriptor.builtIn) {
                            Text(
                                text = stringResource(R.string.stores_builtin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.stores_adapter_label,
                                stringResource(descriptor.adapterType.labelRes()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val toggleDescription = stringResource(R.string.stores_toggle, name)
                Switch(
                    checked = descriptor.enabled,
                    onCheckedChange = { enabled -> onToggle(descriptor.id, enabled) },
                    modifier = Modifier.semantics {
                        contentDescription = toggleDescription
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = descriptor.indexUrl
                    ?: descriptor.apiUrl
                    ?: descriptor.repositoryUrl
                    ?: descriptor.homepage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (security.httpInsecure) {
                Text(
                    text = stringResource(
                        if (security.httpOptIn) {
                            R.string.stores_http_opt_in
                        } else {
                            R.string.stores_http_blocked
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            descriptor.fingerprint?.let { fingerprint ->
                Text(
                    text = stringResource(R.string.stores_fingerprint_label, fingerprint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val builtInTrustNote = if (descriptor.builtIn) {
                stringResource(descriptor.id.builtInTrustNoteRes())
            } else {
                ""
            }
            val trustNote = descriptor.trustNote.ifBlank { builtInTrustNote }
            if (trustNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = trustNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val health = descriptor.health
            val healthError = health.error
            Spacer(modifier = Modifier.height(4.dp))
            when {
                health.status == StoreHealthStatus.ERROR && healthError != null -> Text(
                    text = stringResource(R.string.stores_health_error, healthError),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                health.status == StoreHealthStatus.UNKNOWN -> Text(
                    text = stringResource(R.string.stores_health_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                health.itemCount != null -> Text(
                    text = pluralStringResource(
                        R.plurals.stores_health_items,
                        health.itemCount ?: 0,
                        health.itemCount ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val linkOnlyReason = entry.availability.reason
            if (descriptor.adapterType == AdapterType.FDROID_V2) {
                Spacer(modifier = Modifier.height(8.dp))
                StoreSyncBlock(
                    status = syncStatus,
                    security = security,
                    storeId = descriptor.id,
                    onSync = onSync,
                    onCancelSync = onCancelSync,
                    onOpenSecurityDetails = onOpenSecurityDetails,
                )
            }
            if (linkOnlyReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.stores_link_only_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(linkOnlyReason.labelRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { onOpenHomepage(descriptor.homepage) }) {
                            Text(stringResource(R.string.stores_open_homepage))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val moveUpDescription = stringResource(R.string.stores_move_up, name)
                val moveDownDescription = stringResource(R.string.stores_move_down, name)
                val exportDescription = stringResource(R.string.stores_export_store, name)
                val editDescription = stringResource(R.string.stores_edit_store, name)
                val deleteDescription = stringResource(R.string.stores_delete_store, name)
                IconButton(
                    onClick = { onMoveUp(descriptor.id) },
                    enabled = canMoveUp,
                    modifier = Modifier.semantics {
                        contentDescription = moveUpDescription
                    },
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                }
                IconButton(
                    onClick = { onMoveDown(descriptor.id) },
                    enabled = canMoveDown,
                    modifier = Modifier.semantics {
                        contentDescription = moveDownDescription
                    },
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
                IconButton(
                    onClick = { onExport(descriptor.id) },
                    modifier = Modifier.semantics {
                        contentDescription = exportDescription
                    },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                }
                IconButton(
                    onClick = { onEdit(descriptor) },
                    modifier = Modifier.semantics {
                        contentDescription = editDescription
                    },
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
                IconButton(
                    onClick = { onRequestDelete(descriptor) },
                    modifier = Modifier.semantics {
                        contentDescription = deleteDescription
                    },
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun StoreSyncBlock(
    status: StoreSyncStatus?,
    security: StoreSecurityDetails,
    storeId: StoreId,
    onSync: (StoreId) -> Unit,
    onCancelSync: (StoreId) -> Unit,
    onOpenSecurityDetails: (StoreId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = status?.snapshot
    val job = status?.job
    val syncing = status?.isSyncing == true
    val freshness = status?.freshness ?: SnapshotFreshness.NEVER_SYNCED

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            snapshot == null -> stringResource(R.string.stores_sync_never)
                            else -> stringResource(
                                R.string.stores_sync_last,
                                DateUtils.getRelativeTimeSpanString(
                                    snapshot.fetchedAtEpochMillis,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                ).toString(),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(freshness.labelRes()),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (freshness) {
                            SnapshotFreshness.FRESH -> MaterialTheme.colorScheme.primary
                            SnapshotFreshness.STALE -> MaterialTheme.colorScheme.error
                            SnapshotFreshness.NEVER_SYNCED ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (syncing) {
                    OutlinedButton(onClick = { onCancelSync(storeId) }) {
                        Text(stringResource(R.string.stores_sync_cancel))
                    }
                } else {
                    OutlinedButton(onClick = { onSync(storeId) }) {
                        Text(
                            stringResource(
                                if (snapshot == null) {
                                    R.string.stores_sync_now
                                } else {
                                    R.string.stores_sync_again
                                },
                            ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(security.trustState.labelRes()),
                style = MaterialTheme.typography.bodySmall,
                color = when (security.trustState) {
                    StoreTrustState.VERIFIED -> MaterialTheme.colorScheme.primary
                    StoreTrustState.FAILED,
                    StoreTrustState.ROTATION_REQUIRED,
                    -> MaterialTheme.colorScheme.error

                    StoreTrustState.UNVERIFIED,
                    StoreTrustState.NOT_CHECKED,
                    -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            security.signerFingerprint?.let { signer ->
                if (security.trustState == StoreTrustState.VERIFIED) {
                    Text(
                        text = stringResource(R.string.stores_signer_fingerprint, signer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (security.rotation != null) {
                Text(
                    text = stringResource(R.string.stores_trust_rotation_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { onOpenSecurityDetails(storeId) }) {
                    Text(stringResource(R.string.stores_security_details_action))
                }
            }

            if (syncing && job != null) {
                val progress = job.progress
                Text(
                    text = if (progress == null) {
                        stringResource(R.string.stores_sync_progress_unknown)
                    } else {
                        stringResource(
                            R.string.stores_sync_progress,
                            (progress * 100).toInt(),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            job?.error?.let { error ->
                Text(
                    text = stringResource(error.kind.messageRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (security.rotation == null) {
                TextButton(onClick = { onOpenSecurityDetails(storeId) }) {
                    Text(stringResource(R.string.stores_security_details_action))
                }
            }
        }
    }
}

@StringRes
private fun SnapshotFreshness.labelRes(): Int = when (this) {
    SnapshotFreshness.FRESH -> R.string.stores_freshness_fresh
    SnapshotFreshness.STALE -> R.string.stores_freshness_stale
    SnapshotFreshness.NEVER_SYNCED -> R.string.stores_freshness_never
}

@StringRes
private fun SignatureStatus.labelRes(): Int = when (this) {
    SignatureStatus.VERIFIED -> R.string.stores_signature_verified
    SignatureStatus.UNVERIFIED -> R.string.stores_signature_unverified
    SignatureStatus.UNSIGNED -> R.string.stores_signature_unsigned
    SignatureStatus.INVALID -> R.string.stores_signature_invalid
    SignatureStatus.NOT_CHECKED -> R.string.stores_signature_not_checked
}

@StringRes
private fun SyncFailureKind.messageRes(): Int = when (this) {
    SyncFailureKind.UNSUPPORTED -> R.string.stores_sync_error_unsupported
    SyncFailureKind.HTTP_POLICY -> R.string.stores_sync_error_http_policy
    SyncFailureKind.FINGERPRINT_CHANGED -> R.string.stores_sync_error_fingerprint
    SyncFailureKind.NETWORK,
    SyncFailureKind.HTTP_STATUS,
    -> R.string.stores_sync_error_network

    SyncFailureKind.VERIFICATION -> R.string.stores_sync_error_verification
    SyncFailureKind.PARSE -> R.string.stores_sync_error_parse
    SyncFailureKind.STORAGE_LIMIT -> R.string.stores_sync_error_storage
    SyncFailureKind.IO -> R.string.stores_sync_error_io
    SyncFailureKind.CANCELLED -> R.string.stores_sync_error_cancelled
    SyncFailureKind.UNKNOWN -> R.string.stores_sync_error_unknown
}

@Composable
private fun StoreSecurityDetailsDialog(
    details: StoreSecurityDetails,
    onApproveFingerprint: (String) -> Unit,
    onRemoveStore: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(details.storeName)
                Text(
                    text = stringResource(R.string.stores_security_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.stores_security_state),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(details.trustState.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    color = when (details.trustState) {
                        StoreTrustState.VERIFIED -> MaterialTheme.colorScheme.primary
                        StoreTrustState.FAILED,
                        StoreTrustState.ROTATION_REQUIRED,
                        -> MaterialTheme.colorScheme.error

                        StoreTrustState.UNVERIFIED,
                        StoreTrustState.NOT_CHECKED,
                        -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = stringResource(details.trustState.bodyRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                details.rotation?.let { rotation ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.stores_security_rotation_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            val newFingerprint = rotation.newFingerprint
                            Text(
                                text = if (newFingerprint == null) {
                                    stringResource(
                                        R.string.stores_security_rotation_no_new,
                                        rotation.currentFingerprint.orEmpty(),
                                    )
                                } else {
                                    stringResource(
                                        R.string.stores_security_rotation_body,
                                        rotation.currentFingerprint.orEmpty(),
                                        newFingerprint,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            if (newFingerprint != null) {
                                Button(onClick = { onApproveFingerprint(newFingerprint) }) {
                                    Text(
                                        stringResource(
                                            R.string.stores_security_rotation_approve,
                                        ),
                                    )
                                }
                            }
                            TextButton(onClick = onRemoveStore) {
                                Text(stringResource(R.string.stores_security_rotation_remove))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                if (details.pinnedFingerprint == null) {
                    Text(
                        text = stringResource(R.string.stores_security_pinned_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = stringResource(
                                R.string.stores_security_pinned,
                                details.pinnedFingerprint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                details.expectedFingerprint?.let { expected ->
                    SelectionContainer {
                        Text(
                            text = stringResource(R.string.stores_security_expected, expected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SelectionContainer {
                    Text(
                        text = details.signerFingerprint?.let { signer ->
                            stringResource(R.string.stores_security_signer, signer)
                        } ?: stringResource(R.string.stores_security_signer_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = details.signatureStatus?.let { status ->
                        stringResource(
                            R.string.stores_security_signature,
                            stringResource(status.labelRes()),
                        )
                    } ?: stringResource(
                        R.string.stores_security_signature,
                        stringResource(R.string.stores_security_signature_not_checked),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = details.verificationCheckedAtEpochMillis?.let { checkedAt ->
                        stringResource(
                            R.string.stores_security_verified_at,
                            formatRelativeTime(checkedAt),
                        )
                    } ?: stringResource(R.string.stores_security_never_verified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = details.lastSuccessfulSyncEpochMillis?.let { lastSync ->
                        stringResource(
                            R.string.stores_security_last_sync,
                            formatRelativeTime(lastSync),
                        )
                    } ?: stringResource(R.string.stores_security_never_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = details.verificationSourceUrl?.let { source ->
                        stringResource(R.string.stores_security_source, source)
                    } ?: stringResource(R.string.stores_security_source_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                details.indexSourceUrl?.let { source ->
                    Text(
                        text = stringResource(R.string.stores_security_index_source, source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                details.indexVersion?.let { version ->
                    Text(
                        text = stringResource(R.string.stores_security_index_version, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                details.indexTimestamp?.let { timestamp ->
                    Text(
                        text = stringResource(
                            R.string.stores_security_index_timestamp,
                            formatTimestamp(timestamp),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                details.indexSha256?.let { hash ->
                    SelectionContainer {
                        Text(
                            text = stringResource(R.string.stores_security_index_hash, hash),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = when {
                        !details.httpInsecure -> stringResource(R.string.stores_security_https)
                        details.httpOptIn -> stringResource(R.string.stores_security_http_opt_in)
                        else -> stringResource(R.string.stores_security_http_blocked)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (details.httpInsecure && !details.httpOptIn) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                details.verificationFailure?.let { failure ->
                    Text(
                        text = stringResource(
                            R.string.stores_security_failure,
                            stringResource(failure.labelRes()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                details.syncFailure?.let { failure ->
                    Text(
                        text = stringResource(
                            R.string.stores_security_sync_failure,
                            stringResource(failure.messageRes()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.stores_security_audit_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (details.audit.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stores_security_audit_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val recent = details.audit.takeLast(5).reversed()
                    Text(
                        text = pluralStringResource(
                            R.plurals.stores_security_audit_count,
                            details.audit.size,
                            recent.size,
                            details.audit.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    recent.forEach { record ->
                        AuditRow(record)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.stores_security_apk_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.stores_security_close))
            }
        },
    )
}

@Composable
private fun AuditRow(record: TrustRecord) {
    val status = stringResource(record.signatureStatus.labelRes())
    val failure = record.failure?.let { stringResource(it.labelRes()) }
    Text(
        text = if (failure == null) {
            stringResource(
                R.string.stores_security_audit_line,
                formatRelativeTime(record.checkedAtEpochMillis),
                status,
            )
        } else {
            stringResource(
                R.string.stores_security_audit_failure,
                formatRelativeTime(record.checkedAtEpochMillis),
                status,
                failure,
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatRelativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))

@StringRes
private fun StoreTrustState.labelRes(): Int = when (this) {
    StoreTrustState.VERIFIED -> R.string.stores_trust_verified
    StoreTrustState.UNVERIFIED -> R.string.stores_trust_unverified
    StoreTrustState.FAILED -> R.string.stores_trust_failed
    StoreTrustState.ROTATION_REQUIRED -> R.string.stores_trust_rotation_required
    StoreTrustState.NOT_CHECKED -> R.string.stores_trust_not_checked
}

@StringRes
private fun StoreTrustState.bodyRes(): Int = when (this) {
    StoreTrustState.VERIFIED -> R.string.stores_trust_verified_body
    StoreTrustState.UNVERIFIED -> R.string.stores_trust_unverified_body
    StoreTrustState.FAILED -> R.string.stores_trust_failed_body
    StoreTrustState.ROTATION_REQUIRED -> R.string.stores_trust_rotation_body
    StoreTrustState.NOT_CHECKED -> R.string.stores_trust_not_checked_body
}

@StringRes
private fun VerificationFailure.labelRes(): Int = when (this) {
    VerificationFailure.ENTRY_MISSING -> R.string.stores_verification_failure_entry_missing
    VerificationFailure.ENTRY_MALFORMED -> R.string.stores_verification_failure_entry_malformed
    VerificationFailure.NO_SIGNATURE -> R.string.stores_verification_failure_no_signature
    VerificationFailure.TAMPERED -> R.string.stores_verification_failure_tampered
    VerificationFailure.SIGNER_MISMATCH -> R.string.stores_verification_failure_signer_mismatch
    VerificationFailure.HASH_MISMATCH -> R.string.stores_verification_failure_hash_mismatch
    VerificationFailure.SIZE_MISMATCH -> R.string.stores_verification_failure_size_mismatch
    VerificationFailure.NO_PINNED_FINGERPRINT ->
        R.string.stores_verification_failure_no_pinned_fingerprint

    VerificationFailure.UNSUPPORTED -> R.string.stores_verification_failure_unsupported
}

@Composable
private fun StoreEditorDialog(
    editor: StoreEditor,
    onChange: (StoreEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onReportProblem: ((StoreEditor) -> Unit)? = null,
) {
    val usesHttp = editor.usesInsecureHttp()
    val canSave = editor.name.isNotBlank() &&
        editor.homepage.isNotBlank() &&
        (!usesHttp || editor.allowInsecureHttp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.isEditing) {
                        R.string.stores_editor_edit_title
                    } else {
                        R.string.stores_editor_add_title
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onChange(editor.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.homepage,
                    onValueChange = { onChange(editor.copy(homepage = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_homepage)) },
                    singleLine = true,
                )
                AdapterPicker(
                    selected = editor.adapterType,
                    onSelected = { onChange(editor.copy(adapterType = it)) },
                )
                OutlinedTextField(
                    value = editor.indexUrl,
                    onValueChange = { onChange(editor.copy(indexUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_index_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.apiUrl,
                    onValueChange = { onChange(editor.copy(apiUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_api_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.repositoryUrl,
                    onValueChange = { onChange(editor.copy(repositoryUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_repository_url)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.fingerprint,
                    onValueChange = { onChange(editor.copy(fingerprint = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_fingerprint)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editor.trustNote,
                    onValueChange = { onChange(editor.copy(trustNote = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stores_editor_trust_note)) },
                    maxLines = 3,
                )

                if (usesHttp) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.stores_editor_http_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Switch(
                                    checked = editor.allowInsecureHttp,
                                    onCheckedChange = {
                                        onChange(editor.copy(allowInsecureHttp = it))
                                    },
                                )
                                Text(
                                    text = stringResource(
                                        R.string.stores_editor_http_opt_in,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = canSave) {
                Text(stringResource(R.string.stores_editor_save))
            }
        },
        dismissButton = {
            Row {
                if (onReportProblem != null) {
                    TextButton(onClick = { onReportProblem(editor) }) {
                        Text(stringResource(R.string.stores_report_problem))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.stores_editor_cancel))
                }
            }
        },
    )
}

@Composable
private fun AdapterPicker(
    selected: AdapterType,
    onSelected: (AdapterType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.stores_editor_adapter),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(stringResource(selected.labelRes()))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                AdapterType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.labelRes())) },
                        onClick = {
                            expanded = false
                            onSelected(type)
                        },
                    )
                }
            }
        }
    }
}

@StringRes
private fun AdapterType.labelRes(): Int = when (this) {
    AdapterType.FDROID_V2 -> R.string.stores_adapter_fdroid_v2
    AdapterType.FDROID_V1 -> R.string.stores_adapter_fdroid_v1
    AdapterType.LIVE_API -> R.string.stores_adapter_live_api
    AdapterType.GENERIC_JSON -> R.string.stores_adapter_generic_json
    AdapterType.SOURCE_REPOSITORY -> R.string.stores_adapter_source_repository
    AdapterType.DEEP_LINK -> R.string.stores_adapter_deep_link
}

@StringRes
private fun LinkOnlyReason.labelRes(): Int = when (this) {
    LinkOnlyReason.ADAPTER_UNSUPPORTED -> R.string.stores_link_only_adapter
    LinkOnlyReason.MISSING_CATALOG_ENDPOINT -> R.string.stores_link_only_endpoint
}

@StringRes
private fun StoreId.builtInTrustNoteRes(): Int = when (value) {
    BuiltInStores.FDROID_ID -> R.string.stores_trust_note_fdroid
    BuiltInStores.IZZY_ON_DROID_ID -> R.string.stores_trust_note_izzy
    else -> R.string.stores_trust_note_predefined
}
