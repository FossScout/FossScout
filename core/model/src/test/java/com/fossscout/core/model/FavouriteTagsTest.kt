package com.fossscout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FavouriteTagsTest {
    @Test
    fun `tags are trimmed lowercased and stripped of hash marks`() {
        val tags = normalizeTags(listOf("  Privacy ", "#Android", "privacy", "#  Open Source"))

        assertEquals(listOf("privacy", "android", "open source"), tags.toList())
    }

    @Test
    fun `duplicate and blank tags collapse to one canonical set`() {
        val tags = normalizeTags(listOf("privacy", "PRIVACY", "  ", "#", "\t"))

        assertEquals(setOf("privacy"), tags)
    }

    @Test
    fun `blank single tag returns null`() {
        assertNull(normalizeTag("  #  "))
    }

    @Test
    fun `overlong tags and too many tags are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeTag("x".repeat(MAX_TAG_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            normalizeTags((1..MAX_TAGS + 1).map { "tag$it" })
        }
    }

    @Test
    fun `favourite validation enforces tag limits`() {
        val favourite = Favourite(
            appKey = AppKey("org.example"),
            displayName = "Example",
            tags = (1..MAX_TAGS + 1).map { "tag$it" }.toSet(),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )

        assertThrows(IllegalArgumentException::class.java) { favourite.validate() }
    }
}
