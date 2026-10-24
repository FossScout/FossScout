package com.fossscout.core.matching

import com.fossscout.core.index.SnapshotFreshness
import com.fossscout.core.index.StoreSnapshot
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.ClassificationResult
import com.fossscout.core.inventory.ClassificationSource
import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
) : ClassificationSource {

    suspend fun match(
        query: MatchQuery,
        includeAlternatives: Boolean = true,
    ): MatchDecision {
        val entries = storeEntries.first()
        val snapshotMap = snapshots.first()
        val timestamp = now()
        val stores = entries.map { entry ->
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
        return MatchEngine.evaluate(
            query = query,
            stores = stores,
            nowEpochMillis = timestamp,
            freshnessWindowMillis = freshnessWindowMillis,
            maxAlternatives = if (includeAlternatives) maxAlternatives else 0,
        )
    }

    /**
     * F01-R4 integration. Only an L1 exact package with license and source may
     * be called FOSS. Missing metadata and partial coverage stay UNKNOWN; the
     * classifier never guesses that an app is proprietary.
     */
    override suspend fun classify(app: InstalledApp): ClassificationResult {
        val decision = match(MatchQuery.from(app), includeAlternatives = false)
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
