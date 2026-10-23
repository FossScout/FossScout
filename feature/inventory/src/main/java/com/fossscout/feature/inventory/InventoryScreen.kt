package com.fossscout.feature.inventory

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.inventory.InventoryEntry
import com.fossscout.core.inventory.InventorySnapshot
import com.fossscout.core.inventory.InventoryStatus
import com.fossscout.core.inventory.ScanMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InventoryScreen(
    state: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCheck: () -> Unit,
    onSelectCandidate: (InstalledApp) -> Unit,
    onFullScan: () -> Unit,
    onCancelScan: () -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
    onIncludeDisabledChange: (Boolean) -> Unit,
    onIncludeWorkProfileChange: (Boolean) -> Unit,
    onIncludeHiddenChange: (Boolean) -> Unit,
    onHide: (String) -> Unit,
    onUnhide: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.inventory_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.inventory_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error?.let { error ->
            InventoryErrorBanner(
                message = stringResource(error.messageRes()),
                onDismiss = onDismissError,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "query") {
                QueryCard(
                    state = state,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onCheck = onCheck,
                )
            }

            item(key = "scan") {
                ScanCard(
                    canFullScan = state.canFullScan,
                    isScanning = state.isScanning,
                    onFullScan = onFullScan,
                    onCancelScan = onCancelScan,
                )
            }

            val snapshot = state.snapshot
            if (snapshot != null) {
                item(key = "summary") {
                    SnapshotSummary(snapshot = snapshot, isStale = state.isStale)
                }
                item(key = "filters") {
                    InventoryFiltersRow(
                        state = state,
                        onIncludeSystemChange = onIncludeSystemChange,
                        onIncludeDisabledChange = onIncludeDisabledChange,
                        onIncludeWorkProfileChange = onIncludeWorkProfileChange,
                        onIncludeHiddenChange = onIncludeHiddenChange,
                    )
                }
            }

            if (state.candidates.isNotEmpty()) {
                item(key = "candidates_header") {
                    Column {
                        Text(
                            text = stringResource(R.string.inventory_pick_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.inventory_pick_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = state.candidates,
                    key = { app -> "candidate_${app.packageName}" },
                ) { app ->
                    CandidateCard(app = app, onClick = { onSelectCandidate(app) })
                }
            }

            when {
                state.isLoading -> item(key = "loading") {
                    InventoryLoading()
                }

                snapshot == null -> item(key = "empty") {
                    EmptyInventory()
                }

                state.entries.isEmpty() -> item(key = "no_matches") {
                    Text(
                        text = stringResource(R.string.inventory_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

                else -> items(
                    items = state.entries,
                    key = { entry -> "entry_${entry.identity}" },
                ) { entry ->
                    val packageName = entry.app?.packageName
                    InventoryEntryCard(
                        entry = entry,
                        isHidden = packageName != null && packageName in state.hiddenPackages,
                        onHide = { packageName?.let(onHide) },
                        onUnhide = { packageName?.let(onUnhide) },
                    )
                }
            }
        }
    }
}

@StringRes
private fun InventoryError.messageRes(): Int = when (this) {
    InventoryError.READ_FAILED -> R.string.inventory_error_read
    InventoryError.CHECK_FAILED -> R.string.inventory_error_check
    InventoryError.SEARCH_FAILED -> R.string.inventory_error_search
    InventoryError.SCAN_FAILED -> R.string.inventory_error_scan
    InventoryError.UPDATE_FAILED -> R.string.inventory_error_update
}

@Composable
private fun InventoryErrorBanner(
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
                Text(stringResource(R.string.inventory_dismiss))
            }
        }
    }
}

@Composable
private fun QueryCard(
    state: InventoryUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCheck: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.inventory_query_label)) },
                placeholder = { Text(stringResource(R.string.inventory_query_hint)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val queryReady = state.query.isNotBlank()
                TextButton(
                    onClick = onSearch,
                    enabled = queryReady && !state.isSearching,
                ) {
                    if (state.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.inventory_searching))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.inventory_search))
                    }
                }
                Button(
                    onClick = onCheck,
                    enabled = queryReady && !state.isChecking,
                ) {
                    if (state.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.inventory_checking))
                    } else {
                        Text(stringResource(R.string.inventory_check))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanCard(
    canFullScan: Boolean,
    isScanning: Boolean,
    onFullScan: () -> Unit,
    onCancelScan: () -> Unit,
) {
    if (!canFullScan) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.inventory_scan_unavailable_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.inventory_scan_unavailable_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isScanning) {
                Text(
                    text = stringResource(R.string.inventory_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onCancelScan, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.inventory_cancel_scan))
                }
            } else {
                Button(onClick = onFullScan, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.inventory_scan_all))
                }
            }
        }
    }
}

@Composable
private fun SnapshotSummary(
    snapshot: InventorySnapshot,
    isStale: Boolean,
) {
    val timestamp = remember(snapshot.capturedAtEpochMillis) {
        formatTimestamp(snapshot.capturedAtEpochMillis)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (snapshot.scanMode == ScanMode.FULL) {
                            R.string.inventory_scanned_all
                        } else {
                            R.string.inventory_checked_one
                        },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = snapshot.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.inventory_last_checked, timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (snapshot.scanMode == ScanMode.FULL) {
                Text(
                    text = stringResource(
                        R.string.inventory_coverage,
                        snapshot.coverage.scannedApps,
                        maxOf(snapshot.coverage.visibleApps, snapshot.coverage.scannedApps),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (snapshot.coverage.inaccessibleApps > 0) {
                    Text(
                        text = stringResource(
                            R.string.inventory_coverage_partial,
                            snapshot.coverage.inaccessibleApps,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isStale) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringResource(R.string.inventory_stale_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.inventory_stale_body, timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryFiltersRow(
    state: InventoryUiState,
    onIncludeSystemChange: (Boolean) -> Unit,
    onIncludeDisabledChange: (Boolean) -> Unit,
    onIncludeWorkProfileChange: (Boolean) -> Unit,
    onIncludeHiddenChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InventoryFilterChip(
                label = stringResource(R.string.inventory_filter_system),
                selected = state.filters.includeSystemApps,
                onSelected = onIncludeSystemChange,
            )
            InventoryFilterChip(
                label = stringResource(R.string.inventory_filter_disabled),
                selected = state.filters.includeDisabledApps,
                onSelected = onIncludeDisabledChange,
            )
            InventoryFilterChip(
                label = stringResource(R.string.inventory_filter_work),
                selected = state.filters.includeWorkProfileApps,
                onSelected = onIncludeWorkProfileChange,
            )
            InventoryFilterChip(
                label = stringResource(R.string.inventory_filter_hidden),
                selected = state.filters.includeHiddenApps,
                onSelected = onIncludeHiddenChange,
            )
        }
        if (state.hiddenCount > 0) {
            Text(
                text = stringResource(R.string.inventory_hidden_count, state.hiddenCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun InventoryFilterChip(
    label: String,
    selected: Boolean,
    onSelected: (Boolean) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelected(!selected) },
        label = { Text(label) },
    )
}

@Composable
private fun CandidateCard(
    app: InstalledApp,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = app.versionName ?: app.versionCode.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InventoryEntryCard(
    entry: InventoryEntry,
    isHidden: Boolean,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
) {
    val app = entry.app
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.identity,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ClassificationBadge(classification = entry.classification)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.explanation
                    ?: stringResource(entry.classification.explanationRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (app != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = versionText(app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = app.installerPackage?.let {
                        stringResource(R.string.inventory_installer, it)
                    } ?: stringResource(R.string.inventory_installer_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = app.signerSha256?.let {
                        stringResource(R.string.inventory_signer, it.take(16) + "…")
                    } ?: stringResource(R.string.inventory_signer_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.isSystemApp) {
                        EntryBadge(stringResource(R.string.inventory_system_badge))
                    }
                    if (!app.isEnabled) {
                        EntryBadge(stringResource(R.string.inventory_disabled_badge))
                    }
                    if (app.profile.isWorkProfile) {
                        EntryBadge(stringResource(R.string.inventory_work_badge))
                    }
                    if (isHidden) {
                        EntryBadge(stringResource(R.string.inventory_hidden_badge))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isHidden) {
                    TextButton(onClick = onUnhide) {
                        Text(stringResource(R.string.inventory_unhide, entry.label))
                    }
                } else {
                    TextButton(onClick = onHide, enabled = app != null) {
                        Text(stringResource(R.string.inventory_hide, entry.label))
                    }
                }
            }
        }
    }
}

@Composable
private fun versionText(app: InstalledApp): String {
    val versionName = app.versionName
    return if (versionName != null) {
        stringResource(R.string.inventory_version_name_code, versionName, app.versionCode)
    } else {
        stringResource(R.string.inventory_version_code, app.versionCode)
    }
}

@Composable
private fun ClassificationBadge(classification: Classification) {
    val colors = when (classification) {
        Classification.FOSS -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        Classification.PROPRIETARY -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer

        Classification.UNKNOWN -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        Classification.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = colors.first,
        contentColor = colors.second,
    ) {
        Text(
            text = stringResource(classification.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StatusBadge(status: InventoryStatus) {
    val container: Color = if (status == InventoryStatus.COMPLETE) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val content: Color = if (status == InventoryStatus.COMPLETE) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text = stringResource(status.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EntryBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun InventoryLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.inventory_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyInventory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.inventory_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.inventory_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
