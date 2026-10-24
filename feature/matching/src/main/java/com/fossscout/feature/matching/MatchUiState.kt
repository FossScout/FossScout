package com.fossscout.feature.matching

import com.fossscout.core.inventory.InstalledApp
import com.fossscout.core.matching.AlternativeSuggestion
import com.fossscout.core.matching.MatchDecision

enum class MatchError {
    QUERY_REQUIRED,
    MATCH_FAILED,
    SEARCH_FAILED,
}

data class MatchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val decision: MatchDecision? = null,
    val candidates: List<InstalledApp> = emptyList(),
    val error: MatchError? = null,
    val hiddenAlternatives: Set<String> = emptySet(),
    val lastHiddenAlternative: AlternativeSuggestion? = null,
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
