package com.fossscout.core.data

import com.fossscout.core.backup.BackupCodec
import com.fossscout.core.backup.BackupConflictMode
import com.fossscout.core.backup.BackupCrypto
import com.fossscout.core.backup.BackupEnvelope
import com.fossscout.core.backup.BackupExportFactory
import com.fossscout.core.backup.BackupLocalState
import com.fossscout.core.backup.BackupPassphraseRequiredException
import com.fossscout.core.backup.BackupPlanner
import com.fossscout.core.backup.BackupRestorePlan
import com.fossscout.core.backup.BackupService
import com.fossscout.core.handoff.HandoffPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Local data was not changed because the restore failed. */
class BackupApplyException(
    message: String,
    cause: Throwable? = null,
    val rollbackFailure: Throwable? = null,
) : Exception(message, cause)

/**
 * Applies the backup contract to the local repositories.
 *
 * Import is validated and planned before any write. If a later repository write
 * fails, earlier writes are rolled back to the captured previous state. Index
 * snapshots, inventory, and APKs are never read or written here.
 */
class DefaultBackupService(
    private val favouritesRepository: FavouritesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val storeRegistryRepository: StoreRegistryRepository,
    private val featureSettingsRepository: FeatureSettingsRepository,
    private val handoffPreferencesRepository: HandoffPreferencesRepository,
    private val matchOverridesRepository: MatchOverridesRepository,
    private val appVersion: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val codec: BackupCodec = BackupCodec(),
    private val crypto: BackupCrypto = BackupCrypto(),
) : BackupService {
    override fun isEncrypted(content: String): Boolean = crypto.isEncrypted(content)

    override suspend fun prepareExport(): BackupEnvelope = withContext(Dispatchers.IO) {
        val local = readLocalState()
        BackupExportFactory.create(
            appVersion = appVersion(),
            createdAtEpochMillis = now(),
            favourites = local.favourites,
            collections = local.collections,
            customStores = local.customStores,
            featureSettings = local.featureSettings,
            handoffPreferences = local.handoffPreferences,
            matchOverrides = local.matchOverrides,
            codec = codec,
        )
    }

    override suspend fun encodeExport(
        envelope: BackupEnvelope,
        passphrase: CharArray?,
    ): String = withContext(Dispatchers.IO) {
        val encoded = codec.encode(envelope)
        if (passphrase == null) {
            encoded
        } else {
            crypto.encrypt(encoded, passphrase)
        }
    }

    override suspend fun decodeImport(
        content: String,
        passphrase: CharArray?,
    ): BackupEnvelope = withContext(Dispatchers.IO) {
        val decoded = if (crypto.isEncrypted(content)) {
            if (passphrase == null || passphrase.isEmpty()) {
                throw BackupPassphraseRequiredException()
            }
            crypto.decrypt(content, passphrase)
        } else {
            content
        }
        codec.decode(decoded)
    }

    override suspend fun preview(
        envelope: BackupEnvelope,
        mode: BackupConflictMode,
    ): BackupRestorePlan = withContext(Dispatchers.IO) {
        BackupPlanner.plan(
            envelope = envelope,
            local = readLocalState(),
            mode = mode,
            nowEpochMillis = now(),
        )
    }

    override suspend fun apply(plan: BackupRestorePlan) = withContext(Dispatchers.IO) {
        val previous = plan.previous
        val target = plan.target
        val rollbacks = mutableListOf<suspend () -> Unit>()
        try {
            if (target.favourites != previous.favourites) {
                favouritesRepository.replaceAll(target.favourites)
                rollbacks += { favouritesRepository.replaceAll(previous.favourites) }
            }
            if (target.collections != previous.collections) {
                collectionsRepository.replaceAll(target.collections)
                rollbacks += { collectionsRepository.replaceAll(previous.collections) }
            }
            if (target.customStores != previous.customStores) {
                storeRegistryRepository.replaceCustom(target.customStores)
                rollbacks += { storeRegistryRepository.replaceCustom(previous.customStores) }
            }
            if (target.featureSettings != previous.featureSettings) {
                featureSettingsRepository.replaceSnapshot(target.featureSettings)
                rollbacks += {
                    featureSettingsRepository.replaceSnapshot(previous.featureSettings)
                }
            }
            if (target.handoffPreferences != previous.handoffPreferences) {
                handoffPreferencesRepository.replace(target.handoffPreferences)
                rollbacks += {
                    handoffPreferencesRepository.replace(previous.handoffPreferences)
                }
            }
            if (target.matchOverrides != previous.matchOverrides) {
                matchOverridesRepository.replaceAll(target.matchOverrides)
                rollbacks += {
                    matchOverridesRepository.replaceAll(previous.matchOverrides)
                }
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) { rollback(rollbacks) }
            throw cancellation
        } catch (throwable: Throwable) {
            val rollbackFailure = withContext(NonCancellable) { rollback(rollbacks) }
            throw BackupApplyException(
                message = "Backup restore failed",
                cause = throwable,
                rollbackFailure = rollbackFailure,
            )
        }
    }

    private suspend fun readLocalState(): BackupLocalState = BackupLocalState(
        favourites = favouritesRepository.favourites.first(),
        collections = collectionsRepository.collections.first(),
        customStores = storeRegistryRepository.entries.first()
            .filterNot { it.descriptor.builtIn }
            .map { it.descriptor },
        featureSettings = featureSettingsRepository.snapshot.first(),
        handoffPreferences = handoffPreferencesRepository.preferences.first(),
        matchOverrides = matchOverridesRepository.overrides.first(),
    )

    private suspend fun rollback(rollbacks: List<suspend () -> Unit>): Throwable? {
        var failure: Throwable? = null
        rollbacks.asReversed().forEach { rollback ->
            try {
                rollback()
            } catch (throwable: Throwable) {
                if (failure == null) failure = throwable
            }
        }
        return failure
    }
}
