package com.fossscout.core.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInStoresTest {
    @Test
    fun `fresh registry lists F-Droid and IzzyOnDroid with verified data`() {
        val registry = BuiltInStores.registry()

        assertEquals(
            listOf(BuiltInStores.FDROID_ID, BuiltInStores.IZZY_ON_DROID_ID),
            registry.stores.map { it.id.value },
        )
        val fdroid = registry.stores.first { it.id.value == BuiltInStores.FDROID_ID }
        val izzy = registry.stores.first { it.id.value == BuiltInStores.IZZY_ON_DROID_ID }

        assertEquals("F-Droid", fdroid.name)
        assertEquals(AdapterType.FDROID_V2, fdroid.adapterType)
        assertEquals("https://f-droid.org", fdroid.homepage)
        assertEquals("https://f-droid.org/repo", fdroid.indexUrl)
        assertEquals(BuiltInStores.FDROID_FINGERPRINT, fdroid.fingerprint)
        assertTrue(fdroid.builtIn)

        assertEquals("IzzyOnDroid", izzy.name)
        assertEquals(AdapterType.FDROID_V2, izzy.adapterType)
        assertEquals("https://apt.izzysoft.de/fdroid/", izzy.homepage)
        assertEquals("https://apt.izzysoft.de/fdroid/repo", izzy.indexUrl)
        assertEquals(BuiltInStores.IZZY_ON_DROID_FINGERPRINT, izzy.fingerprint)
        assertTrue(izzy.builtIn)
    }

    @Test
    fun `built-in fingerprints are lowercase SHA-256 values`() {
        listOf(
            BuiltInStores.FDROID_FINGERPRINT to
                "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
            BuiltInStores.IZZY_ON_DROID_FINGERPRINT to
                "3bf0d6abfeae2f401707b6d966be743bf0eee49c2561b9ba39073711f628937a",
        ).forEach { (actual, expected) ->
            assertEquals(expected, actual)
            assertTrue(Regex("^[0-9a-f]{64}$").matches(actual))
        }
    }

    @Test
    fun `built-in descriptors are valid and matchable`() {
        BuiltInStores.registry().stores.forEach { descriptor ->
            assertTrue(StoreValidation.validate(descriptor).isEmpty())
            assertTrue(descriptor.priority >= 0)
            assertEquals(StoreMode.MATCHABLE, descriptor.availability().mode)
            assertTrue(descriptor.enabled)
            assertNotNull(descriptor.indexUrl)
        }
    }

    @Test
    fun `unsupported adapter is link only`() {
        val descriptor = BuiltInStores.fdroid.copy(
            id = com.fossscout.core.model.StoreId("example"),
            adapterType = AdapterType.DEEP_LINK,
            indexUrl = null,
            homepage = "https://example.org",
        )

        val availability = descriptor.availability()

        assertEquals(StoreMode.LINK_ONLY, availability.mode)
        assertEquals(LinkOnlyReason.ADAPTER_UNSUPPORTED, availability.reason)
    }

    @Test
    fun `supported adapter without an index is link only`() {
        val descriptor = BuiltInStores.fdroid.copy(indexUrl = null)

        val availability = descriptor.availability()

        assertEquals(StoreMode.LINK_ONLY, availability.mode)
        assertEquals(LinkOnlyReason.MISSING_CATALOG_ENDPOINT, availability.reason)
    }

    @Test
    fun `http requires explicit opt-in`() {
        val httpStore = StoreDescriptor(
            id = com.fossscout.core.model.StoreId("http-store"),
            name = "HTTP store",
            homepage = "http://example.org",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "http://example.org/repo",
        )

        assertTrue(StoreValidationError.INSECURE_HOMEPAGE in StoreValidation.validate(httpStore))
        assertTrue(StoreValidationError.INSECURE_INDEX_URL in StoreValidation.validate(httpStore))

        val optedIn = httpStore.copy(allowInsecureHttp = true)
        assertTrue(StoreValidation.validate(optedIn).isEmpty())
    }

    @Test
    fun `fingerprints accept common separators but reject malformed values`() {
        val valid = "43:23:8D:51:2C:1E:5E:B2:D6:56:9F:4A:3A:FB:F5:52:" +
            "34:18:B8:2E:0A:3E:D1:55:27:70:AB:B9:A9:C9:CC:AB"
        assertEquals(
            "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
            StoreValidation.normalizeFingerprint(valid),
        )

        val invalid = BuiltInStores.fdroid.copy(fingerprint = "not-a-fingerprint")
        assertTrue(StoreValidationError.INVALID_FINGERPRINT in StoreValidation.validate(invalid))
        assertFalse(StoreValidation.validate(BuiltInStores.fdroid).isNotEmpty())
    }
}
