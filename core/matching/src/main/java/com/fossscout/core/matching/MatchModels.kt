package com.fossscout.core.matching

import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness
import kotlinx.serialization.Serializable

/**
 * Default freshness window for a store snapshot used by matching.
 *
 * This is deliberately the same one-day boundary as the inventory/index UI:
 * after 24 hours a cached store cannot contribute to a "not found" conclusion.
 * The value is injected into the engine so tests and future settings can change
 * it without hiding a magic number in the verdict logic.
 */
const val DEFAULT_MATCH_FRESHNESS_MILLIS: Long = 24L * 60L * 60L * 1_000L

/** Maximum number of functional alternatives shown for one query. */
const val DEFAULT_MAX_ALTERNATIVES: Int = 5

/**
 * F02 match levels. Only [L1] may be described as "the same app"; [L2] is a
 * fork/variant and [L3] is a different app that solves a similar job.
 */
@Serializable
enum class MatchLevel {
    L1,
    L2,
    L3,
    L0,
}

/** The conclusion shown to the user. Coverage gaps are never "not found". */
@Serializable
enum class MatchOutcome {
    MATCH,
    ALTERNATIVES,
    NOT_FOUND,
    INCOMPLETE,
    UNKNOWN,
}

/** Label-first confidence. There is no unexplained numeric score. */
@Serializable
enum class ConfidenceLabel {
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

@Serializable
enum class CoverageStatus {
    CHECKED,
    STALE,
    FAILED,
    SKIPPED,
}

@Serializable
enum class CoverageReason {
    NONE,
    DISABLED,
    UNSUPPORTED,
    NEVER_SYNCED,
    STALE_DATA,
    MISSING_PAYLOAD,
    READ_ERROR,
}

/**
 * What a store contributed to one matching decision. Every non-checked store
 * carries a reason (F06-R3).
 */
@Serializable
data class StoreCoverage(
    val storeId: String,
    val storeName: String,
    val status: CoverageStatus,
    val reason: CoverageReason = CoverageReason.NONE,
    val detail: String? = null,
    val lastSyncEpochMillis: Long? = null,
    val packageCount: Int? = null,
    val signatureStatus: SignatureStatus? = null,
) {
    init {
        require(storeId.isNotBlank()) { "Coverage store id is blank" }
        require(storeName.isNotBlank()) { "Coverage store name is blank" }
        require((status == CoverageStatus.CHECKED) == (reason == CoverageReason.NONE)) {
            "Checked coverage must not have a reason; gaps must have one"
        }
    }

    /** Disabled stores are user choices; every other gap blocks a conclusion. */
    val requiredForConclusiveResult: Boolean
        get() = reason != CoverageReason.DISABLED

    val contributesCatalogResults: Boolean
        get() = status == CoverageStatus.CHECKED || status == CoverageStatus.STALE
}

/**
 * Coverage for one match decision. A result is conclusive when at least one
 * enabled store was checked with fresh enough data and every enabled store that
 * was not deliberately disabled was also checked. Unsupported, stale, failed,
 * and never-synced stores remain visible gaps.
 */
@Serializable
data class CoverageSnapshot(
    val checkedAtEpochMillis: Long,
    val freshnessWindowMillis: Long = DEFAULT_MATCH_FRESHNESS_MILLIS,
    val stores: List<StoreCoverage> = emptyList(),
) {
    val checked: List<StoreCoverage>
        get() = stores.filter { it.status == CoverageStatus.CHECKED }

    val required: List<StoreCoverage>
        get() = stores.filter(StoreCoverage::requiredForConclusiveResult)

    val complete: Boolean
        get() = required.isNotEmpty() && required.all { it.status == CoverageStatus.CHECKED }

    val checkedCount: Int get() = checked.size

    val gapCount: Int
        get() = required.count { it.status != CoverageStatus.CHECKED }

    val contributing: List<StoreCoverage>
        get() = stores.filter(StoreCoverage::contributesCatalogResults)
}

/** Evidence attached to an exact, fork, or functional match. */
@Serializable
data class MatchCandidate(
    val appKey: String,
    val level: MatchLevel,
    val storeId: String,
    val storeName: String,
    val packageName: String,
    val displayName: String,
    val summary: String = "",
    val license: String? = null,
    val sourceUrl: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val signerSha256: List<String> = emptyList(),
    val confidence: ConfidenceLabel = ConfidenceLabel.UNKNOWN,
    val reasons: List<String> = emptyList(),
    val lastSeenEpochMillis: Long? = null,
    val categories: List<String> = emptyList(),
    val signatureStatus: SignatureStatus? = null,
) {
    init {
        require(appKey.isNotBlank()) { "Candidate app key is blank" }
        require(storeId.isNotBlank()) { "Candidate store id is blank" }
        require(packageName.isNotBlank()) { "Candidate package is blank" }
        require(displayName.isNotBlank()) { "Candidate display name is blank" }
    }

    /** Only an L1 candidate with both license and source may be called FOSS. */
    val isFoss: Boolean
        get() = level == MatchLevel.L1 && !license.isNullOrBlank() && !sourceUrl.isNullOrBlank()

    val isSameApp: Boolean get() = level == MatchLevel.L1
}

/**
 * A functional alternative. It is always a different app and is never shown when
 * an L1 or L2 candidate exists.
 */
@Serializable
data class AlternativeSuggestion(
    val appKey: String,
    val storeId: String,
    val storeName: String,
    val packageName: String,
    val displayName: String,
    val summary: String = "",
    val license: String? = null,
    val sourceUrl: String? = null,
    val categories: List<String> = emptyList(),
    val score: Int,
    val confidence: ConfidenceLabel = ConfidenceLabel.LOW,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(appKey.isNotBlank()) { "Alternative app key is blank" }
        require(storeId.isNotBlank()) { "Alternative store id is blank" }
        require(packageName.isNotBlank()) { "Alternative package is blank" }
        require(displayName.isNotBlank()) { "Alternative display name is blank" }
        require(score > 0) { "An alternative needs positive relevance evidence" }
        require(reasons.isNotEmpty()) { "An alternative needs a visible reason" }
    }

    val level: MatchLevel get() = MatchLevel.L3
    val isDifferentApp: Boolean get() = true
}

/** One matching result, including coverage and any alternatives. */
@Serializable
data class MatchDecision(
    val appKey: String,
    val label: String,
    val packageName: String? = null,
    val level: MatchLevel,
    val outcome: MatchOutcome,
    val confidence: ConfidenceLabel,
    val primary: MatchCandidate? = null,
    val otherMatches: List<MatchCandidate> = emptyList(),
    val alternatives: List<AlternativeSuggestion> = emptyList(),
    val coverage: CoverageSnapshot,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(appKey.isNotBlank()) { "Decision app key is blank" }
        require(label.isNotBlank()) { "Decision label is blank" }
    }

    val hasExactMatch: Boolean get() = primary?.level == MatchLevel.L1
    val hasForkOrExactMatch: Boolean
        get() = primary?.level == MatchLevel.L1 || primary?.level == MatchLevel.L2
}

/**
 * A store prepared for the engine. Catalog loading is separated from verdict
 * logic so coverage, matching, and ranking can be tested without files.
 */
data class MatchStoreInput(
    val storeId: String,
    val storeName: String,
    val enabled: Boolean,
    val supported: Boolean,
    val snapshot: com.fossscout.core.index.StoreSnapshot? = null,
    val freshness: SnapshotFreshness = SnapshotFreshness.NEVER_SYNCED,
    val catalog: StoreCatalogData? = null,
    val loadError: String? = null,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(storeName.isNotBlank()) { "Store name is blank" }
    }

    val contributesCatalogResults: Boolean
        get() = enabled &&
            supported &&
            catalog != null &&
            (freshness == SnapshotFreshness.FRESH || freshness == SnapshotFreshness.STALE)
}

/**
 * Input to a match. [packageName] is explicit because a favourite key can be a
 * catalog/source key rather than an Android package name.
 */
data class MatchQuery(
    val appKey: String,
    val label: String,
    val packageName: String? = null,
    val installedSignerSha256: String? = null,
    val categories: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val sourceUrls: Set<String> = emptySet(),
) {
    init {
        require(appKey.isNotBlank()) { "Query app key is blank" }
        require(label.isNotBlank()) { "Query label is blank" }
    }

    val effectivePackageName: String?
        get() = packageName?.trim()?.takeIf(String::isNotEmpty)
            ?: appKey.trim().takeIf(::looksLikePackageName)

    companion object
}
