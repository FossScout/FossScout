package com.fossscout.core.matching

private val WORD_SPLIT = Regex("[^a-z0-9]+")

private val STOP_WORDS = setOf(
    "the", "and", "for", "with", "from", "this", "that", "your", "you",
    "app", "apps", "application", "client", "mobile", "free", "open", "source",
    "foss", "floss", "libre", "android", "simple", "light", "lightweight",
)

/** A store entry considered for functional alternatives. */
data class AlternativeCandidate(
    val storeId: String,
    val storeName: String,
    val entry: CatalogEntry,
)

/**
 * Ranks functional alternatives by explainable overlap with the query. Store
 * affiliation never contributes to the score (F03-R6).
 */
object AlternativeRanker {
    fun rank(
        categories: Set<String> = emptySet(),
        tags: Set<String> = emptySet(),
        tokens: Set<String> = emptySet(),
        candidates: List<AlternativeCandidate>,
        excludePackages: Set<String> = emptySet(),
        excludeNames: Set<String> = emptySet(),
        limit: Int = DEFAULT_MAX_ALTERNATIVES,
    ): List<AlternativeSuggestion> {
        if (limit <= 0) return emptyList()

        val queryCategories = categories.mapNotNull(::normalizeLabel).toSet()
        val queryTags = tags.mapNotNull(::normalizeLabel).toSet()
        val queryTokens = tokens.flatMap(::tokenize).toSet()

        val ranked = candidates.mapNotNull { candidate ->
            val entry = candidate.entry
            if (entry.packageName in excludePackages) return@mapNotNull null
            val normalizedName = normalizeLabel(entry.name)
            if (normalizedName != null && normalizedName in excludeNames) {
                // An identically named app with a different package is too close
                // to call a "different app" without stronger evidence.
                return@mapNotNull null
            }

            val entryCategories = entry.categories.mapNotNull(::normalizeLabel).toSet()
            val entryTokens = tokenize(entry.name) + tokenize(entry.summary)

            val categoryMatches = queryCategories.intersect(entryCategories)
            val tagMatches = queryTags.filter { it in entryCategories || it in entryTokens }
            val tokenMatches = queryTokens.intersect(entryTokens)

            val score = categoryMatches.size * 4 +
                tagMatches.size * 3 +
                tokenMatches.size

            if (score <= 0) return@mapNotNull null

            val reasons = buildList {
                categoryMatches.take(2).forEach { category ->
                    add("Same category: $category")
                }
                tagMatches.take(1).forEach { tag ->
                    add("Related to your tag: $tag")
                }
                tokenMatches.take(2).forEach { token ->
                    add("Summary or name mentions: $token")
                }
            }
            if (reasons.isEmpty()) return@mapNotNull null

            AlternativeSuggestion(
                appKey = entry.packageName,
                storeId = candidate.storeId,
                storeName = candidate.storeName,
                packageName = entry.packageName,
                displayName = entry.name,
                summary = entry.summary,
                license = entry.license,
                sourceUrl = entry.sourceCode,
                categories = entry.categories,
                score = score,
                confidence = if (score >= 6) {
                    ConfidenceLabel.MEDIUM
                } else {
                    ConfidenceLabel.LOW
                },
                reasons = reasons,
            )
        }
            .sortedWith(
                compareByDescending<AlternativeSuggestion> { it.score }
                    .thenBy { it.displayName.lowercase() }
                    .thenBy { it.storeId },
            )
            .distinctBy(AlternativeSuggestion::packageName)

        return ranked.take(limit)
    }
}

internal fun normalizeLabel(value: String?): String? {
    val normalized = value
        ?.lowercase()
        ?.replace(WORD_SPLIT, " ")
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()
    return normalized.takeIf(String::isNotEmpty)
}

internal fun tokenize(value: String): Set<String> =
    value.lowercase()
        .split(WORD_SPLIT)
        .asSequence()
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()
