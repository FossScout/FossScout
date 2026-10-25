package com.fossscout.feature.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fossscout.core.handoff.AppHandoff
import com.fossscout.core.handoff.CompanionStore
import com.fossscout.core.handoff.HandoffFallback
import com.fossscout.core.handoff.InstalledCompanionStore
import com.fossscout.core.handoff.ReplacementAssessment
import com.fossscout.core.handoff.ReplacementVerdict
import com.fossscout.core.handoff.ReplacementWarning
import com.fossscout.core.handoff.RepositoryHandoff
import com.fossscout.core.handoff.SignerComparison
import com.fossscout.core.handoff.VersionSummary

/**
 * F07/F08 result surface. The screen only offers safe handoffs; it has no
 * install, uninstall, or delete action.
 */
@Composable
fun CompanionScreen(
    state: CompanionUiState,
    onBack: () -> Unit,
    onSelectClient: (String) -> Unit,
    onConfirmReplace: (Boolean) -> Unit,
    onToggleQr: () -> Unit,
    onOpenStoreApp: (AppHandoff) -> Unit,
    onOpenRepository: (String, String?) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.companion_back))
            }
            Text(
                text = stringResource(R.string.companion_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.companion_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { error ->
                item(key = "error") {
                    ErrorBanner(
                        message = stringResource(error.messageRes()),
                        onDismiss = onDismissError,
                    )
                }
            }

            if (state.isLoading) {
                item(key = "loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.companion_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else if (state.candidate == null) {
                item(key = "empty") {
                    EmptyState()
                }
            } else {
                if (state.showReplacementGuidance) {
                    state.assessment?.let { assessment ->
                        item(key = "replacement") {
                            ReplacementCard(
                                assessment = assessment,
                                confirmed = state.replaceConfirmed,
                                onConfirm = onConfirmReplace,
                            )
                        }
                    }
                }
                if (state.showHandoff) {
                    item(key = "handoff") {
                        HandoffCard(
                            state = state,
                            onSelectClient = onSelectClient,
                            onToggleQr = onToggleQr,
                            onOpenStoreApp = onOpenStoreApp,
                            onOpenRepository = onOpenRepository,
                            onOpenBrowser = onOpenBrowser,
                            onCopy = onCopy,
                        )
                    }
                }
                if (!state.showReplacementGuidance && !state.showHandoff) {
                    item(key = "disabled") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.companion_disabled_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.companion_disabled_body),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplacementCard(
    assessment: ReplacementAssessment,
    confirmed: Boolean,
    onConfirm: (Boolean) -> Unit,
) {
    val container = when (assessment.verdict) {
        ReplacementVerdict.UPDATE -> MaterialTheme.colorScheme.primaryContainer
        ReplacementVerdict.REPLACE -> MaterialTheme.colorScheme.errorContainer
        ReplacementVerdict.ALTERNATIVE -> MaterialTheme.colorScheme.secondaryContainer
        ReplacementVerdict.CANNOT_VERIFY -> MaterialTheme.colorScheme.tertiaryContainer
        ReplacementVerdict.NO_UPDATE,
        ReplacementVerdict.NOT_INSTALLED,
        -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.companion_replacement_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = stringResource(assessment.verdict.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (assessment.isInstalled) {
                VersionLine(
                    label = assessment.installedLabel ?: assessment.installedPackageName,
                    version = assessment.installedVersion,
                    prefix = stringResource(R.string.companion_installed, ""),
                )
            }
            VersionLine(
                label = assessment.candidatePackageName,
                version = assessment.candidateVersion,
                prefix = stringResource(R.string.companion_candidate, ""),
            )
            if (assessment.isInstalled) {
                Text(
                    text = stringResource(assessment.signerComparison.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                assessment.installedInstallerPackage?.let { installer ->
                    Text(
                        text = stringResource(R.string.companion_installer, installer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (assessment.warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                assessment.warnings.forEach { warning ->
                    Text(
                        text = "• ${stringResource(warning.messageRes())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (assessment.explanation.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                assessment.explanation.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (assessment.requiresExplicitConfirmation) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirm,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.companion_confirm_replace),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.companion_no_uninstall_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionLine(
    label: String?,
    version: VersionSummary?,
    prefix: String,
) {
    val versionName = version?.versionName
    val versionCode = version?.versionCode
    val versionText = when {
        versionName != null && versionCode != null -> {
            stringResource(R.string.companion_version_line, versionName, versionCode)
        }

        versionName != null -> versionName
        versionCode != null -> {
            stringResource(R.string.companion_version_code_only, versionCode)
        }

        else -> stringResource(R.string.companion_version_unknown)
    }
    val labelText = label?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.companion_verdict_not_installed)
    Text(
        text = "$prefix$labelText — $versionText",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun HandoffCard(
    state: CompanionUiState,
    onSelectClient: (String) -> Unit,
    onToggleQr: () -> Unit,
    onOpenStoreApp: (AppHandoff) -> Unit,
    onOpenRepository: (String, String?) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.companion_handoff_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.clients.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                NoStoreFallback(
                    fallback = state.fallback,
                    repository = state.repositoryHandoff,
                    showQr = state.showQr,
                    onToggleQr = onToggleQr,
                    onOpenBrowser = onOpenBrowser,
                    onCopy = onCopy,
                )
                return@Column
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.companion_installed_clients),
                style = MaterialTheme.typography.titleSmall,
            )
            state.clients.forEach { client ->
                ClientRow(
                    client = client,
                    selected = client.store.id == state.selectedClientId,
                    onSelect = onSelectClient,
                )
            }

            if (!state.hasEnabledClient) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.companion_all_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val selected = state.selectedClient
            if (selected != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { state.appHandoff?.let(onOpenStoreApp) },
                    enabled = state.canOpenAppInStore,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.companion_open_in, selected.store.displayName))
                }
                if (state.requiresConfirmation && !state.replaceConfirmed) {
                    Text(
                        text = stringResource(R.string.companion_need_confirmation),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.appHandoff == null) {
                    Text(
                        text = stringResource(
                            R.string.companion_app_not_listed,
                            selected.store.displayName,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (state.repositoryHandoff != null) {
                RepositoryCard(
                    repository = state.repositoryHandoff,
                    selectedClient = selected?.store,
                    canAdd = state.canAddRepository,
                    showQr = state.showQr,
                    onToggleQr = onToggleQr,
                    onOpenRepository = onOpenRepository,
                    onCopy = onCopy,
                )
            } else {
                Text(
                    text = stringResource(R.string.companion_repository_unsupported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.fallback?.let { fallback ->
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { onOpenBrowser(fallback.browserUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.companion_open_browser))
                }
            }
        }
    }
}

@Composable
private fun ClientRow(
    client: InstalledCompanionStore,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = client.enabled) { onSelect(client.store.id) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = { if (client.enabled) onSelect(client.store.id) },
            enabled = client.enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (client.enabled) {
                    client.store.displayName
                } else {
                    stringResource(R.string.companion_client_disabled, client.store.displayName)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            client.versionName?.let { version ->
                Text(
                    text = version,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RepositoryCard(
    repository: RepositoryHandoff,
    selectedClient: CompanionStore?,
    canAdd: Boolean,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    onOpenRepository: (String, String?) -> Unit,
    onCopy: (String) -> Unit,
) {
    val descriptor = repository.descriptor
    Text(
        text = stringResource(R.string.companion_repository_title, descriptor.storeName),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.companion_repository_url, descriptor.repositoryUrl),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = stringResource(
            R.string.companion_repository_fingerprint,
            descriptor.fingerprint,
        ),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onCopy(repository.copyPayload) }) {
            Text(stringResource(R.string.companion_copy_repository))
        }
        OutlinedButton(onClick = onToggleQr) {
            Text(
                stringResource(
                    if (showQr) R.string.companion_hide_qr else R.string.companion_show_qr,
                ),
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onCopy(descriptor.repositoryUrl) }) {
            Text(stringResource(R.string.companion_copy_url))
        }
        TextButton(onClick = { onCopy(descriptor.fingerprint) }) {
            Text(stringResource(R.string.companion_copy_fingerprint))
        }
    }
    if (showQr) {
        Spacer(modifier = Modifier.height(8.dp))
        RepositoryQrCode(payload = repository.qrPayload)
    }
    if (canAdd && selectedClient != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenRepository(
                    repository.addRepositoryUri,
                    selectedClient.packageName,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    R.string.companion_add_repository,
                    selectedClient.displayName,
                ),
            )
        }
    }
}

@Composable
private fun NoStoreFallback(
    fallback: HandoffFallback?,
    repository: RepositoryHandoff?,
    showQr: Boolean,
    onToggleQr: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.companion_no_store_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.companion_no_store_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            fallback?.let {
                Button(
                    onClick = { onOpenBrowser(it.installUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.companion_get_fdroid))
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { onOpenBrowser(it.browserUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.companion_open_browser))
                }
            }
            if (repository != null) {
                Spacer(modifier = Modifier.height(10.dp))
                RepositoryCard(
                    repository = repository,
                    selectedClient = null,
                    canAdd = false,
                    showQr = showQr,
                    onToggleQr = onToggleQr,
                    onOpenRepository = { _, _ -> },
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                Text(stringResource(R.string.companion_dismiss))
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.companion_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.companion_empty_body),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun ReplacementVerdict.labelRes(): Int = when (this) {
    ReplacementVerdict.UPDATE -> R.string.companion_verdict_update
    ReplacementVerdict.REPLACE -> R.string.companion_verdict_replace
    ReplacementVerdict.ALTERNATIVE -> R.string.companion_verdict_alternative
    ReplacementVerdict.NO_UPDATE -> R.string.companion_verdict_no_update
    ReplacementVerdict.CANNOT_VERIFY -> R.string.companion_verdict_cannot_verify
    ReplacementVerdict.NOT_INSTALLED -> R.string.companion_verdict_not_installed
}

private fun SignerComparison.labelRes(): Int = when (this) {
    SignerComparison.SAME -> R.string.companion_signer_same
    SignerComparison.DIFFERENT -> R.string.companion_signer_different
    SignerComparison.UNKNOWN -> R.string.companion_signer_unknown
    SignerComparison.NOT_APPLICABLE -> R.string.companion_signer_not_applicable
}

private fun ReplacementWarning.messageRes(): Int = when (this) {
    ReplacementWarning.DATA_LOSS -> R.string.companion_warning_data_loss
    ReplacementWarning.UNINSTALL_REQUIRED -> R.string.companion_warning_uninstall_required
    ReplacementWarning.SYSTEM_APP -> R.string.companion_warning_system_app
    ReplacementWarning.OTHER_STORE -> R.string.companion_warning_other_store
    ReplacementWarning.CROSS_STORE_KEYS -> R.string.companion_warning_cross_store_keys
    ReplacementWarning.DOWNGRADE -> R.string.companion_warning_downgrade
    ReplacementWarning.VERSION_UNKNOWN -> R.string.companion_warning_version_unknown
    ReplacementWarning.SIGNER_UNKNOWN -> R.string.companion_warning_signer_unknown
    ReplacementWarning.WORK_PROFILE -> R.string.companion_warning_work_profile
    ReplacementWarning.DISABLED_APP -> R.string.companion_warning_disabled_app
    ReplacementWarning.INSTALLER_UNKNOWN -> R.string.companion_warning_installer_unknown
}

private fun CompanionError.messageRes(): Int = when (this) {
    CompanionError.NO_CANDIDATE -> R.string.companion_error_no_candidate
    CompanionError.INSTALLED_LOOKUP_FAILED -> R.string.companion_error_lookup
    CompanionError.DETECTION_FAILED -> R.string.companion_error_detection
    CompanionError.STORE_LOOKUP_FAILED -> R.string.companion_error_store
    CompanionError.PREFERENCE_READ_FAILED -> R.string.companion_error_pref_read
    CompanionError.PREFERENCE_WRITE_FAILED -> R.string.companion_error_pref_write
}
