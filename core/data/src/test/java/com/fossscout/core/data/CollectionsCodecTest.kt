package com.fossscout.core.data

import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.COLLECTION_SCHEMA_VERSION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsCodecTest {
    private val codec = CollectionsCodec()

    @Test
    fun `round trip preserves ordered items and schema version`() {
        val collection = AppCollection(
            id = "privacy",
            name = "Privacy tools",
            description = "Private by default",
            itemKeys = listOf(AppKey("org.example.a"), AppKey("org.example.b")),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
        )

        val decoded = codec.decode(codec.encode(listOf(collection)))

        assertEquals(listOf(collection), decoded)
        assertEquals(COLLECTION_SCHEMA_VERSION, decoded.single().schemaVersion)
        assertEquals(
            listOf("org.example.a", "org.example.b"),
            decoded.single().itemKeys.map(AppKey::value),
        )
    }

    @Test
    fun `future envelope version is rejected`() {
        val exception = assertThrows(CollectionsFormatException::class.java) {
            codec.decode("""{"schemaVersion":99,"collections":[]}""")
        }
        assertTrue(exception.message.orEmpty().contains("99"))
    }

    @Test
    fun `future item schema version is rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "collections": [{
                "id": "one",
                "name": "One",
                "createdAtEpochMillis": 1,
                "updatedAtEpochMillis": 2,
                "schemaVersion": 99
              }]
            }
        """.trimIndent()

        assertThrows(CollectionsFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `duplicate item keys are rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "collections": [{
                "id": "one",
                "name": "One",
                "itemKeys": ["org.example", "org.example"],
                "createdAtEpochMillis": 1,
                "updatedAtEpochMillis": 2
              }]
            }
        """.trimIndent()

        assertThrows(CollectionsFormatException::class.java) {
            codec.decode(encoded)
        }
    }

    @Test
    fun `malformed data raises a typed error`() {
        assertThrows(CollectionsFormatException::class.java) {
            codec.decode("""{"schemaVersion":1,"collections":[""")
        }
    }
}
