package com.fossscout.core.backup

import com.fossscout.core.feature.FEATURE_SETTINGS_SCHEMA_VERSION
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingType
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.handoff.CompanionStores
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.COLLECTION_SCHEMA_VERSION
import com.fossscout.core.model.FAVOURITES_SCHEMA_VERSION
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.store.BuiltInStores
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreValidation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val BACKUP_SCHEMA_VERSION = 2
const val LEGACY_BACKUP_SCHEMA_VERSION = 1
const val BACKUP_FORMAT = "fossscout-backup"
const val ENCRYPTED_BACKUP_FORMAT = "fossscout-backup-encrypted"
const val BACKUP_PASSPHRASE_MIN_LENGTH = 8

/** Stable section names shown in the export manifest and restore preview. */
@Serializable
enum class BackupSection {
    @SerialName("favourites")
    FAVOURITES,

    @SerialName("notes")
    NOTES,

    @SerialName("collections")
    COLLECTIONS,

    @SerialName("custom_stores")
    CUSTOM_STORES,

    @SerialName("feature_settings")
    FEATURE_SETTINGS,

    @SerialName("preferred_store_clients")
    PREFERRED_STORE_CLIENTS,

    @SerialName("match_overrides")
    MATCH_OVERRIDES,
}

/** Data that is deliberately not part of a portable profile backup. */
@Serializable
enum class BackupExclusion {
    @SerialName("index_snapshots")
    INDEX_SNAPSHOTS,

    @SerialName("apk_files")
    APK_FILES,

    @SerialName("installed_app_inventory")
    INSTALLED_APP_INVENTORY,

    /** Kept so backups created before F20 still decode. */
    @SerialName("match_overrides")
    MATCH_OVERRIDES,

    @SerialName("secrets")
    SECRETS,
}

/**
 * Self-describing list of what a backup does and does not contain. The current
 * manifest includes every profile section so a restore on a clean install is
 * complete; the legacy manifest is accepted only for v1 imports.
 */
@Serializable
data class BackupManifest(
    val included: List<BackupSection> = INCLUDED_SECTIONS,
    val excluded: List<BackupExclusion> = EXCLUDED_SECTIONS,
) {
    val isCurrent: Boolean
        get() = included == INCLUDED_SECTIONS && excluded == EXCLUDED_SECTIONS

    val isLegacy: Boolean
        get() = included == LEGACY_INCLUDED_SECTIONS && excluded == LEGACY_EXCLUDED_SECTIONS

    fun validate() {
        require(isCurrent || isLegacy) {
            "Backup manifest does not match a supported profile"
        }
    }

    companion object {
        val INCLUDED_SECTIONS: List<BackupSection> = listOf(
            BackupSection.FAVOURITES,
            BackupSection.NOTES,
            BackupSection.COLLECTIONS,
            BackupSection.CUSTOM_STORES,
            BackupSection.FEATURE_SETTINGS,
            BackupSection.PREFERRED_STORE_CLIENTS,
            BackupSection.MATCH_OVERRIDES,
        )

        val EXCLUDED_SECTIONS: List<BackupExclusion> = listOf(
            BackupExclusion.INDEX_SNAPSHOTS,
            BackupExclusion.APK_FILES,
            BackupExclusion.INSTALLED_APP_INVENTORY,
            BackupExclusion.SECRETS,
        )

        val LEGACY_INCLUDED_SECTIONS: List<BackupSection> = listOf(
            BackupSection.FAVOURITES,
            BackupSection.NOTES,
            BackupSection.COLLECTIONS,
            BackupSection.CUSTOM_STORES,
            BackupSection.FEATURE_SETTINGS,
            BackupSection.PREFERRED_STORE_CLIENTS,
        )

        val LEGACY_EXCLUDED_SECTIONS: List<BackupExclusion> = listOf(
            BackupExclusion.INDEX_SNAPSHOTS,
            BackupExclusion.APK_FILES,
            BackupExclusion.INSTALLED_APP_INVENTORY,
            BackupExclusion.MATCH_OVERRIDES,
            BackupExclusion.SECRETS,
        )

        fun forPayload(): BackupManifest = BackupManifest()
    }
}

/**
 * Portable feature configuration. This mirrors the persisted feature-settings
 * shape without depending on Android DataStore. Unknown feature IDs and stale
 * settings are ignored when converting to [FeatureSettings], matching the
 * repository's sanitisation rules.
 */
@Serializable
data class BackupFeatureSettings(
    val disabledFeatureIds: List<String> = emptyList(),
    val values: List<BackupFeatureSetting> = emptyList(),
) {
    fun validate() {
        require(disabledFeatureIds.all(String::isNotBlank)) {
            "Disabled feature id is blank"
        }
        require(disabledFeatureIds.distinct().size == disabledFeatureIds.size) {
            "Duplicate disabled feature ids"
        }
        require(values.all { it.featureId.isNotBlank() && it.key.isNotBlank() }) {
            "Feature setting id or key is blank"
        }
        require(values.map { "${it.featureId}/${it.key}" }.distinct().size == values.size) {
            "Duplicate feature setting"
        }
        require(values.all { it.type in FeatureSettingType.entries.map(FeatureSettingType::name) }) {
            "Unknown feature setting type"
        }
    }

    fun toFeatureSettings(): FeatureSettings {
        val descriptors = FeatureCatalog.all.associateBy { it.id }
        val disabled = disabledFeatureIds.mapNotNull(::featureIdOrNull)
            .filterTo(mutableSetOf()) { it in descriptors }
        val converted = linkedMapOf<FeatureId, MutableMap<String, FeatureSettingValue>>()
        values.forEach { stored ->
            val featureId = featureIdOrNull(stored.featureId) ?: return@forEach
            val descriptor = descriptors[featureId] ?: return@forEach
            val setting = descriptor.settings.firstOrNull { it.key == stored.key }
                ?: return@forEach
            val value = when (stored.type) {
                FeatureSettingType.BOOLEAN.name ->
                    stored.value.toBooleanStrictOrNull()
                        ?.let(FeatureSettingValue::BooleanValue)

                FeatureSettingType.TEXT.name -> FeatureSettingValue.TextValue(stored.value)

                FeatureSettingType.INTEGER.name ->
                    stored.value.toIntOrNull()?.let(FeatureSettingValue::IntegerValue)

                else -> null
            } ?: return@forEach
            if (!setting.validate(value)) return@forEach
            converted.getOrPut(featureId, ::linkedMapOf)[stored.key] = value
        }
        return FeatureSettings(
            schemaVersion = FEATURE_SETTINGS_SCHEMA_VERSION,
            disabledFeatureIds = disabled,
            values = converted,
        )
    }

    companion object {
        fun from(settings: FeatureSettings): BackupFeatureSettings {
            val values = settings.values.entries
                .sortedBy { it.key.name }
                .flatMap { (featureId, featureValues) ->
                    featureValues.entries.sortedBy { it.key }.map { (key, value) ->
                        BackupFeatureSetting(
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
            return BackupFeatureSettings(
                disabledFeatureIds = settings.disabledFeatureIds
                    .map(FeatureId::name)
                    .sorted(),
                values = values,
            )
        }

        private fun featureIdOrNull(value: String): FeatureId? =
            FeatureId.entries.firstOrNull { it.name == value }
    }
}

@Serializable
data class BackupFeatureSetting(
    val featureId: String,
    val key: String,
    val type: String,
    val value: String,
)

@Serializable
data class BackupPreferredStore(
    val appKey: String,
    val clientId: String,
)

/** Preferred companion clients, stored as a sorted list for a stable checksum. */
@Serializable
data class BackupHandoffPreferences(
    val preferredStores: List<BackupPreferredStore> = emptyList(),
) {
    fun validate() {
        require(preferredStores.all { it.appKey.isNotBlank() && it.clientId.isNotBlank() }) {
            "Preferred store entry is blank"
        }
        require(preferredStores.map(BackupPreferredStore::appKey).distinct().size ==
            preferredStores.size) {
            "Duplicate preferred store app key"
        }
    }

    fun toHandoffPreferences(): HandoffPreferences {
        val knownClients = CompanionStores.all.map { it.id }.toSet()
        val stores = preferredStores
            .asSequence()
            .map { it.copy(appKey = it.appKey.trim(), clientId = it.clientId.trim()) }
            .filter { it.appKey.isNotEmpty() && it.clientId in knownClients }
            .distinctBy(BackupPreferredStore::appKey)
            .associate { it.appKey to it.clientId }
        return HandoffPreferences(preferredStores = stores)
    }

    companion object {
        fun from(preferences: HandoffPreferences): BackupHandoffPreferences =
            BackupHandoffPreferences(
                preferredStores = preferences.preferredStores.entries
                    .sortedBy { it.key }
                    .map { (appKey, clientId) ->
                        BackupPreferredStore(appKey = appKey, clientId = clientId)
                    },
            )
    }
}

/**
 * The complete portable profile. Inventory, index snapshots, and APKs are
 * intentionally absent. Match overrides are optional so v1 backups still decode.
 */
@Serializable
data class BackupPayload(
    val favourites: List<Favourite> = emptyList(),
    val collections: List<AppCollection> = emptyList(),
    val customStores: List<StoreDescriptor> = emptyList(),
    val featureSettings: BackupFeatureSettings? = null,
    val handoffPreferences: BackupHandoffPreferences? = null,
    val matchOverrides: List<MatchOverride>? = null,
) {
    fun validate() {
        require(favourites.map(Favourite::appKey).distinct().size == favourites.size) {
            "Duplicate favourite app keys"
        }
        favourites.forEach { favourite ->
            require(favourite.schemaVersion == FAVOURITES_SCHEMA_VERSION) {
                "Unsupported favourite schema version: ${favourite.schemaVersion}"
            }
            favourite.validate()
        }

        require(collections.map(AppCollection::id).distinct().size == collections.size) {
            "Duplicate collection ids"
        }
        collections.forEach { collection ->
            require(collection.schemaVersion == COLLECTION_SCHEMA_VERSION) {
                "Unsupported collection schema version: ${collection.schemaVersion}"
            }
            collection.validate()
        }

        require(customStores.none(StoreDescriptor::builtIn)) {
            "Built-in stores are provided by the app and cannot be restored from a backup"
        }
        val builtInIds = setOf(BuiltInStores.FDROID_ID, BuiltInStores.IZZY_ON_DROID_ID)
        require(customStores.none { it.id.value in builtInIds }) {
            "A custom store cannot reuse a built-in store id"
        }
        require(customStores.map { it.id }.distinct().size == customStores.size) {
            "Duplicate custom store ids"
        }
        customStores.forEach(StoreValidation::requireValid)

        require(featureSettings != null) { "Backup is missing feature settings" }
        require(handoffPreferences != null) { "Backup is missing preferred store clients" }
        featureSettings.validate()
        handoffPreferences.validate()

        val overrides = matchOverrides.orEmpty()
        require(overrides.map(MatchOverride::installedAppKey).distinct().size == overrides.size) {
            "Duplicate match override app keys"
        }
        overrides.forEach(MatchOverride::validate)
    }
}

/**
 * Versioned full-profile backup. [checksum] is a SHA-256 over the canonical
 * metadata and payload; encryption is a separate outer envelope so the same
 * plain format can be inspected and tested.
 */
@Serializable
data class BackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val appVersion: String,
    val createdAtEpochMillis: Long,
    val manifest: BackupManifest = BackupManifest(),
    val payload: BackupPayload = BackupPayload(),
    val checksum: String = "",
) {
    fun validateStructure() {
        require(format == BACKUP_FORMAT) { "Unsupported backup format: $format" }
        require(schemaVersion == BACKUP_SCHEMA_VERSION) {
            "Unsupported backup schema version: $schemaVersion"
        }
        require(appVersion.isNotBlank()) { "Backup app version is blank" }
        require(createdAtEpochMillis >= 0) { "Backup creation time is negative" }
        require(CHECKSUM_PATTERN.matches(checksum)) {
            "Backup checksum must be 64 lowercase hex characters"
        }
        require(manifest.isCurrent) {
            "Backup manifest must list every current profile section"
        }
        payload.validate()
    }

    companion object {
        val CHECKSUM_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
