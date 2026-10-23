package com.fossscout.core.data

import com.fossscout.core.store.STORE_DESCRIPTOR_SCHEMA_VERSION
import com.fossscout.core.store.STORE_EXPORT_SCHEMA_VERSION
import com.fossscout.core.store.STORE_REGISTRY_SCHEMA_VERSION
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreDescriptorExport
import com.fossscout.core.store.StoreRegistry
import com.fossscout.core.store.StoreValidation
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The stored registry blob could not be decoded or has an unsupported version. */
class StoreRegistryFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class StoreRegistryEnvelope(
    val schemaVersion: Int,
    val stores: List<StoreDescriptor> = emptyList(),
)

/**
 * Versioned JSON codec for the store registry and portable descriptor export.
 *
 * Decoding never falls back to defaults for malformed or future data: callers
 * see [StoreRegistryFormatException] and must not overwrite the stored value.
 */
class StoreRegistryCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encodeRegistry(registry: StoreRegistry): String {
        require(registry.schemaVersion == STORE_REGISTRY_SCHEMA_VERSION) {
            "Unsupported store registry schema version: ${registry.schemaVersion}"
        }
        registry.stores.forEach(StoreValidation::requireValid)
        require(registry.stores.map { it.id }.toSet().size == registry.stores.size) {
            "Duplicate store IDs"
        }
        return json.encodeToString(
            StoreRegistryEnvelope(
                schemaVersion = registry.schemaVersion,
                stores = registry.stores,
            ),
        )
    }

    fun decodeRegistry(encoded: String): StoreRegistry {
        val envelope = decode(
            encoded = encoded,
            description = "store registry",
        ) { json.decodeFromString<StoreRegistryEnvelope>(it) }

        if (envelope.schemaVersion != STORE_REGISTRY_SCHEMA_VERSION) {
            throw StoreRegistryFormatException(
                "Unsupported store registry schema version: ${envelope.schemaVersion}",
            )
        }
        if (envelope.stores.map { it.id }.toSet().size != envelope.stores.size) {
            throw StoreRegistryFormatException("Duplicate store IDs")
        }
        envelope.stores.forEach(::requireDecodableDescriptor)

        return StoreRegistry(
            schemaVersion = envelope.schemaVersion,
            stores = envelope.stores,
        )
    }

    fun encodeDescriptor(descriptor: StoreDescriptor): String {
        StoreValidation.requireValid(descriptor)
        return json.encodeToString(
            StoreDescriptorExport(
                schemaVersion = STORE_EXPORT_SCHEMA_VERSION,
                descriptor = descriptor,
            ),
        )
    }

    fun decodeDescriptor(encoded: String): StoreDescriptor {
        val export = decode(
            encoded = encoded,
            description = "store descriptor export",
        ) { json.decodeFromString<StoreDescriptorExport>(it) }

        if (export.schemaVersion != STORE_EXPORT_SCHEMA_VERSION) {
            throw StoreRegistryFormatException(
                "Unsupported store export schema version: ${export.schemaVersion}",
            )
        }
        requireDecodableDescriptor(export.descriptor)
        return export.descriptor
    }

    private fun requireDecodableDescriptor(descriptor: StoreDescriptor) {
        if (descriptor.schemaVersion != STORE_DESCRIPTOR_SCHEMA_VERSION) {
            throw StoreRegistryFormatException(
                "Unsupported store descriptor schema version: ${descriptor.schemaVersion}",
            )
        }
        val errors = StoreValidation.validate(descriptor)
        if (errors.isNotEmpty()) {
            throw StoreRegistryFormatException(
                "Invalid store descriptor ${descriptor.id.value}: " +
                    errors.joinToString { it.name },
            )
        }
    }

    private fun <T> decode(
        encoded: String,
        description: String,
        block: (String) -> T,
    ): T = try {
        block(encoded)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: StoreRegistryFormatException) {
        throw exception
    } catch (exception: Exception) {
        throw StoreRegistryFormatException("Malformed $description data", exception)
    }
}
