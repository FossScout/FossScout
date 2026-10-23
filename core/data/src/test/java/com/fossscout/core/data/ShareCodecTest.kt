package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import com.fossscout.core.model.SHARE_SCHEMA_VERSION
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.ShareItem
import com.fossscout.core.model.ShareType
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareCodecTest {
    private val codec = ShareCodec()

    @Test
    fun `json round trip preserves every share field`() {
        val envelope = envelope()

        val decoded = codec.decodeJson(codec.encodeJson(envelope))

        assertEquals(envelope, decoded)
        assertEquals(SHARE_SCHEMA_VERSION, decoded.schemaVersion)
    }

    @Test
    fun `plain text round trip preserves names keys stores tags and notes`() {
        val envelope = envelope()

        val decoded = codec.decodeText(codec.encodeText(envelope))

        assertEquals(envelope, decoded)
    }

    @Test
    fun `json omits null notes and keeps them when present`() {
        val withoutNotes = codec.encodeJson(envelope(note = null))
        assertFalse(withoutNotes.contains("\"note\""))

        val withNotes = codec.encodeJson(envelope(note = "Private thought"))
        assertTrue(withNotes.contains("\"note\""))
        assertEquals("Private thought", codec.decodeJson(withNotes).items.single().note)
    }

    @Test
    fun `plain text is readable without the app`() {
        val text = codec.encodeText(envelope(note = null))

        assertTrue(text.contains("FossScout shared list"))
        assertTrue(text.contains("Name: Privacy tools"))
        assertTrue(text.contains("1. Example"))
        assertTrue(text.contains("App: org.example"))
        assertTrue(text.contains("Store: fdroid"))
        assertTrue(text.contains("Tags: privacy, tools"))
        assertFalse(text.contains("Private thought"))
    }

    @Test
    fun `future json schema version is rejected clearly`() {
        val exception = assertThrows(ShareFormatException::class.java) {
            codec.decodeJson("""{"schemaVersion":99,"type":"collection","name":"Future"}""")
        }

        assertEquals(ShareFormatReason.UNSUPPORTED_VERSION, exception.reason)
        assertTrue(exception.message.orEmpty().contains("99"))
    }

    @Test
    fun `future plain text schema version is rejected clearly`() {
        val exception = assertThrows(ShareFormatException::class.java) {
            codec.decodeText(
                """
                FossScout shared list
                Schema: 99
                Type: collection
                Name: Future
                """.trimIndent(),
            )
        }

        assertEquals(ShareFormatReason.UNSUPPORTED_VERSION, exception.reason)
    }

    @Test
    fun `missing schema version is malformed not silently accepted`() {
        val exception = assertThrows(ShareFormatException::class.java) {
            codec.decodeJson("""{"type":"collection","name":"No version"}""")
        }

        assertEquals(ShareFormatReason.MALFORMED, exception.reason)
    }

    @Test
    fun `malformed json is rejected`() {
        val exception = assertThrows(ShareFormatException::class.java) {
            codec.decodeJson("""{"schemaVersion":1,"type":""")
        }

        assertEquals(ShareFormatReason.MALFORMED, exception.reason)
    }

    @Test
    fun `duplicate app keys are accepted by the codec and deduplicated by preview`() {
        val duplicate = ShareEnvelope(
            schemaVersion = SHARE_SCHEMA_VERSION,
            type = ShareType.ALL_FAVOURITES,
            name = "Duplicates",
            items = listOf(
                ShareItem(AppKey("org.example"), "Example"),
                ShareItem(AppKey("org.example"), "Example again"),
            ),
        )

        assertEquals(2, codec.decodeJson(codec.encodeJson(duplicate)).items.size)
    }

    @Test
    fun `single favourite share requires exactly one item`() {
        val invalid = ShareEnvelope(
            schemaVersion = SHARE_SCHEMA_VERSION,
            type = ShareType.SINGLE_FAVOURITE,
            name = "Example",
            items = emptyList(),
        )

        assertThrows(IllegalArgumentException::class.java) {
            codec.encodeJson(invalid)
        }
    }

    @Test
    fun `decode autodetects json and text`() {
        val envelope = envelope()
        assertEquals(envelope, codec.decode(codec.encodeJson(envelope)))
        assertEquals(envelope, codec.decode(codec.encodeText(envelope)))
    }

    @Test
    fun `text with an item count mismatch is rejected`() {
        val text = codec.encodeText(envelope()).replace("Items: 1", "Items: 2")

        val exception = assertThrows(ShareFormatException::class.java) {
            codec.decodeText(text)
        }

        assertEquals(ShareFormatReason.MALFORMED, exception.reason)
    }

    @Test
    fun `unknown note fields stay null after round trip`() {
        val decoded = codec.decode(codec.encodeText(envelope(note = null)))
        assertNull(decoded.items.single().note)
    }

    private fun envelope(note: String? = "Private thought") = ShareEnvelope(
        schemaVersion = SHARE_SCHEMA_VERSION,
        type = ShareType.COLLECTION,
        name = "Privacy tools",
        description = "Tools I recommend",
        collectionId = "privacy",
        items = listOf(
            ShareItem(
                appKey = AppKey("org.example"),
                displayName = "Example",
                preferredStoreId = StoreId("fdroid"),
                tags = setOf("privacy", "tools"),
                note = note,
            ),
        ),
        exportedAtEpochMillis = 123L,
    )
}
