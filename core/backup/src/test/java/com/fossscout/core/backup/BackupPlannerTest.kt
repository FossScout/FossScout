package com.fossscout.core.backup

import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.IndexCacheSettings
import com.fossscout.core.handoff.HandoffPreferences
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.StoreId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPlannerTest {
    @Test
    fun `clean install replace reproduces the profile and fingerprint`() {
        val envelope = sampleEnvelope()

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = BackupLocalState(),
            mode = BackupConflictMode.REPLACE,
            nowEpochMillis = 500,
        )

        assertEquals(envelope.payload.favourites, plan.target.favourites)
        assertEquals(envelope.payload.collections, plan.target.collections)
        assertEquals(envelope.payload.customStores, plan.target.customStores)
        assertEquals(
            "a".repeat(64),
            plan.target.customStores.single().fingerprint,
        )
        assertEquals(1, plan.preview.addedFavouriteCount)
        assertEquals(1, plan.preview.notesItemCount)
        assertEquals(1, plan.target.handoffPreferences.preferredStores.size)
    }

    @Test
    fun `merge combines conflicts and keeps local settings`() {
        val localFavourite = sampleFavourite(
            note = "local note",
            tags = setOf("local"),
            preferredStoreId = StoreId("local-store"),
        )
        val incomingFavourite = sampleFavourite(
            displayName = "Incoming name",
            note = "backup note",
            tags = setOf("backup"),
            preferredStoreId = StoreId("backup-store"),
        )
        val localSettings = sampleSettings(
            disabled = setOf(FeatureId.STANDALONE),
            storageBudget = 128,
        )
        val incomingSettings = sampleSettings(
            disabled = setOf(FeatureId.UPDATES),
            storageBudget = 512,
        )
        val envelope = sampleEnvelope(
            samplePayload(
                favourites = listOf(incomingFavourite),
                featureSettings = incomingSettings,
            ),
        )
        val local = BackupLocalState(
            favourites = listOf(localFavourite),
            featureSettings = localSettings,
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.MERGE,
            nowEpochMillis = 500,
        )

        val merged = plan.target.favourites.single()
        assertEquals("Incoming name", merged.displayName)
        assertEquals("backup note", merged.note)
        assertEquals(setOf("local", "backup"), merged.tags)
        assertEquals(StoreId("backup-store"), merged.preferredStoreId)
        assertEquals(500, merged.updatedAtEpochMillis)
        assertEquals(
            setOf(FeatureId.STANDALONE, FeatureId.UPDATES),
            plan.target.featureSettings.disabledFeatureIds,
        )
        assertEquals(
            FeatureSettingValue.IntegerValue(128),
            plan.target.featureSettings.valueFor(
                FeatureId.INDEX_CACHE,
                IndexCacheSettings.storageBudget,
            ),
        )
        assertEquals(BackupChangeAction.MERGE, plan.preview.favourites.single().action)
    }

    @Test
    fun `replace removes local entries absent from the backup`() {
        val localFavourite = sampleFavourite(key = "org.local.app", note = "local")
        val localCollection = sampleCollection(id = "local-collection")
        val localStore = sampleStore(id = "local-store")
        val envelope = sampleEnvelope()
        val local = BackupLocalState(
            favourites = listOf(localFavourite),
            collections = listOf(localCollection),
            customStores = listOf(localStore),
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.REPLACE,
            nowEpochMillis = 500,
        )

        assertEquals(
            listOf(AppKey("org.example.app")),
            plan.target.favourites.map { it.appKey },
        )
        assertEquals(listOf("collection-1"), plan.target.collections.map { it.id })
        assertEquals(
            listOf("custom-repo"),
            plan.target.customStores.map { it.id.value },
        )
        assertEquals(1, plan.preview.removedFavouriteCount)
        assertEquals(1, plan.preview.removedCollectionCount)
        assertEquals(1, plan.preview.removedStoreCount)
    }

    @Test
    fun `skip only adds missing items and leaves settings alone`() {
        val localFavourite = sampleFavourite(note = "local note")
        val localSettings = sampleSettings(storageBudget = 128)
        val envelope = sampleEnvelope(
            samplePayload(
                favourites = listOf(
                    sampleFavourite(note = "backup note"),
                    sampleFavourite(key = "org.new.app", note = "new note"),
                ),
                featureSettings = sampleSettings(storageBudget = 512),
            ),
        )
        val local = BackupLocalState(
            favourites = listOf(localFavourite),
            featureSettings = localSettings,
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.SKIP,
            nowEpochMillis = 500,
        )

        val existing = plan.target.favourites.first { it.appKey == AppKey("org.example.app") }
        assertEquals("local note", existing.note)
        assertTrue(plan.target.favourites.any { it.appKey == AppKey("org.new.app") })
        assertEquals(BackupChangeAction.SKIP, plan.preview.favourites.first().action)
        assertEquals(localSettings, plan.target.featureSettings)
        assertFalse(plan.preview.settings.changed)
    }

    @Test
    fun `merge store conflict keeps the local fingerprint and reports the difference`() {
        val localStore = sampleStore(fingerprint = "c".repeat(64), trustNote = "local")
        val incomingStore = sampleStore(fingerprint = "d".repeat(64), trustNote = "backup")
        val envelope = sampleEnvelope(
            samplePayload(customStores = listOf(incomingStore)),
        )
        val local = BackupLocalState(customStores = listOf(localStore))

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.MERGE,
            nowEpochMillis = 500,
        )

        assertEquals("c".repeat(64), plan.target.customStores.single().fingerprint)
        assertTrue(plan.preview.stores.single().fingerprintChanged)
        assertTrue(plan.preview.stores.single().trustNoteChanged)
    }

    @Test
    fun `merge handoff keeps the local client and adds missing choices`() {
        val envelope = sampleEnvelope(
            samplePayload(
                handoffPreferences = HandoffPreferences(
                    preferredStores = mapOf(
                        "org.example.app" to "droidify",
                        "org.new.app" to "fdroid",
                    ),
                ),
            ),
        )
        val local = BackupLocalState(
            handoffPreferences = HandoffPreferences(
                preferredStores = mapOf("org.example.app" to "fdroid"),
            ),
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.MERGE,
            nowEpochMillis = 500,
        )

        assertEquals("fdroid", plan.target.handoffPreferences.preferredStores["org.example.app"])
        assertEquals("fdroid", plan.target.handoffPreferences.preferredStores["org.new.app"])
        assertEquals(1, plan.preview.handoff.added)
        assertEquals(0, plan.preview.handoff.removed)
    }

    @Test
    fun `replace includes incoming overrides and removes local-only ones`() {
        val incoming = sampleMatchOverride(installedAppKey = "org.example.app")
        val localOnly = sampleMatchOverride(installedAppKey = "org.local.app")
        val envelope = sampleEnvelope(
            samplePayload(matchOverrides = listOf(incoming)),
        )
        val local = BackupLocalState(matchOverrides = listOf(localOnly))

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.REPLACE,
            nowEpochMillis = 500,
        )

        assertEquals(listOf(incoming), plan.target.matchOverrides)
        assertEquals(1, plan.preview.addedMatchOverrideCount)
        assertEquals(1, plan.preview.removedMatchOverrideCount)
    }

    @Test
    fun `merge keeps the newest override for the same app`() {
        val local = sampleMatchOverride(
            installedAppKey = "org.example.app",
            note = "local",
            updatedAt = 30,
        )
        val olderIncoming = sampleMatchOverride(
            installedAppKey = "org.example.app",
            note = "older",
            updatedAt = 20,
        )
        val newerIncoming = sampleMatchOverride(
            installedAppKey = "org.example.app",
            note = "newer",
            updatedAt = 40,
        )
        val localState = BackupLocalState(matchOverrides = listOf(local))

        val olderPlan = BackupPlanner.plan(
            envelope = sampleEnvelope(
                samplePayload(matchOverrides = listOf(olderIncoming)),
            ),
            local = localState,
            mode = BackupConflictMode.MERGE,
            nowEpochMillis = 500,
        )
        val newerPlan = BackupPlanner.plan(
            envelope = sampleEnvelope(
                samplePayload(matchOverrides = listOf(newerIncoming)),
            ),
            local = localState,
            mode = BackupConflictMode.MERGE,
            nowEpochMillis = 500,
        )

        assertEquals(listOf(local), olderPlan.target.matchOverrides)
        assertEquals(listOf(newerIncoming), newerPlan.target.matchOverrides)
        assertEquals(
            BackupChangeAction.MERGE,
            newerPlan.preview.matchOverrides.single().action,
        )
        assertTrue(newerPlan.preview.matchOverrides.single().noteChanged)
    }

    @Test
    fun `skip keeps local overrides and adds only missing apps`() {
        val local = sampleMatchOverride(installedAppKey = "org.example.app", note = "local")
        val incomingConflict = sampleMatchOverride(
            installedAppKey = "org.example.app",
            note = "incoming",
        )
        val incomingNew = sampleMatchOverride(installedAppKey = "org.new.app")
        val envelope = sampleEnvelope(
            samplePayload(matchOverrides = listOf(incomingConflict, incomingNew)),
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = BackupLocalState(matchOverrides = listOf(local)),
            mode = BackupConflictMode.SKIP,
            nowEpochMillis = 500,
        )

        assertEquals(
            listOf(local, incomingNew),
            plan.target.matchOverrides,
        )
        assertEquals(BackupChangeAction.SKIP, plan.preview.matchOverrides.first().action)
        assertEquals(BackupChangeAction.ADD, plan.preview.matchOverrides.last().action)
    }

    @Test
    fun `replace settings removes local-only disabled features`() {
        val local = BackupLocalState(
            featureSettings = sampleSettings(
                disabled = setOf(FeatureId.STANDALONE, FeatureId.UPDATES),
                storageBudget = 128,
            ),
        )
        val envelope = sampleEnvelope(
            samplePayload(
                featureSettings = sampleSettings(
                    disabled = setOf(FeatureId.STANDALONE),
                    storageBudget = 512,
                ),
            ),
        )

        val plan = BackupPlanner.plan(
            envelope = envelope,
            local = local,
            mode = BackupConflictMode.REPLACE,
            nowEpochMillis = 500,
        )

        assertEquals(
            setOf(FeatureId.STANDALONE),
            plan.target.featureSettings.disabledFeatureIds,
        )
        assertEquals(1, plan.preview.settings.disabledRemoved)
        assertNotEquals(local.featureSettings, plan.target.featureSettings)
    }
}
