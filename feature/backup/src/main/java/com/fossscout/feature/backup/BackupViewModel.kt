package com.fossscout.feature.backup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.backup.BACKUP_PASSPHRASE_MIN_LENGTH
import com.fossscout.core.backup.BackupConflictMode
import com.fossscout.core.backup.BackupEncryptionException
import com.fossscout.core.backup.BackupEncryptionReason
import com.fossscout.core.backup.BackupEnvelope
import com.fossscout.core.backup.BackupFormatException
import com.fossscout.core.backup.BackupFormatReason
import com.fossscout.core.backup.BackupPassphraseRequiredException
import com.fossscout.core.backup.BackupRestorePlan
import com.fossscout.core.backup.BackupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class BackupError {
    READ_FAILED,
    EXPORT_FAILED,
    IMPORT_MALFORMED,
    IMPORT_UNSUPPORTED_VERSION,
    IMPORT_UNSUPPORTED_FORMAT,
    IMPORT_CHECKSUM_MISMATCH,
    IMPORT_WRONG_PASSPHRASE,
    IMPORT_PASSPHRASE_REQUIRED,
    IMPORT_READ_FAILED,
    IMPORT_APPLY_FAILED,
    PASSPHRASE_TOO_SHORT,
    PASSPHRASE_MISMATCH,
}

data class BackupExportRequest(
    val subject: String,
    val fileName: String,
    val mimeType: String,
    val content: String,
)

data class BackupUiState(
    val isLoading: Boolean = true,
    val exportDraft: BackupEnvelope? = null,
    val exportRequest: BackupExportRequest? = null,
    val isExporting: Boolean = false,
    val useEncryption: Boolean = false,
    val passphrase: String = "",
    val confirmPassphrase: String = "",
    val importPreview: BackupRestorePlan? = null,
    val importMode: BackupConflictMode = BackupConflictMode.MERGE,
    val importNeedsPassphrase: Boolean = false,
    val importPassphrase: String = "",
    val importFileName: String? = null,
    val isPreviewing: Boolean = false,
    val isApplying: Boolean = false,
    val restoreCompleted: Boolean = false,
    val error: BackupError? = null,
)

/**
 * Backup UI state. The ViewModel never writes repositories directly: the data
 * layer validates and plans first, then applies with rollback.
 */
class BackupViewModel(
    private val service: BackupService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var pendingImportContent: String? = null
    private var pendingImportEnvelope: BackupEnvelope? = null
    private var previewJob: Job? = null
    private var refreshJob: Job? = null

    init {
        refreshExport()
    }

    fun refreshExport() {
        if (_uiState.value.isLoading && _uiState.value.exportDraft != null) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val draft = service.prepareExport()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exportDraft = draft,
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not prepare backup export", throwable)
                _uiState.update {
                    it.copy(isLoading = false, error = BackupError.READ_FAILED)
                }
            }
        }
    }

    fun setEncryption(enabled: Boolean) {
        _uiState.update {
            it.copy(
                useEncryption = enabled,
                passphrase = if (enabled) it.passphrase else "",
                confirmPassphrase = if (enabled) it.confirmPassphrase else "",
                error = null,
            )
        }
    }

    fun setPassphrase(value: String) {
        _uiState.update { it.copy(passphrase = value, error = null) }
    }

    fun setConfirmPassphrase(value: String) {
        _uiState.update { it.copy(confirmPassphrase = value, error = null) }
    }

    fun exportBackup() {
        val state = _uiState.value
        if (state.isExporting) return
        val draft = state.exportDraft
        if (draft == null) {
            _uiState.update { it.copy(error = BackupError.READ_FAILED) }
            return
        }
        if (state.useEncryption) {
            if (state.passphrase.length < BACKUP_PASSPHRASE_MIN_LENGTH) {
                _uiState.update { it.copy(error = BackupError.PASSPHRASE_TOO_SHORT) }
                return
            }
            if (state.passphrase != state.confirmPassphrase) {
                _uiState.update { it.copy(error = BackupError.PASSPHRASE_MISMATCH) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, error = null) }
            val passphrase = if (state.useEncryption) state.passphrase.toCharArray() else null
            val suffix = if (state.useEncryption) "-encrypted" else ""
            try {
                val content = service.encodeExport(draft, passphrase)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        passphrase = "",
                        confirmPassphrase = "",
                        exportRequest = BackupExportRequest(
                            subject = "FossScout backup",
                            fileName = "fossscout-backup-${draft.createdAtEpochMillis}$suffix.json",
                            mimeType = "application/json",
                            content = content,
                        ),
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not encode backup export", throwable)
                _uiState.update {
                    it.copy(isExporting = false, error = BackupError.EXPORT_FAILED)
                }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    fun consumeExportRequest() {
        _uiState.update { it.copy(exportRequest = null) }
    }

    fun startImport(content: String, fileName: String?) {
        previewJob?.cancel()
        pendingImportEnvelope = null
        pendingImportContent = null
        _uiState.update {
            it.copy(
                importPreview = null,
                importNeedsPassphrase = false,
                importPassphrase = "",
                importFileName = fileName,
                isPreviewing = false,
                restoreCompleted = false,
                error = null,
            )
        }
        if (content.isBlank()) {
            _uiState.update { it.copy(error = BackupError.IMPORT_MALFORMED) }
            return
        }
        if (service.isEncrypted(content)) {
            pendingImportContent = content
            _uiState.update { it.copy(importNeedsPassphrase = true) }
        } else {
            loadPreview(content = content, passphrase = null)
        }
    }

    fun setImportPassphrase(value: String) {
        _uiState.update { it.copy(importPassphrase = value, error = null) }
    }

    fun unlockImport() {
        val content = pendingImportContent ?: return
        val passphrase = _uiState.value.importPassphrase
        if (passphrase.isBlank()) {
            _uiState.update { it.copy(error = BackupError.IMPORT_PASSPHRASE_REQUIRED) }
            return
        }
        loadPreview(content = content, passphrase = passphrase.toCharArray())
    }

    fun setImportMode(mode: BackupConflictMode) {
        if (_uiState.value.importMode == mode) return
        _uiState.update { it.copy(importMode = mode, error = null) }
        val envelope = pendingImportEnvelope ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isPreviewing = true, error = null) }
            try {
                val plan = service.preview(envelope, mode)
                if (!isActive) return@launch
                _uiState.update {
                    it.copy(isPreviewing = false, importPreview = plan)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (!isActive) return@launch
                Log.e(TAG, "Could not re-plan backup import", throwable)
                _uiState.update {
                    it.copy(isPreviewing = false, error = mapImportError(throwable))
                }
            }
        }
    }

    fun applyImport() {
        val state = _uiState.value
        if (state.isApplying) return
        val plan = state.importPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, error = null) }
            try {
                service.apply(plan)
                val refreshedDraft = try {
                    service.prepareExport()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                pendingImportContent = null
                pendingImportEnvelope = null
                _uiState.update {
                    it.copy(
                        isApplying = false,
                        importPreview = null,
                        importNeedsPassphrase = false,
                        importPassphrase = "",
                        importFileName = null,
                        restoreCompleted = true,
                        exportDraft = refreshedDraft ?: it.exportDraft,
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Could not apply backup import", throwable)
                _uiState.update {
                    it.copy(isApplying = false, error = BackupError.IMPORT_APPLY_FAILED)
                }
            }
        }
    }

    fun dismissImport() {
        if (_uiState.value.isApplying) return
        previewJob?.cancel()
        pendingImportContent = null
        pendingImportEnvelope = null
        _uiState.update {
            it.copy(
                importPreview = null,
                importNeedsPassphrase = false,
                importPassphrase = "",
                importFileName = null,
                isPreviewing = false,
                restoreCompleted = false,
                error = null,
            )
        }
    }

    fun acknowledgeRestoreCompleted() {
        _uiState.update { it.copy(restoreCompleted = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reportReadFailure() {
        previewJob?.cancel()
        pendingImportContent = null
        pendingImportEnvelope = null
        _uiState.update {
            it.copy(
                isPreviewing = false,
                importPreview = null,
                importNeedsPassphrase = false,
                importPassphrase = "",
                importFileName = null,
                restoreCompleted = false,
                error = BackupError.IMPORT_READ_FAILED,
            )
        }
    }

    private fun loadPreview(content: String, passphrase: CharArray?) {
        previewJob?.cancel()
        val wasEncrypted = service.isEncrypted(content)
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreviewing = true,
                    importPreview = null,
                    importNeedsPassphrase = false,
                    error = null,
                )
            }
            try {
                val envelope = service.decodeImport(content, passphrase)
                val plan = service.preview(envelope, _uiState.value.importMode)
                if (!isActive) return@launch
                pendingImportContent = content
                pendingImportEnvelope = envelope
                _uiState.update {
                    it.copy(
                        isPreviewing = false,
                        importPreview = plan,
                        importPassphrase = "",
                        importNeedsPassphrase = false,
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (!isActive) return@launch
                Log.e(TAG, "Could not preview backup import", throwable)
                val error = mapImportError(throwable)
                _uiState.update {
                    it.copy(
                        isPreviewing = false,
                        importPreview = null,
                        importNeedsPassphrase = wasEncrypted &&
                            (error == BackupError.IMPORT_WRONG_PASSPHRASE ||
                                error == BackupError.IMPORT_PASSPHRASE_REQUIRED),
                        error = error,
                    )
                }
            } finally {
                passphrase?.fill('\u0000')
            }
        }
    }

    private fun mapImportError(throwable: Throwable): BackupError = when (throwable) {
        is BackupPassphraseRequiredException -> BackupError.IMPORT_PASSPHRASE_REQUIRED
        is BackupFormatException -> when (throwable.reason) {
            BackupFormatReason.UNSUPPORTED_VERSION -> BackupError.IMPORT_UNSUPPORTED_VERSION
            BackupFormatReason.UNSUPPORTED_FORMAT -> BackupError.IMPORT_UNSUPPORTED_FORMAT
            BackupFormatReason.CHECKSUM_MISMATCH -> BackupError.IMPORT_CHECKSUM_MISMATCH
            BackupFormatReason.MALFORMED -> BackupError.IMPORT_MALFORMED
        }

        is BackupEncryptionException -> when (throwable.reason) {
            BackupEncryptionReason.UNSUPPORTED_VERSION ->
                BackupError.IMPORT_UNSUPPORTED_VERSION

            BackupEncryptionReason.WRONG_PASSPHRASE_OR_TAMPERED ->
                BackupError.IMPORT_WRONG_PASSPHRASE

            BackupEncryptionReason.MALFORMED -> BackupError.IMPORT_MALFORMED
        }

        else -> BackupError.IMPORT_MALFORMED
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}
