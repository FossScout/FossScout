package com.fossscout.app.profile

import com.fossscout.core.feature.Capability
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileFeaturesTest {
    @Test
    fun `trust badges are enabled only outside Play`() {
        val profile = ProfileFeatures.profile
        val expected = profile.name != "playstore"

        assertEquals(expected, Capability.STORE_TRUST_BADGES in profile.capabilities)
    }
}
