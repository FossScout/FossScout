package com.fossscout.core.matching

import com.fossscout.core.index.FdroidV2Parser
import com.fossscout.core.index.PayloadWriteResult
import com.fossscout.core.index.SignatureStatus
import com.fossscout.core.index.SnapshotPayloadSink
import com.fossscout.core.index.SnapshotPayloadStore
import com.fossscout.core.index.StorePackage
import com.fossscout.core.index.StorePackageVersion
import com.fossscout.core.index.StoreSnapshot
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotCatalogSourceTest {
    @Test
    fun `load projects only the fields matching needs`() = runTest {
        val payloads = FakePayloadStore(
            mapOf("one.ndjson" to listOf(packageRecord())),
        )
        val source = SnapshotCatalogSource(payloads)

        val catalog = source.load(snapshot("one.ndjson"))

        val entry = catalog.byPackage.getValue("org.example.app")
        assertEquals("Example", entry.name)
        assertEquals("GPL-3.0-only", entry.license)
        assertEquals("https://example.org/src", entry.sourceCode)
        assertEquals(listOf("Tools"), entry.categories)
        assertEquals("1.2.3", entry.versionName)
        assertEquals(2L, entry.versionCode)
        assertEquals(listOf("aaaa"), entry.signerSha256)
        assertTrue(entry.hasLicense)
        assertTrue(entry.hasSource)
    }

    @Test
    fun `cache reuses the same payload path`() = runTest {
        val payloads = FakePayloadStore(
            mapOf("one.ndjson" to listOf(packageRecord())),
        )
        val source = SnapshotCatalogSource(payloads)

        source.load(snapshot("one.ndjson"))
        source.load(snapshot("one.ndjson"))

        assertEquals(1, payloads.loads)
    }

    @Test
    fun `a new payload path is read again`() = runTest {
        val payloads = FakePayloadStore(
            mapOf(
                "one.ndjson" to listOf(packageRecord()),
                "two.ndjson" to listOf(packageRecord()),
            ),
        )
        val source = SnapshotCatalogSource(payloads)

        source.load(snapshot("one.ndjson"))
        source.load(snapshot("two.ndjson"))

        assertEquals(2, payloads.loads)
    }

    @Test
    fun `cache evicts the least recently used payload`() = runTest {
        val payloads = FakePayloadStore(
            mapOf(
                "one.ndjson" to listOf(packageRecord()),
                "two.ndjson" to listOf(packageRecord()),
                "three.ndjson" to listOf(packageRecord()),
            ),
        )
        val source = SnapshotCatalogSource(payloads, maxCachedCatalogs = 2)

        source.load(snapshot("one.ndjson"))
        source.load(snapshot("two.ndjson"))
        source.load(snapshot("three.ndjson"))
        source.load(snapshot("one.ndjson"))

        assertEquals(4, payloads.loads)
    }

    @Test
    fun `source URL index ignores case and trailing slashes`() = runTest {
        val payloads = FakePayloadStore(
            mapOf(
                "one.ndjson" to listOf(
                    packageRecord(sourceCode = "https://Example.org/SRC/"),
                ),
            ),
        )
        val source = SnapshotCatalogSource(payloads)

        val catalog = source.load(snapshot("one.ndjson"))

        assertNotNull(catalog.bySource["https://example.org/src"])
    }

    private fun packageRecord(
        sourceCode: String = "https://example.org/src",
    ) = StorePackage(
        packageName = "org.example.app",
        name = mapOf("en-US" to "Example"),
        summary = mapOf("en-US" to "An example app"),
        categories = listOf("Tools"),
        license = "GPL-3.0-only",
        sourceCode = sourceCode,
        versions = listOf(
            StorePackageVersion(
                fileSha256 = "a".repeat(64),
                versionCode = 2,
                versionName = "1.2.3",
                signerSha256 = listOf("aaaa"),
            ),
            StorePackageVersion(
                fileSha256 = "b".repeat(64),
                versionCode = 1,
                versionName = "1.0.0",
                signerSha256 = listOf("old"),
            ),
        ),
    )

    private fun snapshot(path: String) = StoreSnapshot(
        storeId = "store-1",
        fetchedAtEpochMillis = 1_000L,
        packageCount = 1,
        payloadPath = path,
        payloadSizeBytes = 10L,
        signatureStatus = SignatureStatus.VERIFIED,
        sourceUrl = "https://store.example/repo",
    )

    private class FakePayloadStore(
        private val payloads: Map<String, List<StorePackage>>,
    ) : SnapshotPayloadStore {
        var loads: Int = 0
            private set

        override suspend fun <T> writePayload(
            storeId: String,
            version: Long,
            maxBytes: Long,
            block: suspend (SnapshotPayloadSink) -> T,
        ): PayloadWriteResult<T> = error("write not used")

        override suspend fun open(payloadPath: String): InputStream =
            error("open not used")

        override suspend fun exists(payloadPath: String): Boolean =
            payloadPath in payloads

        override suspend fun delete(payloadPath: String) = Unit

        override suspend fun usageBytes(): Long = 0L

        override suspend fun cleanup(referencedPaths: Set<String>): Long = 0L

        override suspend fun forEachPackage(
            payloadPath: String,
            parser: FdroidV2Parser,
            action: suspend (StorePackage) -> Boolean,
        ) {
            loads++
            val records = payloads[payloadPath]
                ?: throw IllegalStateException("No payload for $payloadPath")
            for (record in records) {
                if (!action(record)) return
            }
        }
    }
}
