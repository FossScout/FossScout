package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.FAVOURITES_SCHEMA_VERSION
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FavouritesCodecTest {
    private val codec = FavouritesCodec()

    @Test
    fun `round trip preserves favourites and schema version`() {
        val favourites = listOf(
            Favourite(
                appKey = AppKey("org.example"),
                displayName = "Example",
                note = "Try this",
                tags = setOf("privacy", "tools"),
                preferredStoreId = StoreId("fdroid"),
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
            ),
        )

        val decoded = codec.decode(codec.encode(favourites))

        assertEquals(favourites, decoded)
        assertEquals(FAVOURITES_SCHEMA_VERSION, decoded.single().schemaVersion)
        assertEquals(
            StoreId("fdroid"),
            decoded.single().preferredStoreId,
        )
        assertEquals(setOf("privacy", "tools"), decoded.single().tags)
    }

    @Test
    fun `corrupt json raises a typed format error`() {
        assertThrows(FavouritesFormatException::class.java) {
            codec.decode("""{"schemaVersion":1,"favourites":[""")
        }
    }

    @Test
    fun `unversioned bare list is rejected`() {
        assertThrows(FavouritesFormatException::class.java) {
            codec.decode("[]")
        }
    }

    @Test
    fun `missing envelope schema version is rejected`() {
        assertThrows(FavouritesFormatException::class.java) {
            codec.decode("""{"favourites":[]}""")
        }
    }

    @Test
    fun `unknown envelope schema version is rejected`() {
        assertThrows(FavouritesFormatException::class.java) {
            codec.decode("""{"schemaVersion":99,"favourites":[]}""")
        }
    }

    @Test
    fun `unknown favourite schema version is rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "favourites": [{
                "appKey": "org.example",
                "displayName": "Example",
                "createdAtEpochMillis": 1,
                "updatedAtEpochMillis": 2,
                "schemaVersion": 99
              }]
            }
        """.trimIndent()

        assertThrows(FavouritesFormatException::class.java) {
            codec.decode(encoded)
        }
    }
}
