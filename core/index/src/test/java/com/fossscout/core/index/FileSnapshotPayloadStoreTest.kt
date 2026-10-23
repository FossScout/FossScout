package com.fossscout.core.index

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSnapshotPayloadStoreTest {
    private val dir: File = Files.createTempDirectory("fossscout-payloads").toFile()
    private val store = FileSnapshotPayloadStore(dir)
    private val json = Json { encodeDefaults = true }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `committed payload is visible and can be streamed`() = runTest {
        val packageRecord = StorePackage(
            packageName = "org.example.one",
            name = mapOf("en-US" to "One"),
        )
        val result = store.writePayload("store", 1_000, 4_096) { sink ->
            sink.writeLine(
                json.encodeToString(
                    SnapshotPayloadHeader.serializer(),
                    SnapshotPayloadHeader(
                        storeId = "store",
                        indexTimestamp = 1_000,
                        fetchedAtEpochMillis = 10,
                    ),
                ),
            )
            sink.writeLine(parser.encodePackage(packageRecord))
            1
        }

        assertEquals(1, result.value)
        assertTrue(result.payload.sizeBytes > 0)
        assertTrue(store.exists(result.payload.path))
        assertEquals(result.payload.sizeBytes, store.usageBytes())
        assertEquals(result.payload.sizeBytes, File(dir, result.payload.path).length())

        val seen = mutableListOf<StorePackage>()
        store.forEachPackage(result.payload.path, parser) { packageValue ->
            seen += packageValue
            true
        }
        assertEquals(listOf(packageRecord), seen)
    }

    @Test
    fun `streaming can stop before the end of the payload`() = runTest {
        val result = store.writePayload("store", 1, 4_096) { sink ->
            sink.writeLine(header("store", 1))
            sink.writeLine(parser.encodePackage(StorePackage("a.one")))
            sink.writeLine(parser.encodePackage(StorePackage("b.two")))
            sink.writeLine(parser.encodePackage(StorePackage("c.three")))
            3
        }

        val seen = mutableListOf<String>()
        store.forEachPackage(result.payload.path, parser) { packageValue ->
            seen += packageValue.packageName
            seen.size < 2
        }

        assertEquals(listOf("a.one", "b.two"), seen)
    }

    @Test
    fun `failed write discards the temporary payload`() = runTest {
        val failure = runCatching {
            store.writePayload("store", 1, 4_096) { sink ->
                sink.writeLine(header("store", 1))
                throw IllegalStateException("parse failed")
            }
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(0L, store.usageBytes())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cancelled write discards the temporary payload`() = runTest {
        val failure = runCatching {
            store.writePayload("store", 1, 4_096) { sink ->
                sink.writeLine(header("store", 1))
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertEquals(0L, store.usageBytes())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `payload over the byte budget is rejected without committing`() = runTest {
        val failure = runCatching {
            store.writePayload("store", 1, maxBytes = 8) { sink ->
                sink.writeLine("this line is over budget")
            }
        }.exceptionOrNull()

        assertTrue(failure is IndexSyncException)
        assertEquals(SyncFailureKind.STORAGE_LIMIT, (failure as IndexSyncException).kind)
        assertEquals(0L, store.usageBytes())
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `payload paths cannot escape the storage root`() = runTest {
        val failure = runCatching { store.open("../escape.ndjson") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `cleanup removes unreferenced files and keeps active ones`() = runTest {
        val first = store.writePayload("store", 1, 4_096) { sink ->
            sink.writeLine(header("store", 1))
            1
        }
        val second = store.writePayload("store", 2, 4_096) { sink ->
            sink.writeLine(header("store", 2))
            1
        }

        val freed = store.cleanup(setOf(first.payload.path))

        assertEquals(second.payload.sizeBytes, freed)
        assertTrue(store.exists(first.payload.path))
        assertFalse(store.exists(second.payload.path))
        assertEquals(first.payload.sizeBytes, store.usageBytes())
    }

    @Test
    fun `malformed payload header is a parse failure`() = runTest {
        File(dir, "bad.ndjson").writeText("not-json\n")

        val failure = runCatching {
            store.forEachPackage("bad.ndjson", parser) { true }
        }.exceptionOrNull()

        assertTrue(failure is IndexSyncException)
        assertEquals(SyncFailureKind.PARSE, (failure as IndexSyncException).kind)
    }

    private val parser = FdroidV2Parser()

    private fun header(storeId: String, timestamp: Long): String = json.encodeToString(
        SnapshotPayloadHeader.serializer(),
        SnapshotPayloadHeader(
            storeId = storeId,
            indexTimestamp = timestamp,
            fetchedAtEpochMillis = timestamp,
        ),
    )
}
