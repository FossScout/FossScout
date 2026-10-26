package com.fossscout.feature.matching

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.AlternativeSuggestion
import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.MatchCandidate
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.matching.StoreCoverage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result/detail surface for F02, F03, and F06. Alternatives are only rendered
 * when the decision has no L1/L2 candidate; coverage details respect the
 * feature toggle.
 */
@Composable
fun MatchScreen(
    state: MatchUiState,
    onQueryChange: (String) -> Unit,
    onCheck: () -> Unit,
    onSelectCandidate: (InstalledApp) -> Unit,
    onOpenSource: (String) -> Unit,
    onHideAlternative: (String) -> Unit,
    onUndoHide: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenCompanion: ((MatchDecision) -> Unit)? = null,
    onReportWrongMatch: ((MatchDecision) -> Unit)? = null,
    showCoverage: Boolean = true,
    showAlternatives: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.match_back))
                }
            }
            Text(
                text = stringResource(R.string.match_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.match_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "query") {
                MatchQueryCard(
                    state = state,
                    onQueryChange = onQueryChange,
                    onCheck = onCheck,
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    MatchErrorBanner(
                        message = stringResource(error.messageRes()),
                        onDismiss = onDismissError,
                    )
                }
            }

            if (state.candidates.isNotEmpty()) {
                item(key = "candidates_header") {
                    Column {
                        Text(
                            text = stringResource(R.string.match_candidates_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.match_candidates_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = state.candidates,
                    key = { app -> "candidate_${app.packageName}" },
                ) { app ->
                    CandidateRow(app = app, onClick = { onSelectCandidate(app) })
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
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.match_checking),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                state.decision?.let { decision ->
                    item(key = "outcome") {
                        OutcomeCard(decision = decision, showCoverage = showCoverage)
                    }
                    if (onReportWrongMatch != null) {
                        item(key = "report_wrong_match") {
                            ReportWrongMatchCard(
                                onReport = { onReportWrongMatch(decision) },
                            )
                        }
                    }
                    decision.primary?.let { primary ->
                        item(key = "primary") {
                            PrimaryEvidenceCard(
                                candidate = primary,
                                onOpenSource = onOpenSource,
                            )
                        }
                        if (onOpenCompanion != null) {
                            item(key = "next_step") {
                                NextStepCard(
                                    onOpen = { onOpenCompanion(decision) },
                                )
                            }
                        }
                    }
                    if (decision.otherMatches.isNotEmpty()) {
                        item(key = "other_matches") {
                            OtherMatchesCard(
                                matches = decision.otherMatches,
                                onOpenSource = onOpenSource,
                            )
                        }
                    }
                    if (showCoverage) {
                        item(key = "coverage") {
                            CoverageCard(coverage = decision.coverage)
                        }
                    }
                    if (showAlternatives && state.visibleAlternatives.isNotEmpty()) {
                        item(key = "alternatives") {
                            AlternativesCard(
                                alternatives = state.visibleAlternatives,
                                onHide = onHideAlternative,
                                onOpenSource = onOpenSource,
                            )
                        }
                    }
                    state.lastHiddenAlternative?.let { hidden ->
                        if (showAlternatives) {
                            item(key = "undo_hidden") {
                                HiddenNotice(
                                    suggestion = hidden,
                                    onUndo = onUndoHide,
                                )
                            }
                        }
                    }
                }

                if (
                    state.decision == null &&
                    state.error == null &&
                    state.candidates.isEmpty()
                ) {
                    item(key = "empty") {
                        EmptyMatch()
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchQueryCard(
    state: MatchUiState,
    onQueryChange: (String) -> Unit,
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
                label = { Text(stringResource(R.string.match_query_label)) },
                placeholder = { Text(stringResource(R.string.match_query_hint)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCheck,
                enabled = state.query.isNotBlank() && !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isLoading) R.string.match_checking else R.string.match_check,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OutcomeCard(decision: MatchDecision, showCoverage: Boolean) {
    val container = when (decision.outcome) {
        MatchOutcome.MATCH -> if (decision.level == MatchLevel.L1) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }

        MatchOutcome.UNKNOWN -> MaterialTheme.colorScheme.secondaryContainer
        MatchOutcome.INCOMPLETE -> MaterialTheme.colorScheme.errorContainer
        MatchOutcome.NOT_FOUND,
        MatchOutcome.ALTERNATIVES,
        -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (decision.outcome) {
        MatchOutcome.MATCH -> if (decision.level == MatchLevel.L1) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onTertiaryContainer
        }

        MatchOutcome.UNKNOWN -> MaterialTheme.colorScheme.onSecondaryContainer
        MatchOutcome.INCOMPLETE -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelBadge(level = decision.level)
                if (showCoverage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.match_confidence,
                            stringResource(decision.confidence.labelRes()),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(decision.outcome.titleRes(decision.level)),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(decision.outcome.bodyRes(decision.level)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (decision.outcome != MatchOutcome.MATCH) {
                decision.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            when {
                decision.level == MatchLevel.L1 -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Badge(stringResource(R.string.match_same_app))
                }

                decision.level == MatchLevel.L2 -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Badge(stringResource(R.string.match_fork_variant))
                }

                decision.level == MatchLevel.L0 -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Badge(stringResource(R.string.match_metadata_missing))
                }
            }
        }
    }
}

@Composable
private fun NextStepCard(onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.match_next_step),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.match_next_step_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onOpen) {
                Text(stringResource(R.string.match_next_step_action))
            }
        }
    }
}

@Composable
private fun ReportWrongMatchCard(onReport: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.match_report_wrong_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.match_report_wrong_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReport) {
                Text(stringResource(R.string.match_report_wrong_action))
            }
        }
    }
}

@Composable
private fun PrimaryEvidenceCard(
    candidate: MatchCandidate,
    onOpenSource: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.match_evidence_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = candidate.displayName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.match_package, candidate.packageName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.match_store, candidate.storeName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    candidate.signatureStatus?.matchLabelRes()
                        ?: R.string.match_index_trust_unknown,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = when (candidate.signatureStatus) {
                    SignatureStatus.VERIFIED -> MaterialTheme.colorScheme.primary
                    SignatureStatus.INVALID,
                    SignatureStatus.UNSIGNED,
                    -> MaterialTheme.colorScheme.error

                    SignatureStatus.UNVERIFIED,
                    SignatureStatus.NOT_CHECKED,
                    null,
                    -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            candidate.versionName?.let { version ->
                Text(
                    text = stringResource(R.string.match_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            candidate.versionCode?.let { versionCode ->
                Text(
                    text = stringResource(R.string.match_version_code, versionCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = candidate.license?.let {
                    stringResource(R.string.match_license, it)
                } ?: stringResource(R.string.match_license_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = candidate.sourceUrl?.let {
                    stringResource(R.string.match_source, it)
                } ?: stringResource(R.string.match_source_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (candidate.signerSha256.isEmpty()) {
                    stringResource(R.string.match_signer_unknown)
                } else {
                    stringResource(
                        R.string.match_signer,
                        candidate.signerSha256.joinToString(", "),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.match_apk_not_verified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            candidate.sourceUrl?.let { source ->
                TextButton(onClick = { onOpenSource(source) }) {
                    Text(stringResource(R.string.match_open_source))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.match_why_title),
                style = MaterialTheme.typography.titleSmall,
            )
            candidate.reasons.forEach { reason ->
                Text(
                    text = "• $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun OtherMatchesCard(
    matches: List<MatchCandidate>,
    onOpenSource: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.match_other_matches),
                style = MaterialTheme.typography.titleMedium,
            )
            matches.forEach { match ->
                Text(
                    text = stringResource(
                        R.string.match_other_match_line,
                        match.storeName,
                        match.packageName,
                        stringResource(match.level.labelRes()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(
                        match.signatureStatus?.matchLabelRes()
                            ?: R.string.match_index_trust_unknown,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val license = match.license
                if (license != null) {
                    Text(
                        text = stringResource(R.string.match_license, license),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.match_license_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                match.sourceUrl?.let { source ->
                    Text(
                        text = stringResource(R.string.match_source, source),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onOpenSource(source) }) {
                        Text(stringResource(R.string.match_open_source))
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverageCard(coverage: CoverageSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.match_coverage_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Badge(
                    stringResource(
                        if (coverage.complete) {
                            R.string.match_coverage_complete
                        } else {
                            R.string.match_coverage_incomplete
                        },
                    ),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.match_coverage_summary,
                    coverage.required.size,
                    coverage.checkedCount,
                    coverage.required.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.match_coverage_window,
                    (coverage.freshnessWindowMillis / 3_600_000L).toInt(),
                    coverage.freshnessWindowMillis / 3_600_000L,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            coverage.stores.forEach { store ->
                CoverageRow(store)
            }
        }
    }
}

@Composable
private fun CoverageRow(store: StoreCoverage) {
    val lastSync = store.lastSyncEpochMillis
    val lastSyncText = if (lastSync != null) {
        stringResource(
            R.string.match_coverage_last_sync,
            formatTimestamp(lastSync),
        )
    } else {
        stringResource(R.string.match_coverage_never_synced)
    }
    val packageText = store.packageCount?.let { count ->
        pluralStringResource(R.plurals.match_coverage_packages, count, count)
    }
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = store.storeName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Badge(stringResource(store.status.labelRes()))
            if (store.reason != com.fossscout.core.matching.CoverageReason.NONE) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(store.reason.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = listOfNotNull(lastSyncText, packageText).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        store.signatureStatus?.let { signature ->
            Text(
                text = stringResource(
                    R.string.match_coverage_index,
                    stringResource(signature.matchLabelRes()),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        store.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlternativesCard(
    alternatives: List<AlternativeSuggestion>,
    onHide: (String) -> Unit,
    onOpenSource: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.match_alternatives_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.match_alternatives_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            alternatives.forEachIndexed { index, suggestion ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                AlternativeRow(
                    suggestion = suggestion,
                    onHide = { onHide(suggestion.packageName) },
                    onOpenSource = onOpenSource,
                )
            }
        }
    }
}

@Composable
private fun AlternativeRow(
    suggestion: AlternativeSuggestion,
    onHide: () -> Unit,
    onOpenSource: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = suggestion.displayName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Badge(stringResource(R.string.match_different_app))
        }
        Text(
            text = stringResource(R.string.match_package, suggestion.packageName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.match_store, suggestion.storeName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = suggestion.license?.let {
                stringResource(R.string.match_license, it)
            } ?: stringResource(R.string.match_license_unknown),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        suggestion.reasons.forEach { reason ->
            Text(
                text = "• $reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.match_alternative_score, suggestion.score),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            suggestion.sourceUrl?.let { source ->
                TextButton(onClick = { onOpenSource(source) }) {
                    Text(stringResource(R.string.match_open_source))
                }
            }
            TextButton(onClick = onHide) {
                Text(stringResource(R.string.match_hide_alternative, suggestion.displayName))
            }
        }
    }
}

@Composable
private fun HiddenNotice(
    suggestion: AlternativeSuggestion,
    onUndo: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.match_hidden_notice, suggestion.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.match_undo_hide))
            }
        }
    }
}

@Composable
private fun CandidateRow(app: InstalledApp, onClick: () -> Unit) {
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
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LevelBadge(level: MatchLevel) {
    val container = when (level) {
        MatchLevel.L1 -> MaterialTheme.colorScheme.primary
        MatchLevel.L2 -> MaterialTheme.colorScheme.tertiary
        MatchLevel.L3 -> MaterialTheme.colorScheme.secondary
        MatchLevel.L0 -> MaterialTheme.colorScheme.error
    }
    val content = when (level) {
        MatchLevel.L1 -> MaterialTheme.colorScheme.onPrimary
        MatchLevel.L2 -> MaterialTheme.colorScheme.onTertiary
        MatchLevel.L3 -> MaterialTheme.colorScheme.onSecondary
        MatchLevel.L0 -> MaterialTheme.colorScheme.onError
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = stringResource(level.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Badge(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MatchErrorBanner(message: String, onDismiss: () -> Unit) {
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
                Text(stringResource(R.string.match_dismiss))
            }
        }
    }
}

@Composable
private fun EmptyMatch() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.match_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.match_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun MatchError.messageRes(): Int = when (this) {
    MatchError.QUERY_REQUIRED -> R.string.match_error_query
    MatchError.MATCH_FAILED -> R.string.match_error_match
    MatchError.SEARCH_FAILED -> R.string.match_error_search
}

private fun SignatureStatus.matchLabelRes(): Int = when (this) {
    SignatureStatus.VERIFIED -> R.string.match_index_trust_verified
    SignatureStatus.UNVERIFIED -> R.string.match_index_trust_unverified
    SignatureStatus.INVALID,
    SignatureStatus.UNSIGNED,
    -> R.string.match_index_trust_failed

    SignatureStatus.NOT_CHECKED -> R.string.match_index_trust_not_checked
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
