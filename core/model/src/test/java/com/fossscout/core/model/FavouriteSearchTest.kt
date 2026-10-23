package com.fossscout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteSearchTest {
    private val signal = Favourite(
        appKey = AppKey("org.thoughtcrime.securesms"),
        displayName = "Signal",
        note = "Private messaging for the family",
        tags = setOf("privacy", "messaging"),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
    private val joplin = Favourite(
        appKey = AppKey("net.cozic.joplin"),
        displayName = "Joplin",
        note = "Encrypted markdown notes",
        tags = setOf("notes", "privacy"),
        createdAtEpochMillis = 2L,
        updatedAtEpochMillis = 2L,
    )
    private val favourites = listOf(signal, joplin)

    @Test
    fun `blank query keeps every favourite`() {
        assertEquals(favourites, favourites.filterFavourites())
        assertEquals(favourites, favourites.filterFavourites(query = "   "))
    }

    @Test
    fun `query matches display name notes and tags`() {
        assertEquals(listOf(signal), favourites.filterFavourites("signal"))
        assertEquals(listOf(joplin), favourites.filterFavourites("markdown"))
        assertEquals(listOf(signal), favourites.filterFavourites("messaging"))
    }

    @Test
    fun `query that matches nothing returns an empty list`() {
        assertTrue(favourites.filterFavourites("qwerty").isEmpty())
    }

    @Test
    fun `tag filter combines with free text`() {
        assertEquals(
            listOf(joplin),
            favourites.filterFavourites(query = "encrypted", tag = "privacy"),
        )
        assertEquals(
            emptyList<Favourite>(),
            favourites.filterFavourites(query = "signal", tag = "notes"),
        )
    }

    @Test
    fun `tag filter accepts hash prefixes and mixed case`() {
        assertEquals(listOf(signal, joplin), favourites.filterFavourites(tag = "#Privacy"))
    }

    @Test
    fun `all tags are distinct and sorted`() {
        assertEquals(
            listOf("messaging", "notes", "privacy"),
            favourites.favouriteTags(),
        )
    }
}
