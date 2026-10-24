package com.fossscout.feature.matching

import com.fossscout.core.matching.ConfidenceLabel
import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageStatus
import com.fossscout.core.matching.MatchLevel
import com.fossscout.core.matching.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MatchLabelsTest {
    @Test
    fun `every match level has a distinct label`() {
        val labels = MatchLevel.entries.map { it.labelRes() }

        assertEquals(MatchLevel.entries.size, labels.toSet().size)
        labels.forEach { assertNotEquals(0, it) }
    }

    @Test
    fun `every confidence and coverage status has a label`() {
        val confidence = ConfidenceLabel.entries.map { it.labelRes() }
        val statuses = CoverageStatus.entries.map { it.labelRes() }
        val reasons = CoverageReason.entries.map { it.labelRes() }

        assertEquals(ConfidenceLabel.entries.size, confidence.toSet().size)
        assertEquals(CoverageStatus.entries.size, statuses.toSet().size)
        assertEquals(CoverageReason.entries.size, reasons.toSet().size)
        (confidence + statuses + reasons).forEach { assertNotEquals(0, it) }
    }

    @Test
    fun `match outcomes distinguish exact and variant wording`() {
        assertNotEquals(
            MatchOutcome.MATCH.titleRes(MatchLevel.L1),
            MatchOutcome.MATCH.titleRes(MatchLevel.L2),
        )
        assertNotEquals(
            MatchOutcome.MATCH.bodyRes(MatchLevel.L1),
            MatchOutcome.MATCH.bodyRes(MatchLevel.L2),
        )
    }

    @Test
    fun `every outcome has a title and body`() {
        MatchOutcome.entries.forEach { outcome ->
            assertNotEquals(0, outcome.titleRes(MatchLevel.L0))
            assertNotEquals(0, outcome.bodyRes(MatchLevel.L0))
        }
    }
}
