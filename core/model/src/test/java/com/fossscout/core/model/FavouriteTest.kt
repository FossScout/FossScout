package com.fossscout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FavouriteTest {
    @Test
    fun `app key rejects blank values`() {
        assertThrows(IllegalArgumentException::class.java) { AppKey(" ") }
    }

    @Test
    fun `note length is capped`() {
        val favourite = Favourite(
            appKey = AppKey("org.example"),
            displayName = "Example",
            note = "x".repeat(MAX_NOTE_LENGTH + 1),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        assertThrows(IllegalArgumentException::class.java) { favourite.validate() }
    }
}
