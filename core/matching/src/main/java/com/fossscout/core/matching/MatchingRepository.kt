package com.fossscout.core.matching

import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.ClassificationResult
import com.fossscout.core.inventory.ClassificationSource
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/**
 * Orchestrates matching over the current store registry and verified snapshots.
 *
 * No network request is made. Catalogs come from the last cached snapshot, so a
 * match can be produced offline. Each store read failure becomes a coverage gap
 * instead of an empty result.
 */
class MatchingRepository(
    private val storeEntries: Flow<List<StoreEntry>>,
    private val snapshots: Flow<Map<String, StoreSnapshot>>,
    private val catalog: StoreCatalog,
    private val now: () -> Long = System::currentTimeMillis,
    private val freshnessWindowMillis: Long = DEFAULT_MATCH_FRESHNESS_MILLIS,
    private val maxAlternatives: Int = DEFAULT_MAX_ALTERNATIVES,
    private val overrides: Flow<List<MatchOverride>> = flowOf(emptyList()),
) : ClassificationSource {

    suspend fun match(
        query: MatchQuery,
        includeAlternatives: Boolean = true,
    ): MatchDecision {
        val timestamp = now()
        val stores = loadStores(timestamp)
        val decision = MatchEngine.evaluate(
            query = query,
            stores = stores,
            nowEpochMillis = timestamp,
            freshnessWindowMillis = freshnessWindowMillis,
            maxAlternatives = if (includeAlternatives) maxAlternatives else 0,
        )
        val correction = overrides.first()
            .firstOrNull { it.installedAppKey.value == query.appKey }
            ?: return decision
        val targetAvailable = correction.target?.let { target ->
            stores.any { store ->
                store.contributesCatalogResults &&
                    store.catalog?.byPackage?.containsKey(target.appKey.value) == true
            }
        }
        return decision.copy(
            correction = correction,
            correctionTargetAvailable = targetAvailable,
        )
    }

    /**
     * Searches the cached catalogs for a correction target. Results are ordered
     * by package/name relevance and never trigger a network request.
     */
    suspend fun searchCatalog(query: String, limit: Int = 10): List<CatalogSearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty() || limit <= 0) return emptyList()
        return loadStores(now())
            .filter(MatchStoreInput::contributesCatalogResults)
            .flatMap { store ->
                store.catalog?.entries.orEmpty().mapNotNull { entry ->
                    val packageName = entry.packageName.lowercase()
                    val name = entry.name.lowercase()
                    val rank = when {
                        packageName == needle -> 0
                        packageName.startsWith(needle) -> 1
                        name.startsWith(needle) -> 2
                        name.contains(needle) -> 3
                        packageName.contains(needle) -> 4
                        else -> null
                    } ?: return@mapNotNull null
                    CatalogSearchResult(
                        entry = entry,
                        storeId = store.storeId,
                        storeName = store.storeName,
                    ) to rank
                }
            }
            .sortedWith(
                compareBy<Pair<CatalogSearchResult, Int>> { it.second }
                    .thenBy { it.first.entry.name.lowercase() }
                    .thenBy { it.first.storeName.lowercase() },
            )
            .map { it.first }
            .take(limit)
    }

    private suspend fun loadStores(timestamp: Long): List<MatchStoreInput> {
        val entries = storeEntries.first()
        val snapshotMap = snapshots.first()
        return entries.map { entry ->
            val storeId = entry.descriptor.id.value
            val snapshot = snapshotMap[storeId]
            val freshness = snapshot
                ?.freshness(timestamp, freshnessWindowMillis)
                ?: SnapshotFreshness.NEVER_SYNCED
            val supported = entry.availability.mode == StoreMode.MATCHABLE
            var data: StoreCatalogData? = null
            var loadError: String? = null
            if (entry.descriptor.enabled && supported && snapshot != null) {
                try {
                    data = catalog.load(snapshot)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    loadError = exception.message ?: "Could not read the cached index"
                }
            }
            MatchStoreInput(
                storeId = storeId,
                storeName = entry.descriptor.name,
                enabled = entry.descriptor.enabled,
                supported = supported,
                snapshot = snapshot,
                freshness = freshness,
                catalog = data,
                loadError = loadError,
            )
        }
    }

    /**
     * F01-R4 integration. Only an L1 exact package with license and source may
     * be called FOSS. Missing metadata and partial coverage stay UNKNOWN; the
     * classifier never guesses that an app is proprietary.
     */
    override suspend fun classify(app: InstalledApp): ClassificationResult {
        val decision = match(MatchQuery.from(app), includeAlternatives = false)
        decision.correction?.let { correction ->
            val explanation = if (correction.isNoMatch) {
                "You marked this app as having no suitable match."
            } else {
                "You corrected this app to ${correction.target?.displayName}; " +
                    "the installed package itself was not confirmed as FOSS."
            }
            return ClassificationResult(
                classification = Classification.UNKNOWN,
                explanation = explanation,
            )
        }
        val primary = decision.primary
        return when {
            primary?.level == MatchLevel.L1 && primary.isFoss ->
                ClassificationResult(
                    classification = Classification.FOSS,
                    explanation = "Exact package found in ${primary.storeName} " +
                        "with license ${primary.license}.",
                )

            primary?.level == MatchLevel.L1 ->
                ClassificationResult(
                    classification = Classification.UNKNOWN,
                    explanation = "Exact package found, but license or source metadata is missing.",
                )

            primary?.level == MatchLevel.L2 ->
                ClassificationResult(
                    classification = Classification.UNKNOWN,
                    explanation = "Only a fork or variant was found; this package itself " +
                        "was not confirmed as FOSS.",
                )

            primary?.level == MatchLevel.L0 ->
                ClassificationResult(
                    classification = Classification.UNKNOWN,
                    explanation = "Exact package found, but license or source metadata is missing.",
                )

            decision.coverage.complete ->
                ClassificationResult(
                    classification = Classification.UNKNOWN,
                    explanation = "No exact FOSS listing in " +
                        "${decision.coverage.checkedCount} checked " +
                        "store${if (decision.coverage.checkedCount == 1) "" else "s"}. " +
                        "This does not prove the app is proprietary.",
                )

            else ->
                ClassificationResult(
                    classification = Classification.UNKNOWN,
                    explanation = "Coverage is incomplete, so a FOSS build cannot be ruled out.",
                )
        }
    }
}
