package com.fossscout.core.backup

import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.MatchOverride
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreHealth

/** A passphrase is required before an encrypted import can be previewed. */
class BackupPassphraseRequiredException :
    Exception("This backup is encrypted and needs a passphrase")

/**
 * Export/import boundary used by the backup UI. Implementations own local data
 * access; the UI never writes repositories directly.
 */
interface BackupService {
    fun isEncrypted(content: String): Boolean

    suspend fun prepareExport(): BackupEnvelope

    suspend fun encodeExport(
        envelope: BackupEnvelope,
        passphrase: CharArray? = null,
    ): String

    suspend fun decodeImport(
        content: String,
        passphrase: CharArray? = null,
    ): BackupEnvelope

    suspend fun preview(
        envelope: BackupEnvelope,
        mode: BackupConflictMode,
    ): BackupRestorePlan

    suspend fun apply(plan: BackupRestorePlan)
}

object BackupExportFactory {
    fun create(
        appVersion: String,
        createdAtEpochMillis: Long,
        favourites: List<Favourite>,
        collections: List<AppCollection>,
        customStores: List<StoreDescriptor>,
        featureSettings: FeatureSettings,
        handoffPreferences: HandoffPreferences,
        matchOverrides: List<MatchOverride> = emptyList(),
        codec: BackupCodec = BackupCodec(),
    ): BackupEnvelope = codec.create(
        appVersion = appVersion,
        createdAtEpochMillis = createdAtEpochMillis,
        payload = BackupPayload(
            favourites = favourites,
            collections = collections,
            // Health is transient sync state, not part of the portable profile.
            customStores = customStores.map { it.copy(health = StoreHealth()) },
            featureSettings = BackupFeatureSettings.from(featureSettings),
            handoffPreferences = BackupHandoffPreferences.from(handoffPreferences),
            matchOverrides = matchOverrides.takeIf { it.isNotEmpty() },
        ),
    )
}
