package com.fossscout.core.data

import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRegistryCodecTest {
    private val codec = StoreRegistryCodec()

    @Test
    fun `registry round-trips custom and link-only stores`() {
        val custom = StoreDescriptor(
            id = StoreId("custom-example"),
            name = "Example repo",
            homepage = "https://example.org",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "https://example.org/repo",
            fingerprint = BuiltInStores.FDROID_FINGERPRINT,
            enabled = false,
            priority = 3,
            trustNote = "Local test store",
            allowInsecureHttp = false,
        )
        val linkOnly = StoreDescriptor(
            id = StoreId("custom-link"),
            name = "Link store",
            homepage = "https://example.com/project",
            adapterType = AdapterType.DEEP_LINK,
            priority = 4,
        )
        val registry = StoreRegistry(
            stores = BuiltInStores.registry().stores + custom + linkOnly,
        )

        val decoded = codec.decodeRegistry(codec.encodeRegistry(registry))

        assertEquals(registry, decoded)
    }

    @Test
    fun `registry round-trips an explicitly approved HTTP store`() {
        val httpStore = StoreDescriptor(
            id = StoreId("http-store"),
            name = "HTTP store",
            homepage = "http://example.org",
            adapterType = AdapterType.FDROID_V2,
            indexUrl = "http://example.org/repo",
            allowInsecureHttp = true,
            priority = 2,
        )

        val decoded = codec.decodeRegistry(
            codec.encodeRegistry(StoreRegistry(stores = listOf(httpStore))),
        )

        assertEquals(httpStore, decoded.stores.single())
        assertTrue(decoded.stores.single().allowInsecureHttp)
    }

    @Test
    fun `descriptor export is versioned and round-trips`() {
        val descriptor = BuiltInStores.izzyOnDroid
        val encoded = codec.encodeDescriptor(descriptor)

        assertTrue(encoded.contains("\"schemaVersion\":1"))
        assertEquals(descriptor, codec.decodeDescriptor(encoded))
    }

    @Test
    fun `malformed registry data is rejected`() {
        val result = runCatching { codec.decodeRegistry("{not-json") }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
    }

    @Test
    fun `future registry schema is rejected`() {
        val encoded = """{"schemaVersion":99,"stores":[]}"""

        val result = runCatching { codec.decodeRegistry(encoded) }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
    }

    @Test
    fun `future descriptor schema is rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "descriptor": {
                "id": "future-store",
                "name": "Future",
                "homepage": "https://example.org",
                "adapterType": "FDROID_V2",
                "schemaVersion": 99
              }
            }
        """.trimIndent()

        val result = runCatching { codec.decodeDescriptor(encoded) }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
    }

    @Test
    fun `unknown adapter type is rejected`() {
        val encoded = """
            {
              "schemaVersion": 1,
              "descriptor": {
                "id": "unknown-store",
                "name": "Unknown",
                "homepage": "https://example.org",
                "adapterType": "QUANTUM_INDEX"
              }
            }
        """.trimIndent()

        val result = runCatching { codec.decodeDescriptor(encoded) }

        assertTrue(result.exceptionOrNull() is StoreRegistryFormatException)
    }

    @Test
    fun `invalid fingerprint and duplicate ids are rejected`() {
        val invalidFingerprint = BuiltInStores.fdroid.copy(
            id = StoreId("invalid-fingerprint"),
            fingerprint = "guess",
        )
        val fingerprintResult = runCatching {
            codec.encodeRegistry(StoreRegistry(stores = listOf(invalidFingerprint)))
        }
        assertTrue(fingerprintResult.exceptionOrNull() is IllegalArgumentException)

        val duplicateResult = runCatching {
            codec.decodeRegistry(
                """
                    {"schemaVersion":1,"stores":[
                      {"id":"same","name":"One","homepage":"https://one.example",
                       "adapterType":"FDROID_V2","indexUrl":"https://one.example/repo"},
                      {"id":"same","name":"Two","homepage":"https://two.example",
                       "adapterType":"FDROID_V2","indexUrl":"https://two.example/repo"}
                    ]}
                """.trimIndent(),
            )
        }
        assertTrue(duplicateResult.exceptionOrNull() is StoreRegistryFormatException)
    }
}
