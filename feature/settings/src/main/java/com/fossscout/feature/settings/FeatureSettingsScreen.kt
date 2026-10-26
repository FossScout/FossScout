package com.fossscout.feature.settings

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureState

@Composable
fun FeatureSettingsScreen(
    uiState: FeatureSettingsUiState,
    onToggle: (FeatureId, Boolean) -> Unit,
    onSettingValueChange: (FeatureId, String, FeatureSettingValue) -> Unit,
    onResetFeature: (FeatureId) -> Unit,
    onResetAll: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenBackup: (() -> Unit)? = null,
    onOpenContribution: (() -> Unit)? = null,
) {
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(stringResource(R.string.settings_reset_all_title)) },
            text = { Text(stringResource(R.string.settings_reset_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmation = false
                        onResetAll()
                    },
                ) {
                    Text(stringResource(R.string.settings_reset_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

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
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showResetConfirmation = true }) {
                Text(stringResource(R.string.settings_reset_all))
            }
        }

        uiState.error?.let { error ->
            FeatureSettingsErrorBanner(
                message = stringResource(error.messageRes()),
                onDismiss = onDismissError,
            )
        }

        if (uiState.isLoading) {
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
                    text = stringResource(R.string.settings_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onOpenBackup != null) {
                    item(key = "backup_entry") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenBackup),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_backup_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(R.string.settings_backup_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (onOpenContribution != null) {
                    item(key = "contribution_entry") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenContribution),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_contribution_title,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.settings_contribution_body,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (uiState.basicItems.isNotEmpty()) {
                    item(key = "header_basic") {
                        SettingsSectionHeader(stringResource(R.string.settings_section_basic))
                    }
                    items(
                        items = uiState.basicItems,
                        key = { item -> "basic_${item.state.descriptor.id.name}" },
                    ) { item ->
                        FeatureSettingsRow(
                            item = item,
                            onToggle = onToggle,
                            onSettingValueChange = onSettingValueChange,
                            onResetFeature = onResetFeature,
                        )
                    }
                }

                if (uiState.advancedItems.isNotEmpty()) {
                    item(key = "advanced_toggle") {
                        TextButton(
                            onClick = onToggleAdvanced,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (uiState.showAdvanced) {
                                        R.string.settings_hide_advanced
                                    } else {
                                        R.string.settings_show_advanced
                                    },
                                ),
                            )
                        }
                    }
                    if (uiState.showAdvanced) {
                        item(key = "header_advanced") {
                            SettingsSectionHeader(
                                stringResource(R.string.settings_section_advanced),
                            )
                        }
                        items(
                            items = uiState.advancedItems,
                            key = { item -> "advanced_${item.state.descriptor.id.name}" },
                        ) { item ->
                            FeatureSettingsRow(
                                item = item,
                                onToggle = onToggle,
                                onSettingValueChange = onSettingValueChange,
                                onResetFeature = onResetFeature,
                            )
                        }
                    }
                }

                if (uiState.unavailableStates.isNotEmpty()) {
                    item(key = "header_unavailable") {
                        SettingsSectionHeader(
                            stringResource(R.string.settings_unavailable_section),
                        )
                    }
                    items(
                        items = uiState.unavailableStates,
                        key = { state -> "unavailable_${state.descriptor.id.name}" },
                    ) { state ->
                        UnavailableFeatureRow(state = state)
                    }
                }
            }
        }
    }
}

@StringRes
private fun FeatureSettingsError.messageRes(): Int = when (this) {
    FeatureSettingsError.READ_FAILED -> R.string.settings_error_read
    FeatureSettingsError.WRITE_FAILED -> R.string.settings_error_write
}

@Composable
private fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FeatureSettingsErrorBanner(
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
                Text(stringResource(R.string.settings_dismiss))
            }
        }
    }
}

@Composable
private fun FeatureSettingsRow(
    item: FeatureSettingsItem,
    onToggle: (FeatureId, Boolean) -> Unit,
    onSettingValueChange: (FeatureId, String, FeatureSettingValue) -> Unit,
    onResetFeature: (FeatureId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = item.state
    val title = stringResource(state.descriptor.id.titleRes())

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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.descriptor.id == FeatureId.SETTINGS) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_always_available),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (item.isModified) {
                    val resetDescription =
                        stringResource(R.string.settings_reset_feature, title)
                    TextButton(
                        onClick = { onResetFeature(state.descriptor.id) },
                        modifier = Modifier.semantics {
                            contentDescription = resetDescription
                        },
                    ) {
                        Text(stringResource(R.string.settings_reset))
                    }
                }

                if (item.showToggle) {
                    val toggleDescription =
                        stringResource(R.string.settings_toggle_feature, title)
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { enabled ->
                            onToggle(state.descriptor.id, enabled)
                        },
                        modifier = Modifier.semantics {
                            contentDescription = toggleDescription
                        },
                    )
                }
            }

            item.settings.forEach { settingState ->
                Spacer(modifier = Modifier.height(8.dp))
                FeatureSettingRow(
                    featureId = state.descriptor.id,
                    settingState = settingState,
                    onValueChange = onSettingValueChange,
                )
            }
        }
    }
}

@Composable
private fun FeatureSettingRow(
    featureId: FeatureId,
    settingState: FeatureSettingValueState,
    onValueChange: (FeatureId, String, FeatureSettingValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val setting = settingState.setting
    val title = stringResource(setting.titleRes(featureId))

    when (val value = settingState.value) {
        is FeatureSettingValue.BooleanValue -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = value.value,
                    onCheckedChange = { checked ->
                        onValueChange(
                            featureId,
                            setting.key,
                            FeatureSettingValue.BooleanValue(checked),
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = title
                    },
                )
            }
        }

        is FeatureSettingValue.TextValue -> {
            var draft by remember(setting.key, value.value) {
                mutableStateOf(value.value)
            }
            val draftValue = FeatureSettingValue.TextValue(draft)
            val valid = setting.validate(draftValue)

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(title) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.settings_setting_invalid)) }
                    } else {
                        null
                    },
                )
                TextButton(
                    onClick = {
                        onValueChange(featureId, setting.key, draftValue)
                    },
                    enabled = valid && draft != value.value,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.settings_setting_save))
                }
            }
        }

        is FeatureSettingValue.IntegerValue -> {
            var draft by remember(setting.key, value.value) {
                mutableStateOf(value.value.toString())
            }
            val parsed = draft.toIntOrNull()
            val draftValue = parsed?.let(FeatureSettingValue::IntegerValue)
            val valid = draftValue != null && setting.validate(draftValue)

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(title) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.settings_setting_invalid)) }
                    } else {
                        null
                    },
                )
                TextButton(
                    onClick = {
                        draftValue?.let {
                            onValueChange(featureId, setting.key, it)
                        }
                    },
                    enabled = valid && parsed != value.value,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.settings_setting_save))
                }
            }
        }
    }
}

@Composable
private fun UnavailableFeatureRow(
    state: FeatureState,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(state.descriptor.id.titleRes())
    val missingCapabilities = state.missingCapabilities
        .sortedBy { it.name }
        .map { stringResource(it.labelRes()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.settings_unavailable_in_build),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            if (missingCapabilities.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.settings_missing_capabilities,
                        missingCapabilities.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
