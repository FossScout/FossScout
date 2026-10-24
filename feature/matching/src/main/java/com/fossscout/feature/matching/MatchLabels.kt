package com.fossscout.feature.matching

import androidx.annotation.StringRes
import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome

@StringRes
fun MatchLevel.labelRes(): Int = when (this) {
    MatchLevel.L1 -> R.string.match_level_l1
    MatchLevel.L2 -> R.string.match_level_l2
    MatchLevel.L3 -> R.string.match_level_l3
    MatchLevel.L0 -> R.string.match_level_l0
}

@StringRes
fun ConfidenceLabel.labelRes(): Int = when (this) {
    ConfidenceLabel.HIGH -> R.string.match_confidence_high
    ConfidenceLabel.MEDIUM -> R.string.match_confidence_medium
    ConfidenceLabel.LOW -> R.string.match_confidence_low
    ConfidenceLabel.UNKNOWN -> R.string.match_confidence_unknown
}

@StringRes
fun MatchOutcome.titleRes(level: MatchLevel): Int = when (this) {
    MatchOutcome.MATCH -> if (level == MatchLevel.L1) {
        R.string.match_outcome_exact_title
    } else {
        R.string.match_outcome_variant_title
    }

    MatchOutcome.ALTERNATIVES -> R.string.match_outcome_alternatives_title
    MatchOutcome.NOT_FOUND -> R.string.match_outcome_not_found_title
    MatchOutcome.INCOMPLETE -> R.string.match_outcome_incomplete_title
    MatchOutcome.UNKNOWN -> R.string.match_outcome_unknown_title
}

@StringRes
fun MatchOutcome.bodyRes(level: MatchLevel): Int = when (this) {
    MatchOutcome.MATCH -> if (level == MatchLevel.L1) {
        R.string.match_outcome_exact_body
    } else {
        R.string.match_outcome_variant_body
    }

    MatchOutcome.ALTERNATIVES -> R.string.match_outcome_alternatives_body
    MatchOutcome.NOT_FOUND -> R.string.match_outcome_not_found_body
    MatchOutcome.INCOMPLETE -> R.string.match_outcome_incomplete_body
    MatchOutcome.UNKNOWN -> R.string.match_outcome_unknown_body
}

@StringRes
fun CoverageStatus.labelRes(): Int = when (this) {
    CoverageStatus.CHECKED -> R.string.match_coverage_status_checked
    CoverageStatus.STALE -> R.string.match_coverage_status_stale
    CoverageStatus.FAILED -> R.string.match_coverage_status_failed
    CoverageStatus.SKIPPED -> R.string.match_coverage_status_skipped
}

@StringRes
fun CoverageReason.labelRes(): Int = when (this) {
    CoverageReason.NONE -> R.string.match_coverage_reason_none
    CoverageReason.DISABLED -> R.string.match_coverage_reason_disabled
    CoverageReason.UNSUPPORTED -> R.string.match_coverage_reason_unsupported
    CoverageReason.NEVER_SYNCED -> R.string.match_coverage_reason_never_synced
    CoverageReason.STALE_DATA -> R.string.match_coverage_reason_stale
    CoverageReason.MISSING_PAYLOAD -> R.string.match_coverage_reason_missing_payload
    CoverageReason.READ_ERROR -> R.string.match_coverage_reason_read_error
}
