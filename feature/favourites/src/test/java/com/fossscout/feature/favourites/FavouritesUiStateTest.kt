package com.fossscout.feature.favourites

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesUiStateTest {
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
        preferredStoreId = StoreId("fdroid"),
        createdAtEpochMillis = 2L,
        updatedAtEpochMillis = 2L,
    )
    private val state = FavouritesUiState(
        favourites = listOf(signal, joplin),
        storeOptions = listOf(FavouriteStoreOption(StoreId("fdroid"), "F-Droid")),
        isLoading = false,
    )

    @Test
    fun `empty search shows every favourite`() {
        assertEquals(listOf(signal, joplin), state.visibleFavourites)
        assertFalse(state.hasActiveFilters)
    }

    @Test
    fun `search matches display name note and tags`() {
        assertEquals(listOf(signal), state.copy(query = "signal").visibleFavourites)
        assertEquals(listOf(joplin), state.copy(query = "markdown").visibleFavourites)
        assertEquals(
            listOf(signal, joplin),
            state.copy(query = "privacy").visibleFavourites,
        )
    }

    @Test
    fun `search with no matches is empty and exposes the filtered state`() {
        val filtered = state.copy(query = "does-not-exist")

        assertTrue(filtered.visibleFavourites.isEmpty())
        assertTrue(filtered.hasActiveFilters)
    }

    @Test
    fun `tag filter narrows the list`() {
        val filtered = state.copy(selectedTag = "notes")

        assertEquals(listOf(joplin), filtered.visibleFavourites)
        assertEquals(
            emptyList<Favourite>(),
            filtered.copy(query = "signal").visibleFavourites,
        )
    }

    @Test
    fun `all tags are sorted and distinct`() {
        assertEquals(
            listOf("messaging", "notes", "privacy"),
            state.tags,
        )
    }

    @Test
    fun `preferred store name is resolved when the registry knows it`() {
        assertEquals("F-Droid", state.storeName(StoreId("fdroid")))
        assertNull(state.storeName(null))
        assertNull(state.storeName(StoreId("deleted-store")))
    }
}
