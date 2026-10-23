package com.fossscout.core.index

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

data class StoredPayload(
    val path: String,
    val sizeBytes: Long,
)

data class PayloadWriteResult<T>(
    val payload: StoredPayload,
    val value: T,
)

interface SnapshotPayloadSink {
    val bytesWritten: Long

    suspend fun writeLine(line: String)
}

/**
 * Bounded, atomically-committed payload storage. A write only becomes visible
 * after the whole payload has been produced and renamed; a failure or
 * cancellation removes the temporary file and leaves the previous payload
 * untouched.
 */
interface SnapshotPayloadStore {
    suspend fun <T> writePayload(
        storeId: String,
        version: Long,
        maxBytes: Long,
        block: suspend (SnapshotPayloadSink) -> T,
    ): PayloadWriteResult<T>

    suspend fun open(payloadPath: String): InputStream

    suspend fun exists(payloadPath: String): Boolean

    suspend fun delete(payloadPath: String)

    suspend fun usageBytes(): Long

    /** Deletes files not referenced by an active snapshot, including temp files. */
    suspend fun cleanup(referencedPaths: Set<String>): Long

    /** Streams normalized packages without loading the whole payload. */
    suspend fun forEachPackage(
        payloadPath: String,
        parser: FdroidV2Parser,
        action: suspend (StorePackage) -> Boolean,
    )
}

class FileSnapshotPayloadStore(
    private val rootDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) : SnapshotPayloadStore {
    override suspend fun <T> writePayload(
        storeId: String,
        version: Long,
        maxBytes: Long,
        block: suspend (SnapshotPayloadSink) -> T,
    ): PayloadWriteResult<T> = withContext(Dispatchers.IO) {
        require(maxBytes >= 0) { "Storage budget must not be negative" }
        ensureRoot()
        val token = UUID.randomUUID().toString()
        val safeStore = storeId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val temp = File(rootDir, "$safeStore-$version-$token.ndjson.tmp")
        val target = File(rootDir, "$safeStore-$version-$token.ndjson")

        val stream = FileOutputStream(temp)
        val sink = FilePayloadSink(
            output = BufferedOutputStream(stream),
            fileDescriptor = stream,
            maxBytes = maxBytes,
        )
        try {
            val value = block(sink)
            sink.commit()
            if (!temp.renameTo(target)) {
                throw IndexSyncException(
                    SyncFailureKind.IO,
                    "Could not activate the new index payload",
                )
            }
            PayloadWriteResult(
                payload = StoredPayload(
                    path = target.name,
                    sizeBytes = sink.bytesWritten,
                ),
                value = value,
            )
        } catch (throwable: Throwable) {
            sink.abort()
            temp.delete()
            throw throwable
        }
    }

    override suspend fun open(payloadPath: String): InputStream = withContext(Dispatchers.IO) {
        BufferedInputStream(FileInputStream(resolve(payloadPath)))
    }

    override suspend fun exists(payloadPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolve(payloadPath).isFile }.getOrDefault(false)
    }

    override suspend fun delete(payloadPath: String) = withContext(Dispatchers.IO) {
        runCatching { resolve(payloadPath).delete() }
        Unit
    }

    override suspend fun usageBytes(): Long = withContext(Dispatchers.IO) {
        ensureRoot()
        rootDir.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }

    override suspend fun cleanup(referencedPaths: Set<String>): Long = withContext(Dispatchers.IO) {
        ensureRoot()
        var freed = 0L
        rootDir.listFiles()?.forEach { file ->
            val referenced = file.isFile && file.name in referencedPaths
            if (!referenced) {
                val size = file.length()
                if (file.delete()) freed += size
            }
        }
        freed
    }

    override suspend fun forEachPackage(
        payloadPath: String,
        parser: FdroidV2Parser,
        action: suspend (StorePackage) -> Boolean,
    ) {
        open(payloadPath).bufferedReader(Charsets.UTF_8).use { reader ->
            val header = reader.readLine()
                ?: throw IndexSyncException(SyncFailureKind.IO, "Index payload is empty")
            try {
                json.decodeFromString(SnapshotPayloadHeader.serializer(), header)
            } catch (exception: Exception) {
                throw IndexSyncException(
                    SyncFailureKind.PARSE,
                    "Index payload header is malformed",
                    exception,
                )
            }

            var line = reader.readLine()
            while (line != null) {
                currentCoroutineContext().ensureActive()
                if (line.isNotBlank()) {
                    if (!action(parser.decodePackage(line))) return
                }
                line = reader.readLine()
            }
        }
    }

    private fun ensureRoot() {
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw IndexSyncException(
                SyncFailureKind.IO,
                "Could not create index storage directory",
            )
        }
    }

    private fun resolve(payloadPath: String): File {
        require(payloadPath.isNotBlank()) { "Payload path is blank" }
        val root = rootDir.canonicalFile
        val candidate = File(rootDir, payloadPath).canonicalFile
        require(candidate.path == root.path || candidate.path.startsWith(root.path + File.separator)) {
            "Payload path escapes the storage root"
        }
        return candidate
    }
}

private class FilePayloadSink(
    private val output: BufferedOutputStream,
    private val fileDescriptor: FileOutputStream,
    private val maxBytes: Long,
) : SnapshotPayloadSink {
    override var bytesWritten: Long = 0
        private set

    private var committed = false

    override suspend fun writeLine(line: String) {
        currentCoroutineContext().ensureActive()
        val bytes = line.toByteArray(Charsets.UTF_8)
        val required = bytes.size.toLong() + 1L
        if (bytesWritten + required > maxBytes) {
            throw IndexSyncException(
                SyncFailureKind.STORAGE_LIMIT,
                "Index payload exceeds the configured storage budget",
            )
        }
        output.write(bytes)
        output.write('\n'.code)
        bytesWritten += required
    }

    fun commit() {
        output.flush()
        fileDescriptor.fd.sync()
        output.close()
        committed = true
    }

    fun abort() {
        if (committed) return
        runCatching { output.close() }
    }
}
