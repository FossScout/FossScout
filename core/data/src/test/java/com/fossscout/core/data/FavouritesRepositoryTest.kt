package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesRepositoryTest {
    private val store = FakeFavouritesStore()
    private val repository = FavouritesRepository(store, now = { 100L })

    @Test
    fun `adding a favourite does not require installation`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        assertEquals(listOf("org.example"), repository.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `favourite survives repository recreation`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        val recreated = FavouritesRepository(store, now = { 200L })
        assertEquals(listOf("org.example"), recreated.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `adding the same app twice keeps one entry and preserves the note`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        repository.updateNote(AppKey("org.example"), "try this")
        repository.add(AppKey("org.example"), "Example renamed")
        val favourite = repository.favourites.first().single()
        assertEquals("try this", favourite.note)
        assertEquals("Example renamed", favourite.displayName)
    }

    @Test
    fun `removing a favourite removes only that app`() = runTest {
        repository.add(AppKey("a"), "A")
        repository.add(AppKey("b"), "B")
        repository.remove(AppKey("a"))
        assertEquals(listOf("b"), repository.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `note update is capped`() = runTest {
        repository.add(AppKey("a"), "A")
        val result = runCatching { repository.updateNote(AppKey("a"), "x".repeat(4_001)) }
        assertTrue(result.isFailure)
    }

    @Test
    fun `adding an existing favourite preserves its preferred store when none is supplied`() = runTest {
        repository.add(AppKey("a"), "A", preferredStoreId = StoreId("fdroid"))
        repository.add(AppKey("a"), "A renamed")

        assertEquals(
            StoreId("fdroid"),
            repository.favourites.first().single().preferredStoreId,
        )
    }

    @Test
    fun `new favourite captures one timestamp for creation and update`() = runTest {
        var tick = 0L
        val repository = FavouritesRepository(store, now = { tick++ })

        val favourite = repository.add(AppKey("a"), "A")

        assertEquals(favourite.createdAtEpochMillis, favourite.updatedAtEpochMillis)
    }

    @Test
    fun `add does not overwrite data that could not be read`() = runTest {
        val unreadableStore = FakeFavouritesStore(
            readError = FavouritesFormatException("corrupt"),
        )
        val repository = FavouritesRepository(unreadableStore)

        val result = runCatching { repository.add(AppKey("a"), "A") }

        assertTrue(result.isFailure)
        assertEquals(0, unreadableStore.saveCalls)
    }
}
