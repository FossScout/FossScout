package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.SHARE_SCHEMA_VERSION
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.ShareItem
import com.fossscout.core.model.ShareType
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareImportServiceTest {
    private val favouritesStore = FakeFavouritesStore()
    private val collectionsStore = FakeCollectionsStore()
    private var tick = 0L
    private val favouritesRepository = FavouritesRepository(favouritesStore, now = { tick++ })
    private val collectionsRepository = CollectionsRepository(
        store = collectionsStore,
        now = { tick++ },
        idFactory = { "imported-${tick}" },
    )
    private val service = ShareImportService(
        favouritesRepository = favouritesRepository,
        collectionsRepository = collectionsRepository,
        now = { 1_000L },
        collectionIdFactory = { "generated-collection" },
    )

    @Test
    fun `preview does not mutate favourites or collections`() = runTest {
        favouritesRepository.add(AppKey("existing"), "Existing")
        collectionsRepository.create("Local collection")
        val favouritesWrites = favouritesStore.saveCalls
        val collectionWrites = collectionsStore.saveCalls

        val preview = service.preview(
            envelope = envelope(
                type = ShareType.COLLECTION,
                items = listOf(
                    shareItem("existing", "Existing renamed"),
                    shareItem("new", "New"),
                ),
            ),
            mode = ImportConflictMode.MERGE,
            knownStoreIds = setOf(StoreId("fdroid")),
        )

        assertEquals(ImportItemAction.MERGE, preview.items.first().action)
        assertEquals(ImportItemAction.ADD, preview.items.last().action)
        assertEquals(favouritesWrites, favouritesStore.saveCalls)
        assertEquals(collectionWrites, collectionsStore.saveCalls)
        assertEquals(listOf("existing"), favouritesRepository.favourites.first().map {
            it.appKey.value
        })
        assertEquals(1, collectionsRepository.collections.first().size)
    }

    @Test
    fun `merge adds new entries and unions tags while keeping local note`() = runTest {
        favouritesRepository.add(
            AppKey("existing"),
            "Existing",
            preferredStoreId = StoreId("fdroid"),
        )
        favouritesRepository.updateDetails(
            AppKey("existing"),
            note = "local note",
            tags = listOf("local"),
            preferredStoreId = StoreId("fdroid"),
        )

        service.apply(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(
                    shareItem("existing", "Shared name", note = "shared note", tags = setOf("shared")),
                    shareItem("new", "New app"),
                ),
            ),
            mode = ImportConflictMode.MERGE,
            includeNotes = false,
            knownStoreIds = setOf(StoreId("fdroid")),
        )

        val favourites = favouritesRepository.favourites.first()
        val existing = favourites.first { it.appKey == AppKey("existing") }
        assertEquals("Shared name", existing.displayName)
        assertEquals("local note", existing.note)
        assertEquals(setOf("local", "shared"), existing.tags)
        assertEquals(StoreId("fdroid"), existing.preferredStoreId)
        assertEquals("New app", favourites.first { it.appKey == AppKey("new") }.displayName)
    }

    @Test
    fun `skip leaves conflicts untouched and still adds new entries`() = runTest {
        favouritesRepository.add(AppKey("existing"), "Existing")
        favouritesRepository.updateNote(AppKey("existing"), "local note")

        service.apply(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(
                    shareItem("existing", "Shared name", note = "shared note"),
                    shareItem("new", "New app"),
                ),
            ),
            mode = ImportConflictMode.SKIP,
            includeNotes = true,
            knownStoreIds = setOf(StoreId("fdroid")),
        )

        val favourites = favouritesRepository.favourites.first()
        val existing = favourites.first { it.appKey == AppKey("existing") }
        assertEquals("Existing", existing.displayName)
        assertEquals("local note", existing.note)
        assertEquals(2, favourites.size)
    }

    @Test
    fun `replace overwrites details and all-favourites replace removes absent entries`() = runTest {
        favouritesRepository.add(AppKey("existing"), "Existing")
        favouritesRepository.add(AppKey("removed"), "Removed")
        val createdAt = favouritesRepository.favourites.first()
            .first { it.appKey == AppKey("existing") }
            .createdAtEpochMillis

        service.apply(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(shareItem("existing", "Replaced", tags = setOf("new"))),
            ),
            mode = ImportConflictMode.REPLACE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        val favourite = favouritesRepository.favourites.first().single()
        assertEquals("Replaced", favourite.displayName)
        assertEquals(setOf("new"), favourite.tags)
        assertEquals(createdAt, favourite.createdAtEpochMillis)
    }

    @Test
    fun `imported notes are dropped by default and kept only with consent`() = runTest {
        service.apply(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(shareItem("one", "One", note = "shared note")),
            ),
            mode = ImportConflictMode.MERGE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )
        assertEquals("", favouritesRepository.favourites.first().single().note)

        service.apply(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(shareItem("two", "Two", note = "kept note")),
            ),
            mode = ImportConflictMode.MERGE,
            includeNotes = true,
            knownStoreIds = emptySet(),
        )
        assertEquals(
            "kept note",
            favouritesRepository.favourites.first()
                .first { it.appKey == AppKey("two") }
                .note,
        )
    }

    @Test
    fun `unknown stores are reported and never applied or added`() = runTest {
        val preview = service.preview(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(
                    shareItem("new", "New", store = StoreId("unknown-store")),
                ),
            ),
            mode = ImportConflictMode.MERGE,
            knownStoreIds = setOf(StoreId("fdroid")),
        )
        assertTrue(preview.items.single().unknownStore)
        assertEquals(listOf(StoreId("unknown-store")), preview.unknownStores)

        service.apply(
            envelope = preview.envelope,
            mode = preview.mode,
            includeNotes = false,
            knownStoreIds = setOf(StoreId("fdroid")),
        )
        assertNull(favouritesRepository.favourites.first().single().preferredStoreId)
    }

    @Test
    fun `collection import creates ordered collection and imports its items`() = runTest {
        val envelope = envelope(
            type = ShareType.COLLECTION,
            collectionId = null,
            name = "Imported",
            description = "From a friend",
            items = listOf(shareItem("b", "B"), shareItem("a", "A")),
        )

        service.apply(
            envelope = envelope,
            mode = ImportConflictMode.MERGE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        val collection = collectionsRepository.collections.first().single()
        assertEquals("generated-collection", collection.id)
        assertEquals(listOf("b", "a"), collection.itemKeys.map(AppKey::value))
        assertEquals(listOf("b", "a"), favouritesRepository.favourites.first().map {
            it.appKey.value
        })
    }

    @Test
    fun `collection merge adds missing items and preserves local metadata`() = runTest {
        val local = collectionsRepository.create(
            name = "Local name",
            description = "Local description",
            id = "shared-id",
            itemKeys = listOf(AppKey("a")),
        )

        service.apply(
            envelope = envelope(
                type = ShareType.COLLECTION,
                collectionId = "shared-id",
                name = "Shared name",
                description = "Shared description",
                items = listOf(shareItem("a", "A"), shareItem("b", "B")),
            ),
            mode = ImportConflictMode.MERGE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        val collection = collectionsRepository.collections.first().single()
        assertEquals("Local name", collection.name)
        assertEquals("Local description", collection.description)
        assertEquals(local.createdAtEpochMillis, collection.createdAtEpochMillis)
        assertEquals(listOf("a", "b"), collection.itemKeys.map(AppKey::value))
    }

    @Test
    fun `collection replace overwrites metadata and item order`() = runTest {
        collectionsRepository.create(
            name = "Local name",
            id = "shared-id",
            itemKeys = listOf(AppKey("a")),
        )

        service.apply(
            envelope = envelope(
                type = ShareType.COLLECTION,
                collectionId = "shared-id",
                name = "Shared name",
                description = "Shared description",
                items = listOf(shareItem("b", "B")),
            ),
            mode = ImportConflictMode.REPLACE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        val collection = collectionsRepository.collections.first().single()
        assertEquals("Shared name", collection.name)
        assertEquals("Shared description", collection.description)
        assertEquals(listOf("b"), collection.itemKeys.map(AppKey::value))
    }

    @Test
    fun `collection skip leaves existing collection untouched`() = runTest {
        collectionsRepository.create(
            name = "Local name",
            id = "shared-id",
            itemKeys = listOf(AppKey("a")),
        )

        service.apply(
            envelope = envelope(
                type = ShareType.COLLECTION,
                collectionId = "shared-id",
                name = "Shared name",
                items = listOf(shareItem("b", "B")),
            ),
            mode = ImportConflictMode.SKIP,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        val collection = collectionsRepository.collections.first().single()
        assertEquals("Local name", collection.name)
        assertEquals(listOf("a"), collection.itemKeys.map(AppKey::value))
    }

    @Test
    fun `preview counts conflicts and all-favourites removals`() = runTest {
        favouritesRepository.add(AppKey("existing"), "Existing")
        favouritesRepository.add(AppKey("removed"), "Removed")

        val preview = service.preview(
            envelope = envelope(
                type = ShareType.ALL_FAVOURITES,
                items = listOf(shareItem("existing", "Existing")),
            ),
            mode = ImportConflictMode.REPLACE,
            knownStoreIds = emptySet(),
        )

        assertEquals(1, preview.replacedFavouriteCount)
        assertEquals(1, preview.removedFavouriteCount)
        assertEquals(0, preview.addedFavouriteCount)
    }

    @Test
    fun `collection import never deletes an unrelated collection`() = runTest {
        collectionsRepository.create(name = "Unrelated", id = "keep-me")

        service.apply(
            envelope = envelope(
                type = ShareType.COLLECTION,
                collectionId = "new-id",
                name = "New",
                items = listOf(shareItem("a", "A")),
            ),
            mode = ImportConflictMode.REPLACE,
            includeNotes = false,
            knownStoreIds = emptySet(),
        )

        assertEquals(
            setOf("keep-me", "new-id"),
            collectionsRepository.collections.first().map(AppCollection::id).toSet(),
        )
    }

    private fun envelope(
        type: ShareType,
        items: List<ShareItem>,
        collectionId: String? = "shared-id",
        name: String = "Shared list",
        description: String = "",
    ) = ShareEnvelope(
        schemaVersion = SHARE_SCHEMA_VERSION,
        type = type,
        name = name,
        description = description,
        collectionId = collectionId,
        items = items,
    )

    private fun shareItem(
        key: String,
        name: String,
        note: String? = "shared note",
        tags: Set<String> = setOf("shared"),
        store: StoreId? = StoreId("fdroid"),
    ) = ShareItem(
        appKey = AppKey(key),
        displayName = name,
        preferredStoreId = store,
        tags = tags,
        note = note,
    )
}
