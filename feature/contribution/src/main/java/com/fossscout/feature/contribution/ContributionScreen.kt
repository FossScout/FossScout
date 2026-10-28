package com.fossscout.feature.contribution

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fossscout.core.contribution.ContributionReport
import com.fossscout.core.contribution.ReportErrorClass
import com.fossscout.core.contribution.ReportKind
import com.fossscout.core.store.AdapterType

@Composable
fun ContributionScreen(
    state: ContributionUiState,
    onBack: () -> Unit,
    onKindSelected: (ReportKind) -> Unit,
    onDraftChange: (ContributionDraft) -> Unit,
    onPreview: () -> Unit,
    onDismissPreview: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenIssue: () -> Unit,
    onCopyJson: () -> Unit,
    onShareJson: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ContributionHeader(onBack = onBack)
        val preview = state.preview
        if (preview == null) {
            ContributionForm(
                state = state,
                onKindSelected = onKindSelected,
                onDraftChange = onDraftChange,
                onPreview = onPreview,
                onDismissError = onDismissError,
                modifier = Modifier.weight(1f),
            )
        } else {
            ContributionPreview(
                preview = preview,
                onDismissPreview = onDismissPreview,
                onCopy = onCopy,
                onShare = onShare,
                onOpenIssue = onOpenIssue,
                onCopyJson = onCopyJson,
                onShareJson = onShareJson,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ContributionHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.contribution_back))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.contribution_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.contribution_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContributionForm(
    state: ContributionUiState,
    onKindSelected: (ReportKind) -> Unit,
    onDraftChange: (ContributionDraft) -> Unit,
    onPreview: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "privacy") {
            PrivacyCard(
                title = stringResource(R.string.contribution_privacy_title),
                body = stringResource(R.string.contribution_privacy_body),
            )
        }
        state.error?.let { error ->
            item(key = "error") {
                ContributionErrorBanner(
                    message = stringResource(error.messageRes()),
                    onDismiss = onDismissError,
                )
            }
        }
        item(key = "kind") {
            KindSelector(
                selected = draft.kind,
                onSelected = onKindSelected,
            )
        }
        item(key = "form") {
            ReportFormCard(
                draft = draft,
                onDraftChange = onDraftChange,
            )
        }
        item(key = "debug") {
            DebugToggleCard(
                checked = draft.includeDebugDetails,
                onCheckedChange = {
                    onDraftChange(draft.copy(includeDebugDetails = it))
                },
            )
        }
        item(key = "preview_action") {
            Button(
                onClick = onPreview,
                enabled = !state.isPreparing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.contribution_preview_action))
            }
        }
    }
}

@Composable
private fun PrivacyCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun KindSelector(
    selected: ReportKind,
    onSelected: (ReportKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.contribution_kind_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReportKind.entries.forEach { kind ->
                FilterChip(
                    selected = selected == kind,
                    onClick = { onSelected(kind) },
                    label = { Text(stringResource(kind.titleRes())) },
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReportFormCard(
    draft: ContributionDraft,
    onDraftChange: (ContributionDraft) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (draft.kind) {
                ReportKind.WRONG_MATCH -> {
                    Text(
                        text = stringResource(R.string.contribution_match_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = draft.packageName,
                        onValueChange = { onDraftChange(draft.copy(packageName = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_package_name)) },
                        placeholder = {
                            Text(stringResource(R.string.contribution_package_name_hint))
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.expectedStore,
                        onValueChange = { onDraftChange(draft.copy(expectedStore = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_expected_store)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.observedResult,
                        onValueChange = { onDraftChange(draft.copy(observedResult = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_observed_result)) },
                        singleLine = true,
                    )
                    if (draft.coverage != null) {
                        Text(
                            text = stringResource(R.string.contribution_coverage_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ReportKind.MISSING_STORE,
                ReportKind.ADAPTER_REQUEST,
                -> {
                    Text(
                        text = stringResource(R.string.contribution_store_section),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedTextField(
                        value = draft.storeName,
                        onValueChange = { onDraftChange(draft.copy(storeName = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_store_name)) },
                        placeholder = {
                            Text(stringResource(R.string.contribution_store_name_hint))
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.storeHomepage,
                        onValueChange = { onDraftChange(draft.copy(storeHomepage = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_store_url)) },
                        placeholder = {
                            Text(stringResource(R.string.contribution_store_url_hint))
                        },
                        singleLine = true,
                    )
                    Text(
                        text = stringResource(R.string.contribution_adapter),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AdapterSelector(
                        selected = draft.adapterType,
                        onSelected = { onDraftChange(draft.copy(adapterType = it)) },
                    )
                    OutlinedTextField(
                        value = draft.indexUrl,
                        onValueChange = { onDraftChange(draft.copy(indexUrl = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.contribution_index_url)) },
                        placeholder = {
                            Text(stringResource(R.string.contribution_index_url_hint))
                        },
                        singleLine = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.contribution_error_section),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.contribution_error_class),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ErrorClassSelector(
                selected = draft.errorClass,
                onSelected = { onDraftChange(draft.copy(errorClass = it)) },
            )
            OutlinedTextField(
                value = draft.errorMessage,
                onValueChange = { onDraftChange(draft.copy(errorMessage = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.contribution_error_details)) },
                placeholder = {
                    Text(stringResource(R.string.contribution_error_details_hint))
                },
                minLines = 3,
                maxLines = 6,
            )
        }
    }
}

@Composable
private fun DebugToggleCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.contribution_include_debug_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.contribution_include_debug_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ContributionPreview(
    preview: ContributionReport,
    onDismissPreview: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenIssue: () -> Unit,
    onCopyJson: () -> Unit,
    onShareJson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "preview_intro") {
            PrivacyCard(
                title = stringResource(R.string.contribution_preview_title),
                body = stringResource(R.string.contribution_preview_body),
            )
        }
        item(key = "preview_body") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = preview.body,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        item(key = "preview_actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.contribution_copy))
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.contribution_share))
                }
            }
        }
        item(key = "issue_action") {
            OutlinedButton(
                onClick = onOpenIssue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.contribution_open_issue))
            }
        }
        if (preview.payload.debugDetails.isNotEmpty()) {
            item(key = "debug_note") {
                Text(
                    text = stringResource(R.string.contribution_preview_debug_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "debug_actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onCopyJson,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.contribution_copy_json))
                    }
                    TextButton(
                        onClick = onShareJson,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.contribution_share_json))
                    }
                }
            }
        }
        item(key = "back_to_edit") {
            TextButton(
                onClick = onDismissPreview,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.contribution_back_to_edit))
            }
        }
    }
}

@Composable
private fun ContributionErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.contribution_dismiss))
            }
        }
    }
}

@Composable
private fun AdapterSelector(
    selected: AdapterType?,
    onSelected: (AdapterType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.let { stringResource(it.labelRes()) }
                    ?: stringResource(R.string.contribution_adapter_none),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contribution_adapter_none)) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            AdapterType.entries.forEach { adapter ->
                DropdownMenuItem(
                    text = { Text(stringResource(adapter.labelRes())) },
                    onClick = {
                        onSelected(adapter)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ErrorClassSelector(
    selected: ReportErrorClass?,
    onSelected: (ReportErrorClass?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected?.let { stringResource(it.labelRes()) }
                    ?: stringResource(R.string.contribution_error_other),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ReportErrorClass.entries.forEach { errorClass ->
                DropdownMenuItem(
                    text = { Text(stringResource(errorClass.labelRes())) },
                    onClick = {
                        onSelected(errorClass)
                        expanded = false
                    },
                )
            }
        }
    }
}
