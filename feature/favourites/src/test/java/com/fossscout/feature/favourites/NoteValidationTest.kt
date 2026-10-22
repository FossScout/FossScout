package com.fossscout.feature.favourites

import com.fossscout.core.model.MAX_NOTE_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteValidationTest {

    @Test
    fun `blank note is stored as empty`() {
        assertEquals("", normalizeNote("   "))
    }

    @Test
    fun `note over the limit is rejected`() {
        assertTrue(runCatching { normalizeNote("x".repeat(4_001)) }.isFailure)
    }

    @Test
    fun `note at the limit is accepted`() {
        val note = "x".repeat(MAX_NOTE_LENGTH)

        assertEquals(note, normalizeNote(note))
    }
}
