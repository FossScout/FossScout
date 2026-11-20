package com.fossscout.feature.matching

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fossscout.core.matching.TrustBadge
import com.fossscout.core.matching.TrustBadgeKind
import com.fossscout.core.matching.TrustBadgeTone

@Composable
internal fun TrustBadgeRow(
    badges: List<TrustBadge>,
    onBadgeClick: (TrustBadge) -> Unit,
    modifier: Modifier = Modifier,
    showCacheAge: Boolean = false,
) {
    if (badges.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            badges.forEach { badge ->
                TrustBadgeChip(badge = badge, onClick = { onBadgeClick(badge) })
            }
        }
        if (showCacheAge) {
            val cacheFetchedAt = badges.mapNotNull(TrustBadge::cacheFetchedAtEpochMillis)
                .maxOrNull()
            if (cacheFetchedAt != null) {
                Text(
                    text = stringResource(
                        R.string.store_search_detail_cache,
                        cacheLabel(cacheFetchedAt),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TrustBadgeChip(badge: TrustBadge, onClick: () -> Unit) {
    val label = badge.label()
    val explanation = badge.explanation()
    val contentDescription = stringResource(
        R.string.trust_badge_content_description,
        label,
        explanation,
    )
    val (container, content) = badge.tone.colors()
    Surface(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun TrustBadgeExplanationDialog(
    badge: TrustBadge,
    onDismiss: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    val sourceUrl = badge.sourceUrl?.takeIf(String::isNotBlank)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trust_badge_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = badge.label(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = badge.explanation(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                badge.cacheFetchedAtEpochMillis?.let { fetchedAt ->
                    Text(
                        text = stringResource(
                            R.string.store_search_detail_cache,
                            cacheLabel(fetchedAt),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                badge.reproducibility?.let { check ->
                    Text(
                        text = stringResource(
                            R.string.trust_badge_checked,
                            relativeTime(check.checkedAtEpochMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sourceUrl?.let { url ->
                    Text(
                        text = stringResource(R.string.trust_badge_source, url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_search_close))
            }
        },
        dismissButton = sourceUrl?.let { url ->
            {
                TextButton(onClick = { onOpenSource(url) }) {
                    Text(stringResource(R.string.trust_badge_source_link))
                }
            }
        },
    )
}

@Composable
private fun TrustBadge.label(): String = when (kind) {
    TrustBadgeKind.INDEX_VERIFIED -> stringResource(R.string.trust_badge_index_verified)
    TrustBadgeKind.INDEX_UNVERIFIED -> stringResource(R.string.trust_badge_index_unverified)
    TrustBadgeKind.INDEX_FAILED -> stringResource(R.string.trust_badge_index_failed)
    TrustBadgeKind.INDEX_NOT_CHECKED -> stringResource(R.string.trust_badge_index_not_checked)
    TrustBadgeKind.SOURCE_AND_LICENSE -> stringResource(R.string.trust_badge_source_license)
    TrustBadgeKind.SOURCE_OR_LICENSE_MISSING ->
        stringResource(R.string.trust_badge_source_license_missing)

    TrustBadgeKind.ANTI_FEATURE -> stringResource(R.string.trust_badge_anti_feature)
    TrustBadgeKind.SIGNER_CHANGED -> stringResource(R.string.trust_badge_signer_changed)
    TrustBadgeKind.STALE_METADATA -> stringResource(R.string.trust_badge_stale_metadata)
    TrustBadgeKind.REPRODUCIBLE -> stringResource(R.string.trust_badge_reproducible)
    TrustBadgeKind.REPRODUCIBILITY_NOT_VERIFIED ->
        stringResource(R.string.trust_badge_reproducibility_not_verified)

    TrustBadgeKind.REPRODUCIBILITY_FAILED ->
        stringResource(R.string.trust_badge_reproducibility_failed)

    TrustBadgeKind.LINK_ONLY -> stringResource(R.string.trust_badge_link_only)
}

@Composable
private fun TrustBadge.explanation(): String {
    val store = storeName ?: stringResource(R.string.store_search_unknown)
    return when (kind) {
        TrustBadgeKind.INDEX_VERIFIED ->
            stringResource(R.string.trust_badge_index_verified_explanation, store)

        TrustBadgeKind.INDEX_UNVERIFIED ->
            stringResource(R.string.trust_badge_index_unverified_explanation, store)

        TrustBadgeKind.INDEX_FAILED ->
            stringResource(R.string.trust_badge_index_failed_explanation, store)

        TrustBadgeKind.INDEX_NOT_CHECKED ->
            stringResource(R.string.trust_badge_index_not_checked_explanation, store)

        TrustBadgeKind.SOURCE_AND_LICENSE ->
            stringResource(R.string.trust_badge_source_license_explanation, store)

        TrustBadgeKind.SOURCE_OR_LICENSE_MISSING ->
            stringResource(R.string.trust_badge_source_license_missing_explanation, store)

        TrustBadgeKind.ANTI_FEATURE -> stringResource(
            R.string.trust_badge_anti_feature_explanation,
            store,
            antiFeatures.joinToString(),
        )

        TrustBadgeKind.SIGNER_CHANGED -> stringResource(
            R.string.trust_badge_signer_changed_explanation,
            store,
            previousSignerSha256.joinToString(),
            currentSignerSha256.joinToString(),
        )

        TrustBadgeKind.STALE_METADATA ->
            stringResource(R.string.trust_badge_stale_explanation, store)

        TrustBadgeKind.REPRODUCIBLE -> stringResource(
            R.string.trust_badge_reproducible_explanation,
            reproducibility?.versionLabel().orEmpty(),
        )

        TrustBadgeKind.REPRODUCIBILITY_FAILED -> stringResource(
            R.string.trust_badge_reproducibility_failed_explanation,
            reproducibility?.versionLabel().orEmpty(),
        )

        TrustBadgeKind.REPRODUCIBILITY_NOT_VERIFIED -> stringResource(
            R.string.trust_badge_reproducibility_not_verified_explanation,
            reproducibility?.versionLabel().orEmpty(),
        )

        TrustBadgeKind.LINK_ONLY ->
            stringResource(R.string.trust_badge_link_only_explanation, store)
    }
}

private fun com.fossscout.core.index.ReproducibilityCheck.versionLabel(): String =
    versionName?.takeIf(String::isNotBlank) ?: versionCode.toString()

@Composable
private fun TrustBadgeTone.colors(): Pair<Color, Color> = when (this) {
    TrustBadgeTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer to
        MaterialTheme.colorScheme.onPrimaryContainer

    TrustBadgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant to
        MaterialTheme.colorScheme.onSurfaceVariant

    TrustBadgeTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer to
        MaterialTheme.colorScheme.onTertiaryContainer

    TrustBadgeTone.NEGATIVE -> MaterialTheme.colorScheme.errorContainer to
        MaterialTheme.colorScheme.onErrorContainer
}

@Composable
internal fun cacheLabel(fetchedAtEpochMillis: Long?): String =
    if (fetchedAtEpochMillis == null) {
        stringResource(R.string.store_search_cache_never)
    } else {
        stringResource(
            R.string.store_search_cache_as_of,
            relativeTime(fetchedAtEpochMillis),
        )
    }

internal fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
