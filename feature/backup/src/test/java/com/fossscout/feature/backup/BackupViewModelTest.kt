package com.fossscout.feature.backup

import com.fossscout.core.backup.BackupCodec
import com.fossscout.core.backup.BackupConflictMode
import com.fossscout.core.backup.BackupEncryptionException
import com.fossscout.core.backup.BackupEncryptionReason
import com.fossscout.core.backup.BackupEnvelope
import com.fossscout.core.backup.BackupFeatureSettings
import com.fossscout.core.backup.BackupFormatException
import com.fossscout.core.backup.BackupFormatReason
import com.fossscout.core.backup.BackupHandoffPreferences
import com.fossscout.core.backup.BackupLocalState
import com.fossscout.core.backup.BackupPassphraseRequiredException
import com.fossscout.core.backup.BackupPayload
import com.fossscout.core.backup.BackupPlanner
import com.fossscout.core.backup.BackupRestorePlan
import com.fossscout.core.backup.BackupService
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.handoff.HandoffPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init prepares an export draft`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.exportDraft)
        assertEquals(1, service.prepareCalls)
    }

    @Test
    fun `plain export produces a share request`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)
        advanceUntilIdle()

        viewModel.exportBackup()
        advanceUntilIdle()

        val request = viewModel.uiState.value.exportRequest
        assertNotNull(request)
        assertEquals("plain", request?.content)
        assertFalse(request?.fileName.orEmpty().contains("encrypted"))
    }

    @Test
    fun `encrypted export validates the passphrase and clears it`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)
        advanceUntilIdle()

        viewModel.setEncryption(true)
        viewModel.setPassphrase("short")
        viewModel.setConfirmPassphrase("short")
        viewModel.exportBackup()
        advanceUntilIdle()
        assertEquals(BackupError.PASSPHRASE_TOO_SHORT, viewModel.uiState.value.error)

        viewModel.setPassphrase("correct horse")
        viewModel.setConfirmPassphrase("different horse")
        viewModel.exportBackup()
        advanceUntilIdle()
        assertEquals(BackupError.PASSPHRASE_MISMATCH, viewModel.uiState.value.error)

        viewModel.setConfirmPassphrase("correct horse")
        viewModel.exportBackup()
        advanceUntilIdle()

        assertEquals("encrypted", viewModel.uiState.value.exportRequest?.content)
        assertEquals("correct horse", service.lastPassphrase)
        assertEquals("", viewModel.uiState.value.passphrase)
        assertEquals("", viewModel.uiState.value.confirmPassphrase)
        assertTrue(viewModel.uiState.value.exportRequest?.fileName.orEmpty().contains("encrypted"))
    }

    @Test
    fun `encrypted import needs a passphrase and wrong passphrase can be retried`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)
        advanceUntilIdle()

        viewModel.startImport("encrypted", "backup.json")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.importNeedsPassphrase)
        assertNull(viewModel.uiState.value.importPreview)

        viewModel.setImportPassphrase("wrong horse")
        viewModel.unlockImport()
        advanceUntilIdle()
        assertEquals(BackupError.IMPORT_WRONG_PASSPHRASE, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.importNeedsPassphrase)
        assertNull(viewModel.uiState.value.importPreview)

        viewModel.setImportPassphrase("correct horse")
        viewModel.unlockImport()
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.importPreview)
        assertFalse(viewModel.uiState.value.importNeedsPassphrase)
        assertEquals("backup.json", viewModel.uiState.value.importFileName)
    }

    @Test
    fun `invalid import reports a format error without a preview`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)
        advanceUntilIdle()

        viewModel.startImport("bad", "bad.json")
        advanceUntilIdle()

        assertEquals(BackupError.IMPORT_MALFORMED, viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.importPreview)
    }

    @Test
    fun `mode changes replan and apply clears the preview`() = runTest {
        val service = FakeBackupService()
        val viewModel = BackupViewModel(service)
        advanceUntilIdle()

        viewModel.startImport("plain", "backup.json")
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.importPreview)

        viewModel.setImportMode(BackupConflictMode.REPLACE)
        advanceUntilIdle()
        assertEquals(BackupConflictMode.REPLACE, service.lastPreviewMode)

        viewModel.applyImport()
        advanceUntilIdle()

        assertEquals(1, service.applyCalls)
        assertNull(viewModel.uiState.value.importPreview)
        assertTrue(viewModel.uiState.value.restoreCompleted)
    }

    private class FakeBackupService : BackupService {
        private val codec = BackupCodec()
        val envelope: BackupEnvelope = codec.create(
            appVersion = "0.1.0",
            createdAtEpochMillis = 100,
            payload = BackupPayload(
                featureSettings = BackupFeatureSettings.from(FeatureSettings()),
                handoffPreferences = BackupHandoffPreferences.from(HandoffPreferences()),
            ),
        )
        var prepareCalls = 0
            private set
        var applyCalls = 0
            private set
        var lastPassphrase: String? = null
            private set
        var lastPreviewMode: BackupConflictMode? = null
            private set

        override fun isEncrypted(content: String): Boolean = content == "encrypted"

        override suspend fun prepareExport(): BackupEnvelope {
            prepareCalls += 1
            return envelope
        }

        override suspend fun encodeExport(
            envelope: BackupEnvelope,
            passphrase: CharArray?,
        ): String {
            lastPassphrase = passphrase?.concatToString()
            return if (passphrase == null) "plain" else "encrypted"
        }

        override suspend fun decodeImport(
            content: String,
            passphrase: CharArray?,
        ): BackupEnvelope {
            if (content == "bad") {
                throw BackupFormatException(
                    BackupFormatReason.MALFORMED,
                    "bad backup",
                )
            }
            if (content == "encrypted") {
                if (passphrase == null) throw BackupPassphraseRequiredException()
                if (passphrase.concatToString() != "correct horse") {
                    throw BackupEncryptionException(
                        BackupEncryptionReason.WRONG_PASSPHRASE_OR_TAMPERED,
                        "wrong passphrase",
                    )
                }
            }
            return envelope
        }

        override suspend fun preview(
            envelope: BackupEnvelope,
            mode: BackupConflictMode,
        ): BackupRestorePlan {
            lastPreviewMode = mode
            return BackupPlanner.plan(
                envelope = envelope,
                local = BackupLocalState(),
                mode = mode,
                nowEpochMillis = 1,
            )
        }

        override suspend fun apply(plan: BackupRestorePlan) {
            applyCalls += 1
        }
    }
}
