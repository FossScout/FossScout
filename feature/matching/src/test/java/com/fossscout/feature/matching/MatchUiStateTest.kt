package com.fossscout.feature.matching

import com.fossscout.core.matching.AlternativeSuggestion
import com.fossscout.core.matching.CoverageSnapshot
import com.fossscout.core.matching.MatchDecision
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import com.fossscout.core.matching.ConfidenceLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchUiStateTest {
    private val first = alternative("org.maps.one", "Open Maps")
    private val second = alternative("org.maps.two", "Maps Lite")

    @Test
    fun `hidden alternatives are removed from the visible list`() {
        val state = MatchUiState(
            decision = decision(listOf(first, second)),
            hiddenAlternatives = setOf(first.packageName),
            lastHiddenAlternative = first,
        )

        assertEquals(listOf(second), state.visibleAlternatives)
        assertTrue(state.hasAlternatives)
        assertTrue(state.canUndoHidden)
    }

    @Test
    fun `undo state disappears when there is nothing hidden`() {
        val state = MatchUiState(decision = decision(listOf(first)))

        assertEquals(listOf(first), state.visibleAlternatives)
        assertFalse(state.canUndoHidden)
    }

    @Test
    fun `no decision has no alternatives`() {
        val state = MatchUiState()

        assertTrue(state.visibleAlternatives.isEmpty())
        assertFalse(state.hasAlternatives)
        assertFalse(state.canUndoHidden)
    }

    private fun decision(
        alternatives: List<AlternativeSuggestion>,
    ) = MatchDecision(
        appKey = "org.example.app",
        label = "Example",
        packageName = "org.example.app",
        level = MatchLevel.L3,
        outcome = MatchOutcome.ALTERNATIVES,
        confidence = ConfidenceLabel.LOW,
        alternatives = alternatives,
        coverage = CoverageSnapshot(
            checkedAtEpochMillis = 1_000L,
            stores = emptyList(),
        ),
    )

    private fun alternative(packageName: String, name: String) = AlternativeSuggestion(
        appKey = packageName,
        storeId = "store-1",
        storeName = "Store 1",
        packageName = packageName,
        displayName = name,
        score = 1,
        reasons = listOf("Summary or name mentions: maps"),
    )
}
