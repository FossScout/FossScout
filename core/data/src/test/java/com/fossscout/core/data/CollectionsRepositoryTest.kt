package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsRepositoryTest {
    private val store = FakeCollectionsStore()
    private var tick = 0L
    private val repository = CollectionsRepository(
        store = store,
        now = { tick++ },
        idFactory = { "collection-${tick}" },
    )

    @Test
    fun `create rename and description edit persist`() = runTest {
        val created = repository.create(
            name = "  Privacy  ",
            description = "  Private tools  ",
        )

        repository.rename(created.id, "Privacy picks")
        repository.updateDescription(created.id, "Updated")

        val collection = repository.collections.first().single()
        assertEquals("Privacy picks", collection.name)
        assertEquals("Updated", collection.description)
        assertEquals("Privacy", created.name)
    }

    @Test
    fun `items are unique and ordered`() = runTest {
        val collection = repository.create("Tools")

        assertTrue(repository.addItem(collection.id, AppKey.from(" org.example.a ")))
        assertTrue(repository.addItem(collection.id, AppKey("org.example.b")))
        assertTrue(!repository.addItem(collection.id, AppKey("org.example.a")))

        assertEquals(
            listOf("org.example.a", "org.example.b"),
            repository.collections.first().single().itemKeys.map(AppKey::value),
        )
    }

    @Test
    fun `reordered items survive repository recreation`() = runTest {
        val collection = repository.create("Tools")
        repository.addItem(collection.id, AppKey("a"))
        repository.addItem(collection.id, AppKey("b"))
        repository.addItem(collection.id, AppKey("c"))

        repository.reorder(collection.id, listOf(AppKey("c"), AppKey("a"), AppKey("b")))

        val recreated = CollectionsRepository(store, now = { 100L })
        assertEquals(
            listOf("c", "a", "b"),
            recreated.collections.first().single().itemKeys.map(AppKey::value),
        )
    }

    @Test
    fun `reorder rejects a partial or foreign item set`() = runTest {
        val collection = repository.create("Tools")
        repository.addItem(collection.id, AppKey("a"))
        repository.addItem(collection.id, AppKey("b"))

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.reorder(collection.id, listOf(AppKey("a")))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.reorder(collection.id, listOf(AppKey("a"), AppKey("foreign")))
            }
        }
    }

    @Test
    fun `move changes order and reports boundaries`() = runTest {
        val collection = repository.create("Tools")
        repository.addItem(collection.id, AppKey("a"))
        repository.addItem(collection.id, AppKey("b"))

        assertTrue(repository.move(collection.id, AppKey("b"), -1))
        assertEquals(
            listOf("b", "a"),
            repository.collections.first().single().itemKeys.map(AppKey::value),
        )
        assertTrue(!repository.move(collection.id, AppKey("b"), -1))
    }

    @Test
    fun `removing an item does not delete the favourite`() = runTest {
        val favouritesStore = FakeFavouritesStore()
        val favourites = FavouritesRepository(favouritesStore)
        favourites.add(AppKey("org.example"), "Example")
        val collectionRepository = CollectionsRepository(store)
        val collection = collectionRepository.create("Tools")
        collectionRepository.addItem(collection.id, AppKey("org.example"))

        collectionRepository.removeItem(collection.id, AppKey("org.example"))

        assertTrue(collectionRepository.collections.first().single().itemKeys.isEmpty())
        assertEquals(1, favourites.favourites.first().size)
    }

    @Test
    fun `deleting a collection keeps every favourite`() = runTest {
        val favouritesStore = FakeFavouritesStore()
        val favourites = FavouritesRepository(favouritesStore)
        favourites.add(AppKey("org.example"), "Example")
        val collectionRepository = CollectionsRepository(store)
        val collection = collectionRepository.create("Tools")
        collectionRepository.addItem(collection.id, AppKey("org.example"))

        collectionRepository.delete(collection.id)

        assertTrue(collectionRepository.collections.first().isEmpty())
        assertEquals(
            listOf(AppKey("org.example")),
            favourites.favourites.first().map(Favourite::appKey),
        )
    }

    @Test
    fun `deleting one collection leaves the others`() = runTest {
        val first = repository.create("First")
        val second = repository.create("Second")

        repository.delete(first.id)

        assertEquals(listOf(second.id), repository.collections.first().map { it.id })
    }

    @Test
    fun `unknown collection raises without a write`() = runTest {
        val result = runCatching { repository.rename("missing", "New") }

        assertTrue(result.isFailure)
        assertEquals(0, store.saveCalls)
    }

    @Test
    fun `add does not overwrite data that could not be read`() = runTest {
        val unreadable = FakeCollectionsStore(
            readError = CollectionsFormatException("corrupt"),
        )
        val repository = CollectionsRepository(unreadable)

        val result = runCatching { repository.create("Tools") }

        assertTrue(result.isFailure)
        assertEquals(0, unreadable.saveCalls)
    }
}
