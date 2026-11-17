package com.fossscout.core.index

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val SECURITY_AUDIT_SCHEMA_VERSION: Int = 1

/**
 * A local, shareable export of one store's timestamped verification history.
 * Store names, trust notes, and installed-app inventory are deliberately
 * excluded because they are not verification evidence.
 */
@Serializable
data class SecurityAuditExport(
    val schemaVersion: Int = SECURITY_AUDIT_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val exportedAtUtc: String,
    val storeId: String,
    val records: List<TrustRecord>,
    val limitations: List<String> = SECURITY_AUDIT_LIMITATIONS,
) {
    init {
        require(storeId.isNotBlank()) { "Store id is blank" }
        require(exportedAtEpochMillis >= 0) {
            "Exported timestamp must not be negative"
        }
        require(records.isNotEmpty()) { "Audit export must contain at least one record" }
        require(records.all { it.storeId == storeId }) {
            "Audit records must belong to the exported store"
        }
    }
}

/** Builds and encodes a local security audit without any network access. */
class SecurityAuditExportBuilder(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) {
    fun build(
        storeId: String,
        records: List<TrustRecord>,
        exportedAtEpochMillis: Long,
    ): SecurityAuditExport {
        require(exportedAtEpochMillis >= 0) {
            "Exported timestamp must not be negative"
        }
        return SecurityAuditExport(
            exportedAtEpochMillis = exportedAtEpochMillis,
            exportedAtUtc = formatUtc(exportedAtEpochMillis),
            storeId = storeId,
            records = records.sortedBy(TrustRecord::checkedAtEpochMillis),
        )
    }

    fun encode(audit: SecurityAuditExport): String =
        json.encodeToString(SecurityAuditExport.serializer(), audit)

    private fun formatUtc(epochMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMillis))
}

private val SECURITY_AUDIT_LIMITATIONS = listOf(
    "This report contains index verification evidence only. " +
        "APK hash and signer continuity are not checked in this build.",
    "No store trust notes, favourites, installed-app inventory, or other " +
        "profile content are included.",
)
