package com.fossscout.core.matching

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlternativeRankerTest {
    @Test
    fun `category overlap is the strongest explainable signal`() {
        val suggestions = rank(
            categories = setOf("Maps & Navigation"),
            candidates = listOf(
                candidate("org.maps", categories = listOf("Maps & Navigation")),
                candidate("org.notes", categories = listOf("Notes")),
            ),
        )

        assertEquals(1, suggestions.size)
        val suggestion = suggestions.single()
        assertEquals("org.maps", suggestion.packageName)
        assertEquals(4, suggestion.score)
        assertEquals(ConfidenceLabel.LOW, suggestion.confidence)
        assertTrue(suggestion.reasons.any { it.contains("Same category") })
    }

    @Test
    fun `tags and description tokens contribute visible reasons`() {
        val suggestions = rank(
            tags = setOf("offline"),
            tokens = setOf("maps"),
            candidates = listOf(
                candidate(
                    packageName = "org.maps",
                    summary = "Offline maps",
                    categories = listOf("Navigation"),
                ),
            ),
        )

        val suggestion = suggestions.single()
        assertEquals(4, suggestion.score)
        assertTrue(suggestion.reasons.any { it.contains("tag") })
        assertTrue(suggestion.reasons.any { it.contains("mentions") })
    }

    @Test
    fun `candidates without positive evidence are excluded`() {
        val suggestions = rank(
            categories = setOf("Maps"),
            tokens = setOf("navigation"),
            candidates = listOf(
                candidate(
                    packageName = "org.unrelated",
                    name = "Todo List",
                    summary = "Tasks and notes",
                    categories = listOf("Productivity"),
                ),
            ),
        )

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `ranking is by score then name not store order`() {
        val suggestions = rank(
            categories = setOf("Maps"),
            tokens = setOf("weak"),
            candidates = listOf(
                candidate("org.weak", name = "Weak Maps", categories = listOf("Notes")),
                candidate("org.strong", name = "Strong Maps", categories = listOf("Maps")),
                candidate("org.strong2", name = "Another Maps", categories = listOf("Maps")),
            ),
        )

        assertEquals(
            listOf("org.strong2", "org.strong", "org.weak"),
            suggestions.map { it.packageName },
        )
        assertEquals(listOf(4, 4, 1), suggestions.map { it.score })
    }

    @Test
    fun `duplicate packages from multiple stores are shown once`() {
        val entry = CatalogEntry(
            packageName = "org.maps",
            name = "Open Maps",
            categories = listOf("Maps"),
            license = "MIT",
            sourceCode = "https://example.org/maps",
        )
        val suggestions = rank(
            categories = setOf("Maps"),
            candidates = listOf(
                AlternativeCandidate("store-a", "A", entry),
                AlternativeCandidate("store-b", "B", entry),
            ),
        )

        assertEquals(1, suggestions.size)
        assertEquals("store-a", suggestions.single().storeId)
    }

    @Test
    fun `same package and same display name are excluded`() {
        val suggestions = rank(
            categories = setOf("Maps"),
            candidates = listOf(
                candidate("org.maps", name = "Open Maps", categories = listOf("Maps")),
                candidate("org.maps.other", name = "Open Maps", categories = listOf("Maps")),
            ),
            excludePackages = setOf("org.maps"),
            excludeNames = setOf("open maps"),
        )

        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `higher evidence raises confidence`() {
        val suggestions = rank(
            categories = setOf("Maps", "Navigation"),
            tokens = setOf("offline", "maps"),
            candidates = listOf(
                candidate(
                    packageName = "org.maps",
                    summary = "Offline maps",
                    categories = listOf("Maps", "Navigation"),
                ),
            ),
        )

        assertEquals(ConfidenceLabel.MEDIUM, suggestions.single().confidence)
        assertTrue(suggestions.single().score >= 6)
    }

    private fun rank(
        categories: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        tokens: Set<String> = emptySet(),
        candidates: List<AlternativeCandidate>,
        excludePackages: Set<String> = emptySet(),
        excludeNames: Set<String> = emptySet(),
    ): List<AlternativeSuggestion> = AlternativeRanker.rank(
        categories = categories,
        tags = tags,
        tokens = tokens,
        candidates = candidates,
        excludePackages = excludePackages,
        excludeNames = excludeNames,
        limit = DEFAULT_MAX_ALTERNATIVES,
    )

    private fun candidate(
        packageName: String,
        name: String = "Open Maps",
        summary: String = "Offline maps",
        categories: List<String> = emptyList(),
    ) = AlternativeCandidate(
        storeId = "store-a",
        storeName = "Store A",
        entry = CatalogEntry(
            packageName = packageName,
            name = name,
            summary = summary,
            categories = categories,
            license = "MIT",
            sourceCode = "https://example.org/$packageName",
        ),
    )
}
