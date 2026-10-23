package com.fossscout.core.model

/**
 * Local search over a favourite's display name, private note, tags, and app key.
 * Search never leaves the device and does not inspect installed state.
 */
fun Favourite.matchesFavouriteQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return displayName.contains(needle, ignoreCase = true) ||
        note.contains(needle, ignoreCase = true) ||
        tags.any { it.contains(needle, ignoreCase = true) } ||
        appKey.value.contains(needle, ignoreCase = true)
}

/** Applies a free-text query and an optional exact tag filter. */
fun List<Favourite>.filterFavourites(
    query: String = "",
    tag: String? = null,
): List<Favourite> {
    val selectedTag = tag?.trim()?.removePrefix("#")?.lowercase()
    return filter { favourite ->
        val matchesTag = selectedTag.isNullOrEmpty() ||
            favourite.tags.any { it.equals(selectedTag, ignoreCase = true) }
        matchesTag && favourite.matchesFavouriteQuery(query)
    }
}

/** Distinct tags across favourites, ordered for a stable filter row. */
fun List<Favourite>.favouriteTags(): List<String> =
    flatMap { it.tags }
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
