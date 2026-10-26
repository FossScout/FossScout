package com.fossscout.core.contribution

import com.fossscout.core.matching.CoverageReason
import com.fossscout.core.matching.CoverageSnapshot
import java.net.URI

/**
 * Privacy boundary for contribution reports.
 *
 * Every field is treated as untrusted. URLs lose userinfo, query strings, and
 * fragments; free text loses local paths, e-mail addresses, token-like values,
 * and long hexadecimal identifiers. The builder only receives the fields
 * returned by this object.
 */
object ContributionRedactor {
    private const val MAX_LABEL_CHARS = 120
    private const val MAX_MESSAGE_CHARS = 500
    private const val MAX_OBSERVED_CHARS = 200

    private val packageNamePattern =
        Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")
    private val urlPattern = Regex("""(?i)\bhttps?://[^\s<>"']+""")
    private val localPathPatterns = listOf(
        Regex("""(?i)\b(?:file|content)://[^\s<>"']+"""),
        Regex(
            """(?i)(?:^|[\s(])(/(?:data|storage|sdcard|mnt|home|Users|var|tmp|private|system|etc|opt|usr|proc|sys|dev|root|run|srv|boot)/[^\s,;)]*)""",
        ),
        Regex("""(?i)(?:^|[\s(])(~/[^\s,;)]*)"""),
        Regex("""(?i)\b[A-Z]:\\[^\s,;)]*"""),
    )
    private val emailPattern = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
    private val hexIdentifierPattern = Regex("""\b[0-9a-fA-F]{16,}\b""")
    private val longDigitIdentifierPattern = Regex("""\b\d{15,}\b""")
    private val tokenPattern = Regex(
        """(?i)\b(token|access_token|api_key|apikey|password|passwd|secret|signature|sig)\b["']?\s*[:=]\s*["']?([^"'\s,&}]+)""",
    )
    private val bearerPattern = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val prefixedTokenPattern = Regex(
        """(?i)\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})\b""",
    )
    private val longTokenPattern = Regex("""(?i)\b[A-Za-z0-9+/=%_-]{40,}\b""")
    private val identifierPathSegment = Regex("""(?i)^(?:\d{15,}|[0-9a-f]{16,})$""")
    private val tokenLikePathSegment = Regex("""(?i)[A-Za-z0-9._~%+=-]{24,}""")
    private val whitespacePattern = Regex("""\s+""")

    /** Keeps scheme, host, port, and path; drops credentials, query, and fragment. */
    fun redactUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath.orEmpty()
            .split('/')
            .joinToString("/") { segment ->
                if (
                    identifierPathSegment.matches(segment) ||
                    tokenLikePathSegment.matches(segment)
                ) {
                    "<redacted>"
                } else {
                    segment
                }
            }
        return "$scheme://$host$port$path"
    }

    /** Sanitizes free text before it can enter a report. */
    fun redactMessage(raw: String?): String? {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        value = urlPattern.replace(value) { match ->
            redactUrl(match.value) ?: "<redacted-url>"
        }
        localPathPatterns.forEach { pattern ->
            value = pattern.replace(value) { match ->
                if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) {
                    match.value.replace(match.groupValues[1], "<local-path>")
                } else {
                    "<local-path>"
                }
            }
        }
        value = emailPattern.replace(value, "<email>")
        value = hexIdentifierPattern.replace(value, "<hex-identifier>")
        value = longDigitIdentifierPattern.replace(value, "<identifier>")
        value = bearerPattern.replace(value, "Bearer <redacted>")
        value = prefixedTokenPattern.replace(value, "<redacted-token>")
        value = longTokenPattern.replace(value, "<redacted-token>")
        value = tokenPattern.replace(value) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        value = whitespacePattern.replace(value, " ").trim()
        return value.take(MAX_MESSAGE_CHARS).ifBlank { null }
    }

    /** A single-line label for store names and other short user input. */
    fun safeLabel(raw: String?): String? =
        redactMessage(raw)?.take(MAX_LABEL_CHARS)?.ifBlank { null }

    /**
     * Only a structurally valid Android package name is reportable. Package
     * names are intentionally not passed through text redaction: a hex-looking
     * segment is still a legitimate package identity.
     */
    fun packageName(raw: String?): String? =
        raw?.trim()?.take(255)?.takeIf { packageNamePattern.matches(it) }

    /** Observed match results are short, sanitized text, never a stack trace. */
    fun observedResult(raw: String?): String? =
        redactMessage(raw)?.take(MAX_OBSERVED_CHARS)?.ifBlank { null }

    fun redactStore(input: ReportStoreInput?): ReportStoreDescriptor? {
        val store = input ?: return null
        val redacted = ReportStoreDescriptor(
            name = safeLabel(store.name),
            homepage = redactUrl(store.homepage),
            adapterType = store.adapterType,
            indexUrl = redactUrl(store.indexUrl),
            apiUrl = redactUrl(store.apiUrl),
            repositoryUrl = redactUrl(store.repositoryUrl),
            hasPinnedFingerprint = store.hasPinnedFingerprint,
        )
        return redacted.takeUnless { it.isEmpty }
    }

    /** Store statuses only. Detail text, package counts, and timestamps are dropped. */
    fun coverage(snapshot: CoverageSnapshot?): List<ReportCoverageSummary> =
        snapshot
            ?.stores
            ?.map { store ->
                ReportCoverageSummary(
                    storeName = safeLabel(store.storeName) ?: "unknown",
                    status = store.status.name,
                    reason = store.reason
                        .takeUnless { it == CoverageReason.NONE }
                        ?.name,
                )
            }
            ?.sortedBy { it.storeName.lowercase() }
            .orEmpty()
}
