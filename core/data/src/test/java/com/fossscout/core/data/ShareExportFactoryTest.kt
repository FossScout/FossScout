package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.ShareType
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareExportFactoryTest {
    private val factory = ShareExportFactory(now = { 500L })

    @Test
    fun `single favourite excludes notes by default`() {
        val envelope = factory.forFavourite(favourite(note = "secret"))

        assertEquals(ShareType.SINGLE_FAVOURITE, envelope.type)
        assertNull(envelope.items.single().note)
        assertEquals("Example", envelope.items.single().displayName)
    }

    @Test
    fun `single favourite includes notes only with explicit consent`() {
        val envelope = factory.forFavourite(favourite(note = "secret"), includeNotes = true)

        assertEquals("secret", envelope.items.single().note)
    }

    @Test
    fun `collection export keeps item order and references canonical keys`() {
        val collection = AppCollection(
            id = "privacy",
            name = "Privacy tools",
            description = "Recommended",
            itemKeys = listOf(AppKey("b"), AppKey("a")),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        val envelope = factory.forCollection(
            collection = collection,
            favourites = listOf(favourite(AppKey("a"), "A"), favourite(AppKey("b"), "B")),
        )

        assertEquals(ShareType.COLLECTION, envelope.type)
        assertEquals("privacy", envelope.collectionId)
        assertEquals(listOf("B", "A"), envelope.items.map { it.displayName })
        assertEquals(
            listOf("b", "a"),
            envelope.items.map { it.appKey.value },
        )
    }

    @Test
    fun `collection items that are not favourites remain importable by key`() {
        val collection = AppCollection(
            id = "mixed",
            name = "Mixed",
            itemKeys = listOf(AppKey("known"), AppKey("unknown")),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        val envelope = factory.forCollection(
            collection = collection,
            favourites = listOf(favourite(AppKey("known"), "Known")),
        )

        assertEquals(listOf("Known", "unknown"), envelope.items.map { it.displayName })
    }

    @Test
    fun `all favourites excludes notes by default and can include them`() {
        val favourites = listOf(
            favourite(note = "one"),
            favourite(AppKey("two"), "Two", note = "two"),
        )

        val withoutNotes = factory.forAllFavourites(favourites)
        val withNotes = factory.forAllFavourites(favourites, includeNotes = true)

        assertEquals(ShareType.ALL_FAVOURITES, withoutNotes.type)
        assertTrue(withoutNotes.items.all { it.note == null })
        assertEquals(listOf("one", "two"), withNotes.items.map { it.note })
    }

    @Test
    fun `file names are stable and safe`() {
        assertEquals("privacy-tools", shareFileBaseName("Privacy tools!"))
        assertEquals("fossscout-list", shareFileBaseName("   "))
    }

    private fun favourite(
        key: AppKey = AppKey("org.example"),
        name: String = "Example",
        note: String = "",
    ) = Favourite(
        appKey = key,
        displayName = name,
        preferredStoreId = StoreId("fdroid"),
        note = note,
        tags = setOf("privacy"),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
    )
}
