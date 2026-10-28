package com.fossscout.core.contribution

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds a prefilled issue URL. Nothing is opened automatically; the UI must
 * show the preview and require an explicit action first.
 */
object ContributionIssueUrl {
    const val DEFAULT_NEW_ISSUE_URL = "https://github.com/FossScout/FossScout/issues/new"

    /** Keep the encoded URL within common browser/server limits; the full report is copyable. */
    const val MAX_BODY_CHARS = 4_000

    fun build(
        baseUrl: String = DEFAULT_NEW_ISSUE_URL,
        title: String,
        body: String,
    ): String {
        val base = baseUrl.trim().substringBefore('#')
        val uri = runCatching { URI(base) }.getOrNull()
        require(
            uri != null &&
                uri.scheme?.lowercase() in setOf("http", "https") &&
                !uri.host.isNullOrBlank(),
        ) {
            "Issue tracker URL must be an absolute HTTP(S) URL"
        }
        val safeTitle = title.trim().take(120)
        val safeBody = body.trim().let { value ->
            if (value.length <= MAX_BODY_CHARS) {
                value
            } else {
                value.take(MAX_BODY_CHARS) +
                    "\n\n[Report truncated. Use Copy report for the full preview.]"
            }
        }
        val separator = when {
            '?' !in base -> "?"
            base.endsWith('?') || base.endsWith('&') -> ""
            else -> "&"
        }
        return buildString {
            append(base)
            append(separator)
            append("title=")
            append(encode(safeTitle))
            append("&body=")
            append(encode(safeBody))
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
