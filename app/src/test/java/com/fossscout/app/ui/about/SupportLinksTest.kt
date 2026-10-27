package com.fossscout.app.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportLinksTest {
    @Test
    fun `blank url keeps the support section hidden`() {
        assertNull(SupportLinks.validatedLiberapayUrl(""))
        assertNull(SupportLinks.validatedLiberapayUrl("   "))
    }

    @Test
    fun `verified liberapay url is accepted and trimmed`() {
        assertEquals(
            "https://liberapay.com/FossScout/",
            SupportLinks.validatedLiberapayUrl(" https://liberapay.com/FossScout/ "),
        )
    }

    @Test
    fun `non https url is rejected`() {
        assertNull(SupportLinks.validatedLiberapayUrl("http://liberapay.com/FossScout/"))
    }

    @Test
    fun `non liberapay host is rejected`() {
        assertNull(SupportLinks.validatedLiberapayUrl("https://example.com/FossScout/"))
    }

    @Test
    fun `root url is rejected`() {
        assertNull(SupportLinks.validatedLiberapayUrl("https://liberapay.com/"))
    }

    @Test
    fun `query and fragment are rejected`() {
        assertNull(SupportLinks.validatedLiberapayUrl("https://liberapay.com/FossScout/?ref=app"))
        assertNull(SupportLinks.validatedLiberapayUrl("https://liberapay.com/FossScout/#support"))
    }

    @Test
    fun `credentials and explicit port are rejected`() {
        assertNull(SupportLinks.validatedLiberapayUrl("https://user@liberapay.com/FossScout/"))
        assertNull(SupportLinks.validatedLiberapayUrl("https://liberapay.com:8443/FossScout/"))
    }
}
