package com.fossscout.feature.favourites

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
}
