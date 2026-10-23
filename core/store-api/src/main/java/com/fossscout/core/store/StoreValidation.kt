package com.fossscout.core.store

import java.net.URI

enum class StoreValidationError {
    BLANK_ID,
    BLANK_NAME,
    MISSING_HOMEPAGE,
    INVALID_HOMEPAGE,
    INSECURE_HOMEPAGE,
    INVALID_INDEX_URL,
    INSECURE_INDEX_URL,
    INVALID_API_URL,
    INSECURE_API_URL,
    INVALID_REPOSITORY_URL,
    INSECURE_REPOSITORY_URL,
    INVALID_FINGERPRINT,
    NEGATIVE_PRIORITY,
    UNSUPPORTED_SCHEMA_VERSION,
}

class StoreValidationException(
    val errors: Set<StoreValidationError>,
) : IllegalArgumentException(
    "Invalid store descriptor: ${errors.joinToString { it.name }}",
)

/**
 * Pure validation shared by the repository, codecs, and tests. HTTP is only
 * accepted when the descriptor carries an explicit opt-in.
 */
object StoreValidation {
    private val fingerprintPattern = Regex("^[0-9a-f]{64}$")

    fun validate(descriptor: StoreDescriptor): Set<StoreValidationError> {
        val errors = linkedSetOf<StoreValidationError>()
        if (descriptor.id.value.isBlank()) errors += StoreValidationError.BLANK_ID
        if (descriptor.name.isBlank()) errors += StoreValidationError.BLANK_NAME
        if (descriptor.schemaVersion != STORE_DESCRIPTOR_SCHEMA_VERSION) {
            errors += StoreValidationError.UNSUPPORTED_SCHEMA_VERSION
        }
        if (descriptor.priority < 0) errors += StoreValidationError.NEGATIVE_PRIORITY

        if (descriptor.homepage.isBlank()) {
            errors += StoreValidationError.MISSING_HOMEPAGE
        } else {
            validateUrl(
                value = descriptor.homepage,
                allowInsecureHttp = descriptor.allowInsecureHttp,
                invalid = StoreValidationError.INVALID_HOMEPAGE,
                insecure = StoreValidationError.INSECURE_HOMEPAGE,
                errors = errors,
            )
        }
        descriptor.indexUrl
            ?.takeIf(String::isNotBlank)
            ?.let { value ->
                validateUrl(
                    value = value,
                    allowInsecureHttp = descriptor.allowInsecureHttp,
                    invalid = StoreValidationError.INVALID_INDEX_URL,
                    insecure = StoreValidationError.INSECURE_INDEX_URL,
                    errors = errors,
                )
            }
        descriptor.apiUrl
            ?.takeIf(String::isNotBlank)
            ?.let { value ->
                validateUrl(
                    value = value,
                    allowInsecureHttp = descriptor.allowInsecureHttp,
                    invalid = StoreValidationError.INVALID_API_URL,
                    insecure = StoreValidationError.INSECURE_API_URL,
                    errors = errors,
                )
            }
        descriptor.repositoryUrl
            ?.takeIf(String::isNotBlank)
            ?.let { value ->
                validateUrl(
                    value = value,
                    allowInsecureHttp = descriptor.allowInsecureHttp,
                    invalid = StoreValidationError.INVALID_REPOSITORY_URL,
                    insecure = StoreValidationError.INSECURE_REPOSITORY_URL,
                    errors = errors,
                )
            }

        val fingerprint = descriptor.fingerprint
        if (!fingerprint.isNullOrBlank() &&
            !fingerprintPattern.matches(normalizeFingerprint(fingerprint))
        ) {
            errors += StoreValidationError.INVALID_FINGERPRINT
        }
        return errors
    }

    fun requireValid(descriptor: StoreDescriptor) {
        val errors = validate(descriptor)
        if (errors.isNotEmpty()) throw StoreValidationException(errors)
    }

    /** Normalizes the common SHA-256 forms (spaces/colons/uppercase) to lowercase hex. */
    fun normalizeFingerprint(value: String?): String =
        value.orEmpty()
            .trim()
            .lowercase()
            .replace(":", "")
            .replace(" ", "")

    /** True when any supplied URL uses plain HTTP, so the UI can ask for opt-in. */
    fun hasInsecureHttp(draft: StoreDraft): Boolean = listOf(
        draft.homepage,
        draft.indexUrl,
        draft.apiUrl,
        draft.repositoryUrl,
    ).any { value ->
        value.trim().startsWith("http://", ignoreCase = true)
    }

    private fun validateUrl(
        value: String,
        allowInsecureHttp: Boolean,
        invalid: StoreValidationError,
        insecure: StoreValidationError,
        errors: MutableSet<StoreValidationError>,
    ) {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        when {
            uri == null || scheme !in setOf("http", "https") || host.isNullOrBlank() ->
                errors += invalid

            scheme == "http" && !allowInsecureHttp -> errors += insecure
        }
    }
}
