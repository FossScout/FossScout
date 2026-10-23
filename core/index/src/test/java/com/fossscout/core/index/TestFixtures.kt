package com.fossscout.core.index

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal data class SignedEntry(
    val jar: ByteArray,
    val entryJson: String,
    val fingerprint: String,
)

/**
 * Generates real JAR-signed entry fixtures with the JDK tools. No network is
 * involved, and the tests exercise the same signature path as production.
 */
internal class TestSigningKey {
    val dir: File = java.nio.file.Files.createTempDirectory("fossscout-signing").toFile()
    private val keystore = File(dir, "test.p12")
    private val password = "changeit"
    private val alias = "fossscout-test"

    val fingerprint: String by lazy {
        val store = KeyStore.getInstance("PKCS12")
        FileInputStream(keystore).use { store.load(it, password.toCharArray()) }
        val certificate = store.getCertificate(alias) as X509Certificate
        sha256(certificate.encoded)
    }

    init {
        runTool(
            "keytool",
            "-genkeypair",
            "-alias", alias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "3650",
            "-dname", "CN=FossScout Test",
            "-keystore", keystore.absolutePath,
            "-storetype", "PKCS12",
            "-storepass", password,
            "-keypass", password,
            "-noprompt",
        )
    }

    fun sign(entryJson: String): SignedEntry {
        val unsigned = File(dir, "entry-${System.nanoTime()}.jar")
        createJar(unsigned, entryJson)
        runTool(
            "jarsigner",
            "-keystore", keystore.absolutePath,
            "-storepass", password,
            "-keypass", password,
            unsigned.absolutePath,
            alias,
        )
        return SignedEntry(
            jar = unsigned.readBytes(),
            entryJson = entryJson,
            fingerprint = fingerprint,
        )
    }

    fun unsigned(entryJson: String): ByteArray {
        val file = File(dir, "unsigned-${System.nanoTime()}.jar")
        createJar(file, entryJson)
        return file.readBytes()
    }

    private fun createJar(target: File, entryJson: String) {
        JarOutputStream(FileOutputStream(target)).use { jar ->
            jar.putNextEntry(JarEntry("entry.json"))
            jar.write(entryJson.toByteArray(Charsets.UTF_8))
            jar.closeEntry()
        }
    }

    private fun runTool(tool: String, vararg args: String) {
        val home = File(System.getProperty("java.home"))
        val command = listOf(File(File(home, "bin"), tool).absolutePath) + args
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        check(exit == 0) {
            "$tool failed with exit $exit:\n$output"
        }
    }

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal object TestIndexFixtures {
    const val PACKAGE_A = "org.example.alpha"
    const val PACKAGE_B = "org.example.beta"
    const val VERSION_HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val VERSION_HASH_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    const val VERSION_HASH_A2 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

    fun indexJson(
        includeBeta: Boolean = true,
        alphaVersionName: String = "1.0",
        alphaVersionCode: Long = 1,
        alphaHash: String = VERSION_HASH_A,
    ): String {
        val packages = buildList {
            add(
                """
                "$PACKAGE_A": {
                  "metadata": {
                    "added": 1000,
                    "lastUpdated": 2000,
                    "name": {"en-US": "Alpha"},
                    "summary": {"en-US": "First test app"},
                    "description": {"en-US": "A longer alpha description"},
                    "license": "GPL-3.0-or-later",
                    "sourceCode": "https://example.org/alpha",
                    "webSite": "https://example.org",
                    "categories": ["Tools"],
                    "antiFeatures": {"NonFreeNet": {"en-US": "Uses a network"}}
                  },
                  "versions": {
                    "$alphaHash": {
                      "added": 2000,
                      "file": {
                        "name": "/alpha-$alphaVersionCode.apk",
                        "sha256": "$alphaHash",
                        "size": 1234
                      },
                      "manifest": {
                        "versionName": "$alphaVersionName",
                        "versionCode": $alphaVersionCode,
                        "usesSdk": {"minSdkVersion": 24, "targetSdkVersion": 35},
                        "signer": {
                          "sha256": [
                            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                          ]
                        },
                        "usesPermission": [{"name": "android.permission.INTERNET"}]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
            if (includeBeta) {
                add(
                    """
                    "$PACKAGE_B": {
                      "metadata": {
                        "added": 3000,
                        "lastUpdated": 4000,
                        "name": {"en-US": "Beta"},
                        "summary": {"en-US": "Second test app"},
                        "license": "Apache-2.0",
                        "sourceCode": "https://example.org/beta"
                      },
                      "versions": {
                        "$VERSION_HASH_B": {
                          "added": 4000,
                          "file": {
                            "name": "/beta-2.apk",
                            "sha256": "$VERSION_HASH_B",
                            "size": 4321
                          },
                          "manifest": {
                            "versionName": "2.0",
                            "versionCode": 2,
                            "signer": {
                              "sha256": [
                                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
                              ]
                            }
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                )
            }
        }
        return """
            {
              "repo": {
                "name": {"en-US": "Test repo"},
                "address": "https://repo.example/repo"
              },
              "packages": {
                ${packages.joinToString(",")}
              }
            }
        """.trimIndent()
    }

    fun diffJson(): String = """
        {
          "repo": {"timestamp": 5000},
          "packages": {
            "$PACKAGE_A": {
              "metadata": {"lastUpdated": 5000, "summary": {"en-US": "Updated alpha"}},
              "versions": {
                "$VERSION_HASH_A": null,
                "$VERSION_HASH_A2": {
                  "added": 5000,
                  "file": {
                    "name": "/alpha-3.apk",
                    "sha256": "$VERSION_HASH_A2",
                    "size": 2222
                  },
                  "manifest": {
                    "versionName": "3.0",
                    "versionCode": 3,
                    "signer": {
                      "sha256": [
                        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
                      ]
                    }
                  }
                }
              }
            },
            "$PACKAGE_B": null
          }
        }
    """.trimIndent()

    fun entryJson(
        timestamp: Long,
        indexJson: String,
        diffs: Map<Long, String> = emptyMap(),
        numPackages: Int = 2,
    ): String {
        val indexBytes = indexJson.toByteArray(Charsets.UTF_8)
        val diffEntries = diffs.entries.joinToString(",") { (oldTimestamp, json) ->
            val bytes = json.toByteArray(Charsets.UTF_8)
            """
            "$oldTimestamp": {
              "name": "/diff/$oldTimestamp.json",
              "sha256": "${sha256(bytes)}",
              "size": ${bytes.size},
              "numPackages": 1
            }
            """.trimIndent()
        }
        return """
            {
              "timestamp": $timestamp,
              "version": 30000,
              "maxAge": 14,
              "index": {
                "name": "/index-v2.json",
                "sha256": "${sha256(indexBytes)}",
                "size": ${indexBytes.size},
                "numPackages": $numPackages
              },
              "diffs": {$diffEntries}
            }
        """.trimIndent()
    }

    fun sha256(bytes: ByteArray): String = TestSigningKey.sha256(bytes)
}

internal class FakeHttpFetcher(
    private val handler: suspend (HttpRequest) -> HttpResponse,
) : HttpFetcher {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun get(request: HttpRequest): HttpResponse {
        requests += request
        return handler(request)
    }
}

internal fun fakeResponse(
    url: String,
    bytes: ByteArray,
    status: Int = 200,
    etag: String? = null,
    lastModified: String? = null,
): HttpResponse = HttpResponse(
    status = status,
    etag = etag,
    lastModified = lastModified,
    contentLength = bytes.size.toLong(),
    contentType = "application/json",
    finalUrl = url,
    body = ByteArrayInputStream(bytes),
)

internal class FakeMetadataStore(
    initial: IndexState = IndexState(),
) : SnapshotMetadataStore {
    val mutableState = MutableStateFlow(initial)

    override val state: Flow<IndexState> = flow { emitAll(mutableState) }

    override suspend fun save(state: IndexState) {
        mutableState.value = state
    }
}

internal class InMemorySnapshotPayloadStore(
    private val onWriteLine: (suspend (String) -> Unit)? = null,
) : SnapshotPayloadStore {
    val payloads = mutableMapOf<String, List<String>>()
    var usage: Long = 0
    private var nextId = 0

    override suspend fun <T> writePayload(
        storeId: String,
        version: Long,
        maxBytes: Long,
        block: suspend (SnapshotPayloadSink) -> T,
    ): PayloadWriteResult<T> {
        val lines = mutableListOf<String>()
        val sink = object : SnapshotPayloadSink {
            override var bytesWritten: Long = 0
                private set

            override suspend fun writeLine(line: String) {
                val bytes = line.toByteArray(Charsets.UTF_8).size + 1
                if (bytesWritten + bytes > maxBytes) {
                    throw IndexSyncException(
                        SyncFailureKind.STORAGE_LIMIT,
                        "Index payload exceeds the configured storage budget",
                    )
                }
                onWriteLine?.invoke(line)
                lines += line
                bytesWritten += bytes
            }
        }
        val value = block(sink)
        val path = "payload-${nextId++}.ndjson"
        payloads[path] = lines
        usage += sink.bytesWritten
        return PayloadWriteResult(
            StoredPayload(path, sink.bytesWritten),
            value,
        )
    }

    override suspend fun open(payloadPath: String): InputStream {
        val content = payloads[payloadPath]?.joinToString("\n", postfix = "\n")
            ?: throw java.io.FileNotFoundException(payloadPath)
        return ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))
    }

    override suspend fun exists(payloadPath: String): Boolean = payloadPath in payloads

    override suspend fun delete(payloadPath: String) {
        val lines = payloads.remove(payloadPath) ?: return
        usage -= lines.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 }
    }

    override suspend fun usageBytes(): Long = usage

    override suspend fun cleanup(referencedPaths: Set<String>): Long {
        var freed = 0L
        payloads.keys.toList().forEach { path ->
            if (path !in referencedPaths) {
                freed += payloads[path]?.sumOf {
                    it.toByteArray(Charsets.UTF_8).size.toLong() + 1
                } ?: 0L
                payloads.remove(path)
            }
        }
        usage = payloads.values.sumOf { lines ->
            lines.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1 }
        }
        return freed
    }

    override suspend fun forEachPackage(
        payloadPath: String,
        parser: FdroidV2Parser,
        action: suspend (StorePackage) -> Boolean,
    ) {
        val lines = payloads[payloadPath] ?: throw java.io.FileNotFoundException(payloadPath)
        lines.drop(1).forEach { line ->
            if (line.isNotBlank() && !action(parser.decodePackage(line))) {
                return
            }
        }
    }
}

internal fun tamperJar(jarBytes: ByteArray, entryJson: String): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    java.util.zip.ZipInputStream(ByteArrayInputStream(jarBytes)).use { input ->
        java.util.zip.ZipOutputStream(output).use { jar ->
            while (true) {
                val entry = input.nextEntry ?: break
                jar.putNextEntry(java.util.zip.ZipEntry(entry.name))
                if (entry.name == "entry.json") {
                    jar.write(entryJson.toByteArray(Charsets.UTF_8))
                } else {
                    input.copyTo(jar)
                }
                jar.closeEntry()
            }
        }
    }
    return output.toByteArray()
}
