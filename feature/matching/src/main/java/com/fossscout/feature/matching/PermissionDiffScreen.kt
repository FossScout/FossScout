package com.fossscout.feature.matching

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.matching.PermissionCategory
import com.fossscout.core.matching.PermissionComparison
import com.fossscout.core.matching.PermissionDetail
import com.fossscout.core.matching.PermissionDiff
import com.fossscout.core.matching.PermissionDiffStatus
import com.fossscout.core.matching.PermissionRelationship
import com.fossscout.core.matching.PermissionSet
import com.fossscout.core.matching.TrackerConfidence
import com.fossscout.core.matching.TrackerDatasetInfo
import com.fossscout.core.matching.TrackerMatch
import com.fossscout.core.matching.TrackerScanResult
import com.fossscout.core.matching.TrackerScanStatus

@Composable
fun PermissionDiffScreen(
    comparison: PermissionComparison,
    onBack: () -> Unit,
    onOpenSource: (String) -> Unit,
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
                Text(stringResource(R.string.permission_diff_back))
            }
            Text(
                text = stringResource(R.string.permission_diff_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.permission_diff_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "apps") {
                AppComparisonHeader(comparison)
            }
            item(key = "summary") {
                PermissionSummary(comparison)
            }
            comparison.diff?.let { diff ->
                if (diff.installedOnly.isNotEmpty()) {
                    item(key = "installed_only_header") {
                        PermissionSectionHeader(
                            title = stringResource(R.string.permission_diff_removed_title),
                            body = stringResource(R.string.permission_diff_removed_body),
                            count = diff.installedOnly.size,
                        )
                    }
                    items(
                        items = diff.installedOnly,
                        key = { permission -> "installed_only_${permission.name}" },
                    ) { permission ->
                        PermissionRow(
                            permission = permission,
                            status = PermissionDiffStatus.INSTALLED_ONLY,
                        )
                    }
                }
                if (diff.alternativeOnly.isNotEmpty()) {
                    item(key = "alternative_only_header") {
                        PermissionSectionHeader(
                            title = stringResource(R.string.permission_diff_added_title),
                            body = stringResource(R.string.permission_diff_added_body),
                            count = diff.alternativeOnly.size,
                        )
                    }
                    items(
                        items = diff.alternativeOnly,
                        key = { permission -> "alternative_only_${permission.name}" },
                    ) { permission ->
                        PermissionRow(
                            permission = permission,
                            status = PermissionDiffStatus.ALTERNATIVE_ONLY,
                        )
                    }
                }
                if (diff.shared.isNotEmpty()) {
                    item(key = "shared_header") {
                        PermissionSectionHeader(
                            title = stringResource(R.string.permission_diff_shared_title),
                            body = stringResource(R.string.permission_diff_shared_body),
                            count = diff.shared.size,
                        )
                    }
                    items(
                        items = diff.shared,
                        key = { permission -> "shared_${permission.name}" },
                    ) { permission ->
                        PermissionRow(
                            permission = permission,
                            status = PermissionDiffStatus.SHARED,
                        )
                    }
                }
                if (diff.isEmpty()) {
                    item(key = "no_permissions") {
                        EmptyPermissionsCard()
                    }
                }
            } ?: run {
                if (comparison.installed.permissions.isEmpty()) {
                    item(key = "installed_empty") {
                        InstalledEmptyCard()
                    }
                } else {
                    item(key = "installed_permissions_header") {
                        PermissionSectionHeader(
                            title = stringResource(R.string.permission_diff_installed_title),
                            body = stringResource(R.string.permission_diff_installed_body),
                            count = comparison.installed.permissions.size,
                        )
                    }
                    items(
                        items = comparison.installed.permissions,
                        key = { permission -> "installed_${permission.name}" },
                    ) { permission ->
                        PermissionRow(permission = permission)
                    }
                }
            }
            item(key = "tracker") {
                TrackerCard(comparison, onOpenSource)
            }
            item(key = "sources") {
                PermissionSourcesCard(comparison, onOpenSource)
            }
        }
    }
}

@Composable
private fun AppComparisonHeader(comparison: PermissionComparison) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppCard(
            title = stringResource(R.string.permission_diff_installed_app),
            app = comparison.installed,
            modifier = Modifier.weight(1f),
        )
        AppCard(
            title = stringResource(R.string.permission_diff_alternative_app),
            app = comparison.alternative,
            relationship = comparison.relationship,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AppCard(
    title: String,
    app: PermissionSet,
    modifier: Modifier = Modifier,
    relationship: PermissionRelationship? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            app.versionName?.let { version ->
                Text(
                    text = stringResource(R.string.permission_diff_version, version),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (relationship != null) {
                Text(
                    text = stringResource(relationship.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PermissionSummary(comparison: PermissionComparison) {
    val diff = comparison.diff
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (diff == null) {
                Text(
                    text = stringResource(R.string.permission_diff_unknown_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.permission_diff_unknown_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Text(
                    text = stringResource(R.string.permission_diff_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryCount(
                        count = diff.alternativeOnly.size,
                        label = stringResource(R.string.permission_diff_added_count),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCount(
                        count = diff.installedOnly.size,
                        label = stringResource(R.string.permission_diff_removed_count),
                        modifier = Modifier.weight(1f),
                    )
                    SummaryCount(
                        count = diff.shared.size,
                        label = stringResource(R.string.permission_diff_shared_count),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCount(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PermissionSectionHeader(
    title: String,
    body: String,
    count: Int,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionRow(
    permission: PermissionDetail,
    status: PermissionDiffStatus? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = permission.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                status?.let { status ->
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusPill(status)
                }
            }
            Text(
                text = stringResource(permission.category.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            permission.explanation?.let { explanation ->
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = permission.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(status: PermissionDiffStatus) {
    val colors = when (status) {
        PermissionDiffStatus.ALTERNATIVE_ONLY -> MaterialTheme.colorScheme.primaryContainer
        PermissionDiffStatus.INSTALLED_ONLY -> MaterialTheme.colorScheme.secondaryContainer
        PermissionDiffStatus.SHARED -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors,
    ) {
        Text(
            text = stringResource(status.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyPermissionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_diff_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.permission_diff_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstalledEmptyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_diff_installed_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.permission_diff_installed_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackerCard(
    comparison: PermissionComparison,
    onOpenSource: (String) -> Unit,
) {
    val tracker = comparison.tracker
    val dataset = tracker?.dataset
    val installed = tracker?.installed
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_diff_tracker_title),
                style = MaterialTheme.typography.titleMedium,
            )
            dataset?.let { info ->
                TrackerDatasetSummary(info, onOpenSource)
            }
            when {
                comparison.trackerScanInProgress -> {
                    Text(
                        text = stringResource(R.string.permission_diff_tracker_scanning),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                dataset == null || installed == null -> {
                    Text(
                        text = stringResource(R.string.permission_diff_tracker_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    TrackerScanSummary(
                        dataset = dataset,
                        installed = installed,
                        onOpenSource = onOpenSource,
                    )
                }
            }
            Text(
                text = stringResource(R.string.permission_diff_tracker_limitation),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackerDatasetSummary(
    dataset: TrackerDatasetInfo,
    onOpenSource: (String) -> Unit,
) {
    Text(
        text = stringResource(
            R.string.permission_diff_tracker_dataset_line,
            dataset.name,
            dataset.version,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        text = stringResource(
            R.string.permission_diff_tracker_license_line,
            dataset.licenseName,
        ),
        style = MaterialTheme.typography.bodySmall,
    )
    dataset.attribution?.let { attribution ->
        Text(
            text = attribution,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onOpenSource(dataset.sourceUrl) }) {
            Text(stringResource(R.string.permission_diff_tracker_open_source))
        }
        TextButton(onClick = { onOpenSource(dataset.licenseUrl) }) {
            Text(stringResource(R.string.permission_diff_tracker_open_license))
        }
    }
}

@Composable
private fun TrackerScanSummary(
    dataset: TrackerDatasetInfo,
    installed: TrackerScanResult,
    onOpenSource: (String) -> Unit,
) {
    when (installed.status) {
        TrackerScanStatus.UNAVAILABLE -> {
            Text(
                text = stringResource(R.string.permission_diff_tracker_scan_unavailable),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TrackerScanStatus.COMPLETE,
        TrackerScanStatus.TRUNCATED -> {
            if (installed.matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.permission_diff_tracker_no_matches),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.permission_diff_tracker_matches_title,
                        installed.matches.size,
                        installed.matches.size,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                installed.matches.forEach { match ->
                    TrackerMatchRow(match, dataset, onOpenSource)
                }
            }
            if (installed.status == TrackerScanStatus.TRUNCATED) {
                Text(
                    text = stringResource(R.string.permission_diff_tracker_scan_incomplete),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (installed.scannedFiles > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.permission_diff_tracker_scanned_files,
                        installed.scannedFiles,
                        installed.scannedFiles,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.permission_diff_tracker_alternative_not_scanned),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TrackerMatchRow(
    match: TrackerMatch,
    dataset: TrackerDatasetInfo,
    onOpenSource: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                TrackerConfidencePill(match.confidence)
            }
            if (match.categories.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.permission_diff_tracker_categories,
                        match.categories.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            match.matchedSignatures.forEach { signature ->
                Text(
                    text = stringResource(
                        R.string.permission_diff_tracker_match_signature,
                        signature,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.permission_diff_tracker_match_source,
                    dataset.name,
                    dataset.version,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            match.website?.let { website ->
                TextButton(onClick = { onOpenSource(website) }) {
                    Text(stringResource(R.string.permission_diff_tracker_website))
                }
            }
        }
    }
}

@Composable
private fun TrackerConfidencePill(confidence: TrackerConfidence) {
    val color = when (confidence) {
        TrackerConfidence.KNOWN -> MaterialTheme.colorScheme.primaryContainer
        TrackerConfidence.CANDIDATE -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color,
    ) {
        Text(
            text = stringResource(confidence.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PermissionSourcesCard(
    comparison: PermissionComparison,
    onOpenSource: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_diff_sources_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.permission_diff_installed_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.permission_diff_alternative_source,
                    comparison.alternative.storeName.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.permission_diff_cached_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.permission_diff_no_upload),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            comparison.alternativeSourceUrl?.let { source ->
                TextButton(onClick = { onOpenSource(source) }) {
                    Text(stringResource(R.string.permission_diff_open_source))
                }
            }
        }
    }
}

private fun PermissionDiff.isEmpty(): Boolean =
    installedOnly.isEmpty() && alternativeOnly.isEmpty() && shared.isEmpty()

private fun PermissionCategory.labelRes(): Int = when (this) {
    PermissionCategory.DANGEROUS -> R.string.permission_diff_category_dangerous
    PermissionCategory.SPECIAL -> R.string.permission_diff_category_special
    PermissionCategory.SIGNATURE -> R.string.permission_diff_category_signature
    PermissionCategory.NORMAL -> R.string.permission_diff_category_normal
    PermissionCategory.OTHER -> R.string.permission_diff_category_other
}

private fun PermissionRelationship.labelRes(): Int = when (this) {
    PermissionRelationship.SAME_APP -> R.string.permission_diff_relationship_same
    PermissionRelationship.VARIANT -> R.string.permission_diff_relationship_variant
    PermissionRelationship.DIFFERENT_APP -> R.string.permission_diff_relationship_different
    PermissionRelationship.UNKNOWN -> R.string.permission_diff_relationship_unknown
}

private fun PermissionDiffStatus.labelRes(): Int = when (this) {
    PermissionDiffStatus.ALTERNATIVE_ONLY -> R.string.permission_diff_status_added
    PermissionDiffStatus.INSTALLED_ONLY -> R.string.permission_diff_status_removed
    PermissionDiffStatus.SHARED -> R.string.permission_diff_status_shared
}

private fun TrackerConfidence.labelRes(): Int = when (this) {
    TrackerConfidence.KNOWN -> R.string.permission_diff_tracker_confidence_known
    TrackerConfidence.CANDIDATE -> R.string.permission_diff_tracker_confidence_candidate
}
