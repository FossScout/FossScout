package com.fossscout.feature.matching

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.matching.StoreSearchAvailabilityFilter
import com.fossscout.core.matching.StoreSearchGroup
import com.fossscout.core.matching.StoreSearchLinkOnly
import com.fossscout.core.matching.StoreSearchStoreHit
import com.fossscout.core.matching.StoreSearchTrustFilter
import com.fossscout.core.matching.StoreSearchUnavailable
import com.fossscout.core.matching.StoreSearchUnavailableReason
import com.fossscout.core.model.AppKey
import com.fossscout.core.store.LinkOnlyReason

@Composable
fun StoreSearchScreen(
    state: StoreSearchUiState,
    savedAppKeys: Set<AppKey>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleStore: (String) -> Unit,
    onTrustFilterChange: (StoreSearchTrustFilter) -> Unit,
    onAvailabilityFilterChange: (StoreSearchAvailabilityFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onDismissDetails: () -> Unit,
    onToggleSaved: (StoreSearchGroup) -> Unit,
    onOpenHomepage: (String) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    showFavoriteAction: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.store_search_back),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.store_search_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.store_search_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.store_search_query)) },
                    placeholder = { Text(stringResource(R.string.store_search_hint)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSearch,
                    enabled = !state.isLoading,
                ) {
                    Text(stringResource(R.string.store_search_action))
                }
            }

            if (state.storeOptions.isNotEmpty()) {
                SearchFilters(
                    state = state,
                    onToggleStore = onToggleStore,
                    onTrustFilterChange = onTrustFilterChange,
                    onAvailabilityFilterChange = onAvailabilityFilterChange,
                    onClearFilters = onClearFilters,
                )
            }
        }

        state.error?.let { error ->
            SearchErrorBanner(
                error = error,
                onDismiss = onDismissError,
            )
        }

        if (state.hasSearched && state.unavailableStores.isNotEmpty()) {
            SearchUnavailableBanner(stores = state.unavailableStores)
        }

        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.store_search_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            !state.hasSearched -> SearchIntro(modifier = Modifier.weight(1f))

            state.groups.isEmpty() && state.linkOnlyStores.isEmpty() -> SearchEmpty(
                noStores = state.storeOptions.isEmpty(),
                query = state.query.trim(),
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.searchedAtEpochMillis?.let { searchedAt ->
                    item(key = "search-as-of") {
                        Text(
                            text = stringResource(
                                R.string.store_search_results_as_of,
                                relativeTime(searchedAt),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = state.groups,
                    key = StoreSearchGroup::packageName,
                ) { group ->
                    SearchGroupCard(
                        group = group,
                        isSaved = AppKey.from(group.packageName) in savedAppKeys,
                        onOpenDetails = { onOpenDetails(group.packageName) },
                        onToggleSaved = { onToggleSaved(group) },
                        showFavoriteAction = showFavoriteAction,
                    )
                }
                if (state.linkOnlyStores.isNotEmpty()) {
                    item(key = "link-only-heading") {
                        Text(
                            text = stringResource(R.string.store_search_link_only_heading),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(
                        items = state.linkOnlyStores,
                        key = StoreSearchLinkOnly::storeId,
                    ) { store ->
                        LinkOnlyCard(
                            store = store,
                            onOpenHomepage = onOpenHomepage,
                        )
                    }
                }
            }
        }
    }

    state.detailGroup?.let { group ->
        SearchDetailsDialog(
            group = group,
            onDismiss = onDismissDetails,
        )
    }
}

@Composable
private fun SearchFilters(
    state: StoreSearchUiState,
    onToggleStore: (String) -> Unit,
    onTrustFilterChange: (StoreSearchTrustFilter) -> Unit,
    onAvailabilityFilterChange: (StoreSearchAvailabilityFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterLabel(stringResource(R.string.store_search_filter_stores))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.storeOptions.forEach { option ->
                FilterChip(
                    selected = option.storeId in state.selectedStoreIds,
                    onClick = { onToggleStore(option.storeId) },
                    label = { Text(option.storeName) },
                )
            }
        }

        FilterLabel(stringResource(R.string.store_search_filter_trust))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreSearchTrustFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.trustFilter == filter,
                    onClick = { onTrustFilterChange(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
        }

        FilterLabel(stringResource(R.string.store_search_filter_availability))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreSearchAvailabilityFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.availabilityFilter == filter,
                    onClick = { onAvailabilityFilterChange(filter) },
                    label = { Text(stringResource(filter.labelRes())) },
                )
            }
        }

        if (state.hasActiveFilters) {
            TextButton(onClick = onClearFilters) {
                Text(stringResource(R.string.store_search_clear_filters))
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchErrorBanner(
    error: StoreSearchError,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_search_dismiss))
            }
        }
    }
}

@Composable
private fun SearchUnavailableBanner(stores: List<StoreSearchUnavailable>) {
    val labels = stores.map { store ->
        store.storeName to stringResource(store.reason.labelRes())
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(
                R.string.store_search_unavailable,
                labels.joinToString { (name, reason) -> "$name ($reason)" },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SearchIntro(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.store_search_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchEmpty(
    noStores: Boolean,
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (noStores) {
                stringResource(R.string.store_search_no_stores)
            } else {
                stringResource(R.string.store_search_empty, query)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchGroupCard(
    group: StoreSearchGroup,
    isSaved: Boolean,
    onOpenDetails: () -> Unit,
    onToggleSaved: () -> Unit,
    showFavoriteAction: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = group.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showFavoriteAction) {
                    IconButton(onClick = onToggleSaved) {
                        Icon(
                            imageVector = if (isSaved) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = stringResource(
                                if (isSaved) {
                                    R.string.store_search_favorite_remove
                                } else {
                                    R.string.store_search_favorite_add
                                },
                                group.name,
                            ),
                            tint = if (isSaved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            if (group.summary.isNotBlank()) {
                Text(
                    text = group.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            group.stores.forEach { hit ->
                Text(
                    text = stringResource(
                        R.string.store_search_store_line,
                        hit.storeName,
                        hit.versionName ?: stringResource(
                            R.string.store_search_version_unknown,
                        ),
                        cacheLabel(hit.fetchedAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrustBadge(
                    text = stringResource(
                        if (group.isIndexVerified) {
                            R.string.store_search_index_verified
                        } else {
                            R.string.store_search_index_unverified
                        },
                    ),
                    isWarning = !group.isIndexVerified,
                )
                TrustBadge(
                    text = stringResource(
                        if (group.hasOpenSourceMetadata) {
                            R.string.store_search_source_license
                        } else {
                            R.string.store_search_source_license_missing
                        },
                    ),
                    isWarning = !group.hasOpenSourceMetadata,
                )
            }

            if (group.antiFeatures.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.store_search_anti_features,
                        group.antiFeatures.joinToString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TrustBadge(text: String, isWarning: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isWarning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isWarning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LinkOnlyCard(
    store: StoreSearchLinkOnly,
    onOpenHomepage: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.store_search_link_only_title, store.storeName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.store_search_link_only_body,
                    store.storeName,
                    stringResource(store.reason.labelRes()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onOpenHomepage(store.homepage) }) {
                Text(
                    stringResource(
                        R.string.store_search_link_only_action,
                        store.storeName,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SearchDetailsDialog(
    group: StoreSearchGroup,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(group.name) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.store_search_detail_package,
                        group.packageName,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (group.summary.isNotBlank()) {
                    Text(
                        text = group.summary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    text = stringResource(R.string.store_search_detail_stores),
                    style = MaterialTheme.typography.titleSmall,
                )
                group.stores.forEach { hit ->
                    StoreHitDetails(hit)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.store_search_close))
            }
        },
    )
}

@Composable
private fun StoreHitDetails(hit: StoreSearchStoreHit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = hit.storeName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = hit.versionCode?.let { code ->
                    stringResource(
                        R.string.store_search_detail_version,
                        hit.versionName ?: stringResource(
                            R.string.store_search_version_unknown,
                        ),
                        code,
                    )
                } ?: stringResource(
                    R.string.store_search_detail_version_name,
                    hit.versionName ?: stringResource(
                        R.string.store_search_version_unknown,
                    ),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.store_search_detail_license,
                    hit.license ?: stringResource(R.string.store_search_unknown),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.store_search_detail_source,
                    hit.sourceCode ?: stringResource(R.string.store_search_unknown),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = if (hit.antiFeatures.isEmpty()) {
                    stringResource(R.string.store_search_detail_no_anti)
                } else {
                    stringResource(
                        R.string.store_search_detail_anti,
                        hit.antiFeatures.joinToString(),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hit.antiFeatures.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                text = stringResource(
                    R.string.store_search_detail_cache,
                    cacheLabel(hit.fetchedAtEpochMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.store_search_detail_trust,
                    indexTrustLabel(hit.signatureStatus),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun cacheLabel(fetchedAtEpochMillis: Long?): String =
    if (fetchedAtEpochMillis == null) {
        stringResource(R.string.store_search_cache_never)
    } else {
        stringResource(
            R.string.store_search_cache_as_of,
            relativeTime(fetchedAtEpochMillis),
        )
    }

@Composable
private fun indexTrustLabel(status: SignatureStatus?): String = when (status) {
    SignatureStatus.VERIFIED -> stringResource(R.string.store_search_index_verified)
    SignatureStatus.UNVERIFIED -> stringResource(R.string.store_search_index_unverified)
    SignatureStatus.NOT_CHECKED,
    SignatureStatus.UNSIGNED,
    SignatureStatus.INVALID,
    null,
    -> stringResource(R.string.store_search_index_unknown)
}

private fun relativeTime(epochMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        epochMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

private fun StoreSearchTrustFilter.labelRes(): Int = when (this) {
    StoreSearchTrustFilter.ALL -> R.string.store_search_trust_all
    StoreSearchTrustFilter.VERIFIED_INDEX -> R.string.store_search_trust_verified
    StoreSearchTrustFilter.OPEN_SOURCE -> R.string.store_search_trust_open_source
    StoreSearchTrustFilter.NO_ANTI_FEATURES -> R.string.store_search_trust_no_anti
}

private fun StoreSearchAvailabilityFilter.labelRes(): Int = when (this) {
    StoreSearchAvailabilityFilter.ALL -> R.string.store_search_availability_all
    StoreSearchAvailabilityFilter.INDEXED -> R.string.store_search_availability_indexed
    StoreSearchAvailabilityFilter.LINK_ONLY -> R.string.store_search_availability_link_only
}

private fun LinkOnlyReason.labelRes(): Int = when (this) {
    LinkOnlyReason.ADAPTER_UNSUPPORTED ->
        R.string.store_search_link_only_reason_adapter
    LinkOnlyReason.MISSING_CATALOG_ENDPOINT ->
        R.string.store_search_link_only_reason_endpoint
}

private fun StoreSearchUnavailableReason.labelRes(): Int = when (this) {
    StoreSearchUnavailableReason.NEVER_SYNCED ->
        R.string.store_search_unavailable_never_synced
    StoreSearchUnavailableReason.INDEX_UNREADABLE ->
        R.string.store_search_unavailable_unreadable
}

private fun StoreSearchError.messageRes(): Int = when (this) {
    StoreSearchError.QUERY_REQUIRED -> R.string.store_search_error_query
    StoreSearchError.SEARCH_FAILED -> R.string.store_search_error_search
}
