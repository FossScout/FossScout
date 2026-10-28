package com.fossscout.feature.matching

import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.AlternativeSuggestion
import com.fossscout.core.matching.CatalogSearchResult
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.model.AppKey

enum class MatchError {
    QUERY_REQUIRED,
    MATCH_FAILED,
    SEARCH_FAILED,
    CORRECTION_FAILED,
}

enum class MatchCorrectionError {
    QUERY_REQUIRED,
    SEARCH_FAILED,
    TARGET_REQUIRED,
    SAVE_FAILED,
}

data class MatchCorrectionUiState(
    val installedAppKey: AppKey,
    val installedLabel: String,
    val query: String = "",
    val results: List<CatalogSearchResult> = emptyList(),
    val selected: CatalogSearchResult? = null,
    val note: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val isSaving: Boolean = false,
    val error: MatchCorrectionError? = null,
)

data class MatchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val decision: MatchDecision? = null,
    val candidates: List<InstalledApp> = emptyList(),
    val error: MatchError? = null,
    val hiddenAlternatives: Set<String> = emptySet(),
    val lastHiddenAlternative: AlternativeSuggestion? = null,
    val correctionEditor: MatchCorrectionUiState? = null,
) {
    /** Suggestions the user hid during this screen session. */
    val visibleAlternatives: List<AlternativeSuggestion>
        get() = decision
            ?.alternatives
            ?.filterNot { it.packageName in hiddenAlternatives }
            .orEmpty()

    val canUndoHidden: Boolean
        get() = lastHiddenAlternative != null

    val hasAlternatives: Boolean get() = visibleAlternatives.isNotEmpty()
}
