package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness

/**
 * Deterministic matching and coverage engine.
 *
 * The engine never performs I/O. Callers provide store snapshots, freshness, and
 * loaded catalogs, which makes the verdict rules testable with fixtures and
 * keeps stale/unsupported stores from silently producing "not found".
 */
object MatchEngine {
    fun evaluate(
        query: MatchQuery,
        stores: List<MatchStoreInput>,
        nowEpochMillis: Long,
        freshnessWindowMillis: Long = DEFAULT_MATCH_FRESHNESS_MILLIS,
        maxAlternatives: Int = DEFAULT_MAX_ALTERNATIVES,
    ): MatchDecision {
        val coverage = CoverageSnapshot(
            checkedAtEpochMillis = nowEpochMillis,
            freshnessWindowMillis = freshnessWindowMillis,
            stores = stores.map { it.toCoverage() },
        )
        val packageName = query.effectivePackageName

        val exactMatches = if (packageName == null) {
            emptyList()
        } else {
            stores.filter(MatchStoreInput::contributesCatalogResults).flatMap { store ->
                store.catalog?.byPackage?.get(packageName)
                    ?.let { entry -> listOf(exactCandidate(query, store, entry)) }
                    .orEmpty()
            }
        }
        val hasForkOrExact = exactMatches.any {
            it.level == MatchLevel.L1 || it.level == MatchLevel.L2
        }
        val forkMatches = if (hasForkOrExact) {
            emptyList()
        } else {
            sourceMatches(query, stores, packageName)
        }
        val allMatches = (exactMatches + forkMatches).sortedWith(
            compareBy<MatchCandidate> { levelRank(it.level) },
        )
        val primary = allMatches.firstOrNull()
        val otherMatches = allMatches.drop(1)
            .distinctBy { it.storeId to it.packageName }

        val alternatives = if (
            maxAlternatives > 0 &&
            primary?.level != MatchLevel.L1 &&
            primary?.level != MatchLevel.L2
        ) {
            alternativesFor(query, stores, allMatches, packageName, maxAlternatives)
        } else {
            emptyList()
        }

        val outcome = when {
            primary?.level == MatchLevel.L1 || primary?.level == MatchLevel.L2 ->
                MatchOutcome.MATCH

            primary?.level == MatchLevel.L0 -> MatchOutcome.UNKNOWN
            !coverage.complete -> MatchOutcome.INCOMPLETE
            alternatives.isNotEmpty() -> MatchOutcome.ALTERNATIVES
            else -> MatchOutcome.NOT_FOUND
        }

        val confidence = when (outcome) {
            MatchOutcome.MATCH -> primary?.confidence ?: ConfidenceLabel.UNKNOWN
            MatchOutcome.UNKNOWN -> ConfidenceLabel.UNKNOWN
            MatchOutcome.NOT_FOUND -> ConfidenceLabel.HIGH
            MatchOutcome.INCOMPLETE -> ConfidenceLabel.LOW
            MatchOutcome.ALTERNATIVES -> {
                if (alternatives.any { it.confidence == ConfidenceLabel.MEDIUM }) {
                    ConfidenceLabel.MEDIUM
                } else {
                    ConfidenceLabel.LOW
                }
            }
        }

        val level = when {
            primary != null -> primary.level
            alternatives.isNotEmpty() -> MatchLevel.L3
            else -> MatchLevel.L0
        }

        return MatchDecision(
            appKey = query.appKey,
            label = query.label,
            packageName = packageName,
            level = level,
            outcome = outcome,
            confidence = confidence,
            primary = primary,
            otherMatches = otherMatches,
            alternatives = alternatives,
            coverage = coverage,
            reasons = decisionReasons(outcome, primary, coverage, alternatives),
        )
    }

    private fun exactCandidate(
        query: MatchQuery,
        store: MatchStoreInput,
        entry: CatalogEntry,
    ): MatchCandidate {
        val missingEvidence = buildList {
            if (!entry.hasLicense) add("License metadata is missing.")
            if (!entry.hasSource) add("Source URL metadata is missing.")
        }
        val staleNote = if (store.freshness == SnapshotFreshness.STALE) {
            listOf("The cached store snapshot is stale, so this result is provisional.")
        } else {
            emptyList()
        }
        val signatureNote = if (
            store.snapshot?.signatureStatus == SignatureStatus.UNVERIFIED
        ) {
            listOf("The index signature is valid but the repository fingerprint is not pinned.")
        } else {
            emptyList()
        }

        if (missingEvidence.isNotEmpty()) {
            return MatchCandidate(
                appKey = entry.packageName,
                level = MatchLevel.L0,
                storeId = store.storeId,
                storeName = store.storeName,
                packageName = entry.packageName,
                displayName = entry.name,
                summary = entry.summary,
                license = entry.license,
                sourceUrl = entry.sourceCode,
                versionName = entry.versionName,
                signerSha256 = entry.signerSha256,
                confidence = ConfidenceLabel.UNKNOWN,
                reasons = listOf("Package ID matches exactly.") +
                    missingEvidence + staleNote + signatureNote,
                lastSeenEpochMillis = store.snapshot?.fetchedAtEpochMillis,
                categories = entry.categories,
                signatureStatus = store.snapshot?.signatureStatus,
            )
        }

        val signerMismatch = signerMismatch(
            installedSigner = query.installedSignerSha256,
            storeSigners = entry.signerSha256,
        )
        val level = if (signerMismatch) MatchLevel.L2 else MatchLevel.L1
        val confidence = when {
            level == MatchLevel.L1 && store.freshness == SnapshotFreshness.FRESH ->
                ConfidenceLabel.HIGH

            level == MatchLevel.L1 -> ConfidenceLabel.MEDIUM
            store.freshness == SnapshotFreshness.FRESH -> ConfidenceLabel.MEDIUM
            else -> ConfidenceLabel.LOW
        }
        val reasons = buildList {
            add("Package ID matches exactly.")
            if (signerMismatch) {
                add(
                    "The installed signer differs from the store listing, so this is " +
                        "treated as a variant rather than the same build.",
                )
            }
            add("License: ${entry.license}")
            add("Source: ${entry.sourceCode}")
            addAll(staleNote)
            addAll(signatureNote)
        }

        return MatchCandidate(
            appKey = entry.packageName,
            level = level,
            storeId = store.storeId,
            storeName = store.storeName,
            packageName = entry.packageName,
            displayName = entry.name,
            summary = entry.summary,
            license = entry.license,
            sourceUrl = entry.sourceCode,
            versionName = entry.versionName,
            signerSha256 = entry.signerSha256,
            confidence = confidence,
            reasons = reasons,
            lastSeenEpochMillis = store.snapshot?.fetchedAtEpochMillis,
            categories = entry.categories,
            signatureStatus = store.snapshot?.signatureStatus,
        )
    }

    private fun sourceMatches(
        query: MatchQuery,
        stores: List<MatchStoreInput>,
        packageName: String?,
    ): List<MatchCandidate> {
        val sources = query.sourceUrls.mapNotNull(::normalizeSourceUrl).toSet()
        if (sources.isEmpty()) return emptyList()

        return stores
            .filter(MatchStoreInput::contributesCatalogResults)
            .flatMap { store ->
                val catalog = store.catalog ?: return@flatMap emptyList()
            sources.flatMap { source ->
                catalog.bySource[source].orEmpty().mapNotNull { entry ->
                    if (entry.packageName == packageName) return@mapNotNull null
                    val missingLicense = !entry.hasLicense
                    val level = if (missingLicense) MatchLevel.L0 else MatchLevel.L2
                    val staleNote = if (store.freshness == SnapshotFreshness.STALE) {
                        listOf("The cached store snapshot is stale, so this result is provisional.")
                    } else {
                        emptyList()
                    }
                    val reasons = buildList {
                        add("Shares the upstream source URL: $source")
                        if (missingLicense) {
                            add("License metadata is missing.")
                        } else {
                            add("License: ${entry.license}")
                        }
                        addAll(staleNote)
                    }
                    MatchCandidate(
                        appKey = entry.packageName,
                        level = level,
                        storeId = store.storeId,
                        storeName = store.storeName,
                        packageName = entry.packageName,
                        displayName = entry.name,
                        summary = entry.summary,
                        license = entry.license,
                        sourceUrl = entry.sourceCode,
                        versionName = entry.versionName,
                        signerSha256 = entry.signerSha256,
                        confidence = when {
                            level == MatchLevel.L0 -> ConfidenceLabel.UNKNOWN
                            store.freshness == SnapshotFreshness.STALE -> ConfidenceLabel.LOW
                            else -> ConfidenceLabel.MEDIUM
                        },
                        reasons = reasons,
                        lastSeenEpochMillis = store.snapshot?.fetchedAtEpochMillis,
                        categories = entry.categories,
                        signatureStatus = store.snapshot?.signatureStatus,
                    )
                }
            }
        }
    }

    private fun alternativesFor(
        query: MatchQuery,
        stores: List<MatchStoreInput>,
        matches: List<MatchCandidate>,
        packageName: String?,
        limit: Int,
    ): List<AlternativeSuggestion> {
        val primary = matches.firstOrNull()
        val categories = query.categories +
            primary?.categories.orEmpty()
        val tokens = tokenize(query.label) + tokenize(primary?.summary.orEmpty())
        val excludedPackages = buildSet {
            packageName?.let { add(it) }
            matches.forEach { add(it.packageName) }
        }
        val excludedNames = buildSet {
            normalizeLabel(query.label)?.let { add(it) }
            primary?.let { candidate ->
                normalizeLabel(candidate.displayName)?.let { add(it) }
            }
        }
        val candidates = stores
            .filter(MatchStoreInput::contributesCatalogResults)
            .flatMap { store ->
                store.catalog.orEmptyEntries().map { entry ->
                    AlternativeCandidate(
                        storeId = store.storeId,
                        storeName = store.storeName,
                        entry = entry,
                    )
                }
            }
        return AlternativeRanker.rank(
            categories = categories,
            tags = query.tags,
            tokens = tokens,
            candidates = candidates,
            excludePackages = excludedPackages,
            excludeNames = excludedNames,
            limit = limit,
        )
    }

    private fun decisionReasons(
        outcome: MatchOutcome,
        primary: MatchCandidate?,
        coverage: CoverageSnapshot,
        alternatives: List<AlternativeSuggestion>,
    ): List<String> = when (outcome) {
        MatchOutcome.MATCH -> buildList {
            add(
                if (primary?.level == MatchLevel.L1) {
                    "Exact package matched in ${primary.storeName}."
                } else {
                    "A related fork or variant was found; only L1 is the same app."
                },
            )
            primary?.reasons.orEmpty().forEach { add(it) }
        }

        MatchOutcome.UNKNOWN -> listOf(
            "An exact package was found, but license or source evidence is missing.",
        )

        MatchOutcome.NOT_FOUND -> listOf(
            "All enabled, supported stores were checked with fresh enough data.",
        )

        MatchOutcome.INCOMPLETE -> buildList {
            add("Not enough coverage to conclude that no FOSS match exists.")
            if (coverage.required.isEmpty()) {
                add("No enabled, supported store is available.")
            } else {
                val gaps = coverage.required.filter { it.status != CoverageStatus.CHECKED }
                if (gaps.isNotEmpty()) {
                    add(
                        "Unchecked stores: " +
                            gaps.joinToString { "${it.storeName} (${it.reason.name})" },
                    )
                }
            }
        }

        MatchOutcome.ALTERNATIVES -> buildList {
            add("No exact or fork match was found; alternatives are different apps.")
            if (alternatives.size == 1) {
                add("One different app is shown as a possible alternative.")
            }
        }
    }

    private fun MatchStoreInput.toCoverage(): StoreCoverage {
        val lastSync = snapshot?.fetchedAtEpochMillis
        val packageCount = snapshot?.packageCount
        val signature = snapshot?.signatureStatus
        return when {
            !enabled -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.SKIPPED,
                reason = CoverageReason.DISABLED,
                detail = "This store is disabled.",
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            !supported -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.SKIPPED,
                reason = CoverageReason.UNSUPPORTED,
                detail = "This build cannot read this store's catalog.",
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            snapshot == null -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.SKIPPED,
                reason = CoverageReason.NEVER_SYNCED,
                detail = "No verified index is cached for this store.",
            )

            loadError != null -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.FAILED,
                reason = CoverageReason.READ_ERROR,
                detail = loadError,
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            catalog == null -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.FAILED,
                reason = CoverageReason.MISSING_PAYLOAD,
                detail = "The cached index payload is unavailable.",
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            freshness == SnapshotFreshness.STALE -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.STALE,
                reason = CoverageReason.STALE_DATA,
                detail = "The cached index is older than the freshness window.",
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            freshness == SnapshotFreshness.NEVER_SYNCED -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.SKIPPED,
                reason = CoverageReason.NEVER_SYNCED,
                detail = "No verified index is cached for this store.",
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )

            else -> StoreCoverage(
                storeId = storeId,
                storeName = storeName,
                status = CoverageStatus.CHECKED,
                reason = CoverageReason.NONE,
                lastSyncEpochMillis = lastSync,
                packageCount = packageCount,
                signatureStatus = signature,
            )
        }
    }

    private fun signerMismatch(
        installedSigner: String?,
        storeSigners: List<String>,
    ): Boolean {
        val installed = installedSigner?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
            ?: return false
        if (storeSigners.isEmpty()) return false
        return storeSigners.none { it.trim().lowercase() == installed }
    }

    private fun levelRank(level: MatchLevel): Int = when (level) {
        MatchLevel.L1 -> 0
        MatchLevel.L2 -> 1
        MatchLevel.L3 -> 2
        MatchLevel.L0 -> 3
    }

    private fun StoreCatalogData?.orEmptyEntries(): List<CatalogEntry> =
        this?.entries.orEmpty()
}
