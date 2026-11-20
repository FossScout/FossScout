package com.fossscout.core.matching

import com.fossscout.core.index.ReproducibilityCheck
import com.fossscout.core.index.ReproducibilityStatus
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotFreshness

/** A single explainable signal. There is deliberately no aggregate trust score. */
enum class TrustBadgeKind {
    INDEX_VERIFIED,
    INDEX_UNVERIFIED,
    INDEX_FAILED,
    INDEX_NOT_CHECKED,
    SOURCE_AND_LICENSE,
    SOURCE_OR_LICENSE_MISSING,
    ANTI_FEATURE,
    SIGNER_CHANGED,
    STALE_METADATA,
    REPRODUCIBLE,
    REPRODUCIBILITY_NOT_VERIFIED,
    REPRODUCIBILITY_FAILED,
    LINK_ONLY,
}

enum class TrustBadgeTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
    NEGATIVE,
}

data class TrustBadge(
    val kind: TrustBadgeKind,
    val tone: TrustBadgeTone,
    val storeName: String? = null,
    val sourceUrl: String? = null,
    val cacheFetchedAtEpochMillis: Long? = null,
    val antiFeatures: List<String> = emptyList(),
    val previousSignerSha256: List<String> = emptyList(),
    val currentSignerSha256: List<String> = emptyList(),
    val reproducibility: ReproducibilityCheck? = null,
    val signatureStatus: SignatureStatus? = null,
    val freshness: SnapshotFreshness? = null,
) {
    init {
        require(kind != TrustBadgeKind.INDEX_VERIFIED || signatureStatus == SignatureStatus.VERIFIED)
        require(
            kind != TrustBadgeKind.INDEX_UNVERIFIED ||
                signatureStatus == SignatureStatus.UNVERIFIED,
        )
        require(
            kind != TrustBadgeKind.INDEX_FAILED ||
                signatureStatus == SignatureStatus.INVALID ||
                signatureStatus == SignatureStatus.UNSIGNED,
        )
        require(kind != TrustBadgeKind.ANTI_FEATURE || antiFeatures.isNotEmpty())
        require(kind != TrustBadgeKind.STALE_METADATA || freshness == SnapshotFreshness.STALE)
        require(
            kind != TrustBadgeKind.SIGNER_CHANGED ||
                (
                    previousSignerSha256.isNotEmpty() &&
                        currentSignerSha256.isNotEmpty() &&
                        previousSignerSha256.toSet() != currentSignerSha256.toSet()
                    ),
        )
        require(
            kind != TrustBadgeKind.REPRODUCIBLE ||
                reproducibility?.status == ReproducibilityStatus.VERIFIED,
        )
        require(
            kind != TrustBadgeKind.REPRODUCIBILITY_FAILED ||
                reproducibility?.status == ReproducibilityStatus.FAILED,
        )
        require(
            kind != TrustBadgeKind.REPRODUCIBILITY_NOT_VERIFIED ||
                reproducibility?.status == ReproducibilityStatus.NOT_VERIFIED,
        )
    }
}

data class TrustBadgeInput(
    val storeName: String,
    val storeHomepage: String? = null,
    val signatureStatus: SignatureStatus? = null,
    val hasLicense: Boolean = false,
    val hasSource: Boolean = false,
    val antiFeatures: List<String> = emptyList(),
    val freshness: SnapshotFreshness? = null,
    val cacheFetchedAtEpochMillis: Long? = null,
    val previousSignerSha256: List<String> = emptyList(),
    val currentSignerSha256: List<String> = emptyList(),
    val reproducibility: ReproducibilityCheck? = null,
) {
    init {
        require(storeName.isNotBlank()) { "Badge store name is blank" }
    }
}

fun TrustBadgeInput.toTrustBadges(): List<TrustBadge> = buildList {
    add(indexBadge())
    add(
        TrustBadge(
            kind = if (hasLicense && hasSource) {
                TrustBadgeKind.SOURCE_AND_LICENSE
            } else {
                TrustBadgeKind.SOURCE_OR_LICENSE_MISSING
            },
            tone = if (hasLicense && hasSource) {
                TrustBadgeTone.POSITIVE
            } else {
                TrustBadgeTone.WARNING
            },
            storeName = storeName,
            sourceUrl = storeHomepage,
            cacheFetchedAtEpochMillis = cacheFetchedAtEpochMillis,
        ),
    )
    if (antiFeatures.isNotEmpty()) {
        add(
            TrustBadge(
                kind = TrustBadgeKind.ANTI_FEATURE,
                tone = TrustBadgeTone.WARNING,
                storeName = storeName,
                sourceUrl = storeHomepage,
                cacheFetchedAtEpochMillis = cacheFetchedAtEpochMillis,
                antiFeatures = antiFeatures.distinct().sorted(),
            ),
        )
    }
    if (
        previousSignerSha256.isNotEmpty() &&
        currentSignerSha256.isNotEmpty() &&
        previousSignerSha256.toSet() != currentSignerSha256.toSet()
    ) {
        add(
            TrustBadge(
                kind = TrustBadgeKind.SIGNER_CHANGED,
                tone = TrustBadgeTone.WARNING,
                storeName = storeName,
                sourceUrl = storeHomepage,
                cacheFetchedAtEpochMillis = cacheFetchedAtEpochMillis,
                previousSignerSha256 = previousSignerSha256,
                currentSignerSha256 = currentSignerSha256,
                signatureStatus = signatureStatus,
                freshness = freshness,
            ),
        )
    }
    if (freshness == SnapshotFreshness.STALE) {
        add(
            TrustBadge(
                kind = TrustBadgeKind.STALE_METADATA,
                tone = TrustBadgeTone.WARNING,
                storeName = storeName,
                sourceUrl = storeHomepage,
                cacheFetchedAtEpochMillis = cacheFetchedAtEpochMillis,
                freshness = freshness,
            ),
        )
    }
    reproducibility?.let { check ->
        when (check.status) {
            ReproducibilityStatus.VERIFIED -> add(reproducibilityBadge(check))
            ReproducibilityStatus.FAILED -> add(reproducibilityBadge(check))
            ReproducibilityStatus.NOT_VERIFIED -> add(reproducibilityBadge(check))
            ReproducibilityStatus.UNAVAILABLE -> Unit
        }
    }
}

private fun TrustBadgeInput.indexBadge(): TrustBadge {
    val kind = when (signatureStatus) {
        SignatureStatus.VERIFIED -> TrustBadgeKind.INDEX_VERIFIED
        SignatureStatus.UNVERIFIED -> TrustBadgeKind.INDEX_UNVERIFIED
        SignatureStatus.INVALID,
        SignatureStatus.UNSIGNED,
        -> TrustBadgeKind.INDEX_FAILED

        SignatureStatus.NOT_CHECKED,
        null,
        -> TrustBadgeKind.INDEX_NOT_CHECKED
    }
    val tone = when (kind) {
        TrustBadgeKind.INDEX_VERIFIED -> TrustBadgeTone.POSITIVE
        TrustBadgeKind.INDEX_FAILED -> TrustBadgeTone.NEGATIVE
        TrustBadgeKind.INDEX_UNVERIFIED -> TrustBadgeTone.WARNING
        else -> TrustBadgeTone.NEUTRAL
    }
    return TrustBadge(
        kind = kind,
        tone = tone,
        storeName = storeName,
        sourceUrl = storeHomepage,
        cacheFetchedAtEpochMillis = cacheFetchedAtEpochMillis,
        signatureStatus = signatureStatus,
        freshness = freshness,
    )
}

private fun reproducibilityBadge(check: ReproducibilityCheck): TrustBadge {
    val kind = when (check.status) {
        ReproducibilityStatus.VERIFIED -> TrustBadgeKind.REPRODUCIBLE
        ReproducibilityStatus.FAILED -> TrustBadgeKind.REPRODUCIBILITY_FAILED
        ReproducibilityStatus.NOT_VERIFIED -> TrustBadgeKind.REPRODUCIBILITY_NOT_VERIFIED
        ReproducibilityStatus.UNAVAILABLE -> error("Unavailable checks are not badges")
    }
    val tone = when (kind) {
        TrustBadgeKind.REPRODUCIBLE -> TrustBadgeTone.POSITIVE
        TrustBadgeKind.REPRODUCIBILITY_FAILED -> TrustBadgeTone.NEGATIVE
        else -> TrustBadgeTone.NEUTRAL
    }
    return TrustBadge(
        kind = kind,
        tone = tone,
        storeName = "F-Droid",
        sourceUrl = check.sourceUrl,
        reproducibility = check,
    )
}

fun StoreSearchStoreHit.toTrustBadges(
    reproducibility: ReproducibilityCheck? = null,
): List<TrustBadge> = TrustBadgeInput(
    storeName = storeName,
    storeHomepage = storeHomepage,
    signatureStatus = signatureStatus,
    hasLicense = !license.isNullOrBlank(),
    hasSource = !sourceCode.isNullOrBlank(),
    antiFeatures = antiFeatures,
    freshness = freshness,
    cacheFetchedAtEpochMillis = fetchedAtEpochMillis,
    previousSignerSha256 = previousSignerSha256,
    currentSignerSha256 = signerSha256,
    reproducibility = reproducibility,
).toTrustBadges()

fun MatchCandidate.toTrustBadges(
    reproducibility: ReproducibilityCheck? = null,
): List<TrustBadge> = TrustBadgeInput(
    storeName = storeName,
    storeHomepage = storeHomepage,
    signatureStatus = signatureStatus,
    hasLicense = !license.isNullOrBlank(),
    hasSource = !sourceUrl.isNullOrBlank(),
    antiFeatures = antiFeatures,
    freshness = snapshotFreshness,
    cacheFetchedAtEpochMillis = lastSeenEpochMillis,
    previousSignerSha256 = previousSignerSha256,
    currentSignerSha256 = signerSha256,
    reproducibility = reproducibility,
).toTrustBadges()

fun StoreSearchLinkOnly.toTrustBadge(): TrustBadge = TrustBadge(
    kind = TrustBadgeKind.LINK_ONLY,
    tone = TrustBadgeTone.NEUTRAL,
    storeName = storeName,
    sourceUrl = homepage,
)
