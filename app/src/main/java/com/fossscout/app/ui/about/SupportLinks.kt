package com.fossscout.app.ui.about

import java.net.URI

/**
 * Keeps the optional donation entry point in one place.
 *
 * The URL stays blank until the project has a verified Liberapay account. A
 * blank or invalid value hides the About support section instead of exposing a
 * dead or untrusted link.
 */
object SupportLinks {
    const val LIBERAPAY_URL: String = ""

    fun liberapayUrlOrNull(): String? = validatedLiberapayUrl(LIBERAPAY_URL)

    internal fun validatedLiberapayUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        if (uri.host != "liberapay.com") return null
        if (uri.userInfo != null || uri.port != -1) return null
        if (uri.query != null || uri.fragment != null) return null

        val path = uri.path.orEmpty()
        if (path.isBlank() || path == "/") return null

        return value
    }
}
