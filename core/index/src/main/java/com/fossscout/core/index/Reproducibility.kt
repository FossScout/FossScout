package com.fossscout.core.index

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Identity used to keep on-demand verification results scoped to one store hit. */
data class ReproducibilityKey(
    val packageName: String,
    val storeId: String,
    val versionCode: Long,
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(versionCode > 0L) { "Version code must be positive" }
    }
}

/** F-Droid verification results for one package version. */
enum class ReproducibilityStatus {
    VERIFIED,
    FAILED,
    NOT_VERIFIED,
    UNAVAILABLE,
}

/**
 * A reproducibility lookup result plus the evidence it was based on.
 *
 * Verification reports can be VERIFIED while [localSha256] and [remoteSha256]
 * differ; the server's comparison accounts for expected build differences.
 */
data class ReproducibilityCheck(
    val packageName: String,
    val versionCode: Long,
    val status: ReproducibilityStatus,
    val sourceUrl: String,
    val checkedAtEpochMillis: Long,
    val versionName: String? = null,
    val reportTimestampEpochMillis: Long? = null,
    val localSha256: String? = null,
    val remoteSha256: String? = null,
) {
    init {
        require(packageName.isNotBlank()) { "Package name is blank" }
        require(versionCode > 0L) { "Version code must be positive" }
        require(sourceUrl.isNotBlank()) { "Source URL is blank" }
    }
}

interface ReproducibilityChecker {
    suspend fun check(packageName: String, versionCode: Long): ReproducibilityCheck
}

/**
 * On-demand client for the F-Droid Verification Server.
 *
 * Requests are only made when the user asks for a check. Results are cached for
 * the process lifetime; failures are not cached so the user can retry.
 */
class FdroidVerificationClient(
    private val httpFetcher: HttpFetcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
) : ReproducibilityChecker {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<Pair<String, Long>, ReproducibilityCheck>()

    override suspend fun check(packageName: String, versionCode: Long): ReproducibilityCheck {
        require(PACKAGE_NAME_PATTERN.matches(packageName)) { "Invalid package name" }
        require(versionCode > 0L) { "Version code must be positive" }

        val key = packageName to versionCode
        cacheMutex.withLock { cache[key] }?.let { return it }

        val result = fetch(packageName, versionCode)
        if (result.status != ReproducibilityStatus.UNAVAILABLE) {
            cacheMutex.withLock { cache[key] = result }
        }
        return result
    }

    private suspend fun fetch(packageName: String, versionCode: Long): ReproducibilityCheck {
        val packagePage = "$baseUrl/packages/$packageName/"
        val reportUrl = "$baseUrl/unsigned/${packageName}_$versionCode.apk.json"
        val checkedAt = now()

        return try {
            val response = httpFetcher.get(
                HttpRequest(
                    url = reportUrl,
                    accept = "application/json",
                ),
            )
            when {
                response.status == HttpURLConnection.HTTP_NOT_FOUND -> {
                    response.body?.close()
                    ReproducibilityCheck(
                        packageName = packageName,
                        versionCode = versionCode,
                        status = ReproducibilityStatus.NOT_VERIFIED,
                        sourceUrl = packagePage,
                        checkedAtEpochMillis = checkedAt,
                    )
                }

                !response.isSuccess -> {
                    response.body?.close()
                    unavailable(packageName, versionCode, packagePage, checkedAt)
                }

                else -> {
                    val text = response.body?.use { body ->
                        readLimited(body, maxResponseBytes)
                    }.orEmpty()
                    parseReport(
                        packageName = packageName,
                        versionCode = versionCode,
                        packagePage = packagePage,
                        checkedAt = checkedAt,
                        text = text,
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            unavailable(packageName, versionCode, packagePage, checkedAt)
        }
    }

    private fun parseReport(
        packageName: String,
        versionCode: Long,
        packagePage: String,
        checkedAt: Long,
        text: String,
    ): ReproducibilityCheck {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }
            .getOrElse {
                return unavailable(packageName, versionCode, packagePage, checkedAt)
            }
        val reports = root.entries.mapNotNull { (key, value) ->
            (value as? JsonObject)?.let { key to it }
        }
        if (reports.isEmpty()) {
            return ReproducibilityCheck(
                packageName = packageName,
                versionCode = versionCode,
                status = ReproducibilityStatus.NOT_VERIFIED,
                sourceUrl = packagePage,
                checkedAtEpochMillis = checkedAt,
            )
        }

        val verifiedReports = reports.filter { (_, report) ->
            report["verified"]?.jsonPrimitive?.booleanOrNull == true
        }
        val verified = verifiedReports.isNotEmpty()
        val evidenceReports = if (verifiedReports.isNotEmpty()) verifiedReports else reports
        val latest = evidenceReports
            .maxByOrNull { (key, _) -> key.toDoubleOrNull() ?: 0.0 }
            ?.second
        val local = latest?.get("local") as? JsonObject
        val remote = latest?.get("remote") as? JsonObject
        val reportTimestamp = local?.stringOrNull("timestamp")
            ?.toDoubleOrNull()
            ?.times(1000.0)
            ?.toLong()
            ?: reports.maxOfOrNull { (key, _) ->
                key.toDoubleOrNull()?.times(1000.0)?.toLong() ?: 0L
            }?.takeIf { it > 0L }

        return ReproducibilityCheck(
            packageName = packageName,
            versionCode = versionCode,
            status = if (verified) {
                ReproducibilityStatus.VERIFIED
            } else {
                ReproducibilityStatus.FAILED
            },
            sourceUrl = packagePage,
            checkedAtEpochMillis = checkedAt,
            versionName = local?.stringOrNull("versionName")
                ?: remote?.stringOrNull("versionName"),
            reportTimestampEpochMillis = reportTimestamp,
            localSha256 = local?.stringOrNull("sha256"),
            remoteSha256 = remote?.stringOrNull("sha256"),
        )
    }

    private fun unavailable(
        packageName: String,
        versionCode: Long,
        packagePage: String,
        checkedAt: Long,
    ): ReproducibilityCheck = ReproducibilityCheck(
        packageName = packageName,
        versionCode = versionCode,
        status = ReproducibilityStatus.UNAVAILABLE,
        sourceUrl = packagePage,
        checkedAtEpochMillis = checkedAt,
    )

    private fun readLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Verification report is too large")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://verification.f-droid.org"
        const val DEFAULT_MAX_RESPONSE_BYTES = 1_048_576

        private val PACKAGE_NAME_PATTERN =
            Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
