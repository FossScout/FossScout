package com.fossscout.core.data

import com.fossscout.core.feature.FEATURE_SETTINGS_SCHEMA_VERSION
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingType
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The stored feature-settings blob could not be decoded or has an unsupported version. */
class FeatureSettingsFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Serializable
private data class FeatureSettingsEnvelope(
    val schemaVersion: Int,
    val disabledFeatureIds: List<String> = emptyList(),
    val values: List<StoredFeatureSetting> = emptyList(),
)

@Serializable
private data class StoredFeatureSetting(
    val featureId: String,
    val key: String,
    val type: String,
    val value: String,
)

/**
 * Encodes feature configuration inside a versioned envelope. Decoding never
 * silently replaces a newer or malformed envelope; callers show an error
 * instead of overwriting data they could not read.
 *
 * Unknown feature IDs, setting types, and malformed individual values are
 * dropped so the remaining configuration still loads. Missing values then fall
 * back to the declared defaults.
 */
class FeatureSettingsCodec(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {
    fun encode(settings: FeatureSettings): String {
        val values = settings.values.entries
            .sortedBy { entry -> entry.key.name }
            .flatMap { (featureId, featureValues) ->
                featureValues.entries
                    .sortedBy { entry -> entry.key }
                    .map { (key, value) ->
                        StoredFeatureSetting(
                            featureId = featureId.name,
                            key = key,
                            type = value.type.name,
                            value = when (value) {
                                is FeatureSettingValue.BooleanValue -> value.value.toString()
                                is FeatureSettingValue.TextValue -> value.value
                                is FeatureSettingValue.IntegerValue -> value.value.toString()
                            },
                        )
                    }
            }

        return json.encodeToString(
            FeatureSettingsEnvelope(
                schemaVersion = FEATURE_SETTINGS_SCHEMA_VERSION,
                disabledFeatureIds = settings.disabledFeatureIds
                    .map(FeatureId::name)
                    .sorted(),
                values = values,
            ),
        )
    }

    fun decode(encoded: String): FeatureSettings {
        val envelope = try {
            json.decodeFromString<FeatureSettingsEnvelope>(encoded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            throw FeatureSettingsFormatException("Malformed feature settings data", exception)
        }

        if (envelope.schemaVersion != FEATURE_SETTINGS_SCHEMA_VERSION) {
            throw FeatureSettingsFormatException(
                "Unsupported feature settings schema version: ${envelope.schemaVersion}",
            )
        }

        val disabled = envelope.disabledFeatureIds
            .mapNotNull(::featureIdOrNull)
            .toSet()
        val values = linkedMapOf<FeatureId, MutableMap<String, FeatureSettingValue>>()
        envelope.values.forEach { stored ->
            val featureId = featureIdOrNull(stored.featureId) ?: return@forEach
            if (stored.key.isBlank()) return@forEach
            val value = when (stored.type) {
                FeatureSettingType.BOOLEAN.name ->
                    stored.value.toBooleanStrictOrNull()
                        ?.let(FeatureSettingValue::BooleanValue)

                FeatureSettingType.TEXT.name -> FeatureSettingValue.TextValue(stored.value)

                FeatureSettingType.INTEGER.name ->
                    stored.value.toIntOrNull()?.let(FeatureSettingValue::IntegerValue)

                else -> null
            } ?: return@forEach
            values.getOrPut(featureId, ::linkedMapOf)[stored.key] = value
        }

        return FeatureSettings(
            schemaVersion = envelope.schemaVersion,
            disabledFeatureIds = disabled,
            values = values,
        )
    }

    private fun featureIdOrNull(value: String): FeatureId? =
        FeatureId.entries.firstOrNull { id -> id.name == value }
}

/**
 * Converts the legacy `disabled_features` preference into the versioned model.
 * Unknown IDs are ignored; every recognised disabled feature is preserved.
 */
internal fun migrateLegacyDisabledFeatures(disabled: Set<String>): FeatureSettings =
    FeatureSettings(
        disabledFeatureIds = disabled.mapNotNull { stored ->
            FeatureId.entries.firstOrNull { id -> id.name == stored }
        }.toSet(),
    )
