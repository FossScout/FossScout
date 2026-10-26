package com.fossscout.core.contribution

import com.fossscout.core.feature.FeatureId
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor

/** Converts a store descriptor without carrying notes, health, or fingerprints. */
fun StoreDescriptor.toReportStoreInput(): ReportStoreInput = ReportStoreInput(
    name = name,
    homepage = homepage,
    adapterType = adapterType,
    indexUrl = indexUrl.orEmpty(),
    apiUrl = apiUrl.orEmpty(),
    repositoryUrl = repositoryUrl.orEmpty(),
    hasPinnedFingerprint = !fingerprint.isNullOrBlank(),
)

fun ContributionContext.Companion.forMissingStore(
    store: ReportStoreInput? = null,
    errorClass: ReportErrorClass? = ReportErrorClass.STORE_VALIDATION,
    errorMessage: String? = null,
): ContributionContext = ContributionContext(
    kind = ReportKind.MISSING_STORE,
    featureId = FeatureId.STORES,
    operation = ReportOperation.ADD_STORE,
    store = store,
    adapterType = store?.adapterType,
    errorClass = errorClass,
    errorMessage = errorMessage,
)

fun ContributionContext.Companion.forAdapterRequest(
    store: ReportStoreInput? = null,
    adapterType: AdapterType? = store?.adapterType,
    errorMessage: String? = null,
): ContributionContext = ContributionContext(
    kind = ReportKind.ADAPTER_REQUEST,
    featureId = FeatureId.STORES,
    operation = ReportOperation.REQUEST_ADAPTER,
    store = store,
    adapterType = adapterType,
    errorClass = ReportErrorClass.ADAPTER_UNSUPPORTED,
    errorMessage = errorMessage,
)

/**
 * Builds a wrong-match context from the decision only. The decision may contain
 * package identities and coverage, but its free-form reasons and inventory are
 * intentionally not copied.
 */
fun ContributionContext.Companion.forWrongMatch(
    decision: MatchDecision,
): ContributionContext = ContributionContext(
    kind = ReportKind.WRONG_MATCH,
    featureId = FeatureId.MATCHING,
    operation = ReportOperation.MATCH_APP,
    errorClass = ReportErrorClass.MATCH,
    packageName = ContributionRedactor.packageName(decision.packageName)
        ?: ContributionRedactor.packageName(decision.appKey),
    observedResult = buildString {
        append("outcome=")
        append(decision.outcome.name)
        append("; level=")
        append(decision.level.name)
        append("; confidence=")
        append(decision.confidence.name)
    },
    coverage = decision.coverage,
)
