package com.fossscout.core.inventory

import java.net.URI

/**
 * Accepts a raw package name, a Play/F-Droid/market store link, or shared text
 * that contains one of those. Pure JVM code so the manual path is testable
 * without Android.
 */
object PackageIdentityParser {
    private val packageName = Regex(
        """^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$""",
    )
    private val url = Regex("""(?:https?://|market://)\S+""", RegexOption.IGNORE_CASE)
    private val packageToken = Regex(
        """(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)(?![A-Za-z0-9_])""",
    )

    fun extract(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (packageName.matches(trimmed)) return trimmed

        val link = url.find(trimmed)?.value
        if (link != null) return fromUri(link)

        return packageToken.find(trimmed)?.groupValues?.getOrNull(1)
            ?.takeIf(packageName::matches)
    }

    fun isPackageName(value: String): Boolean = packageName.matches(value.trim())

    private fun fromUri(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val queryId = uri.rawQuery
            ?.split('&')
            ?.mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2 && pieces[0] == "id") pieces[1] else null
            }
            ?.firstOrNull()
        if (queryId != null && isPackageName(queryId)) return queryId.trim()

        val segments = uri.path
            ?.trim('/')
            ?.split('/')
            ?.filter(String::isNotBlank)
            .orEmpty()
        val packageIndex = segments.indexOf("packages")
        if (packageIndex >= 0) {
            segments.getOrNull(packageIndex + 1)
                ?.takeIf(packageName::matches)
                ?.let { return it }
        }

        // Fall back to the last path segment for simple /package/name links.
        return segments.lastOrNull()?.takeIf(packageName::matches)
    }
}
