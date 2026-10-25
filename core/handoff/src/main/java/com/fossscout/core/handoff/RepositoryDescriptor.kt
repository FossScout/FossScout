package com.fossscout.core.handoff

import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreValidation
import java.net.URI

/**
 * Canonical F-Droid repository descriptor. URL, fingerprint, QR payload, and
 * clipboard text are all derived from this one value so every add path produces
 * the same repository.
 */
data class RepositoryDescriptor(
    val storeId: String,
    val storeName: String,
    val repositoryUrl: String,
    val fingerprint: String,
) {
    init {
        require(storeId.isNotBlank()) { "Repository store id is blank" }
        require(storeName.isNotBlank()) { "Repository store name is blank" }
        val parsed = runCatching { URI(repositoryUrl) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        require(
            (scheme == "http" || scheme == "https") && !parsed?.host.isNullOrBlank(),
        ) {
            "Repository URL must be a valid HTTP or HTTPS URL"
        }
        require(FINGERPRINT_PATTERN.matches(fingerprint)) {
            "Repository fingerprint must be 64 lowercase hex characters"
        }
    }

    /** `fdroidrepos://` URI accepted by F-Droid-compatible clients. */
    val uri: String by lazy {
        val parsed = URI(repositoryUrl)
        val authority = parsed.rawAuthority
            ?: throw IllegalArgumentException("Repository URL has no authority")
        val path = parsed.rawPath.orEmpty().trimEnd('/')
        val query = parsed.rawQuery?.takeIf(String::isNotBlank)
        buildString {
            append("fdroidrepos://")
            append(authority)
            append(path)
            append('?')
            if (query != null) {
                append(query)
                append('&')
            }
            append("fingerprint=")
            append(fingerprint)
        }
    }

    companion object {
        private val FINGERPRINT_PATTERN = Regex("^[0-9a-f]{64}$")

        /**
         * Builds a descriptor only for F-Droid-compatible stores with a usable
         * HTTPS endpoint and pinned fingerprint. HTTP is accepted only when the
         * store registry explicitly opted in.
         */
        fun from(descriptor: StoreDescriptor): RepositoryDescriptor? {
            if (
                descriptor.adapterType != AdapterType.FDROID_V1 &&
                descriptor.adapterType != AdapterType.FDROID_V2
            ) {
                return null
            }
            val repositoryUrl = (descriptor.repositoryUrl ?: descriptor.indexUrl)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: return null
            val parsed = runCatching { URI(repositoryUrl) }.getOrNull() ?: return null
            val scheme = parsed.scheme?.lowercase() ?: return null
            if (scheme != "https" && !(scheme == "http" && descriptor.allowInsecureHttp)) {
                return null
            }
            if (parsed.host.isNullOrBlank()) return null
            val fingerprint = StoreValidation.normalizeFingerprint(descriptor.fingerprint)
            if (!FINGERPRINT_PATTERN.matches(fingerprint)) return null
            return RepositoryDescriptor(
                storeId = descriptor.id.value,
                storeName = descriptor.name,
                repositoryUrl = repositoryUrl.trimEnd('/'),
                fingerprint = fingerprint,
            )
        }
    }
}

/** The same canonical repository exposed for each add path. */
data class RepositoryHandoff(
    val descriptor: RepositoryDescriptor,
) {
    val addRepositoryUri: String get() = descriptor.uri
    val qrPayload: String get() = descriptor.uri
    val copyPayload: String get() = descriptor.uri
}
