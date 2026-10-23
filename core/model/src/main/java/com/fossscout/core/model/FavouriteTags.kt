package com.fossscout.core.model

/** A single tag is short, lowercase, and free of surrounding separators. */
const val MAX_TAG_LENGTH = 32

/** Keeps the editor and persisted favourite compact. */
const val MAX_TAGS = 12

/**
 * Normalizes one user-entered tag. Leading `#` markers and surrounding
 * whitespace are removed, and tags are compared case-insensitively. Blank
 * values return null so callers can ignore them.
 */
fun normalizeTag(raw: String): String? {
    val normalized = raw.trim()
        .removePrefix("#")
        .trim()
        .lowercase()
    if (normalized.isEmpty()) return null
    require(normalized.length <= MAX_TAG_LENGTH) { "Tag is too long" }
    return normalized
}

/**
 * Normalizes a tag collection by trimming, lowercasing, removing blanks, and
 * collapsing duplicates. The result keeps first-seen order.
 */
fun normalizeTags(rawTags: Iterable<String>): Set<String> {
    val normalized = linkedSetOf<String>()
    rawTags.forEach { raw ->
        normalizeTag(raw)?.let(normalized::add)
    }
    require(normalized.size <= MAX_TAGS) { "Too many tags" }
    return normalized
}
