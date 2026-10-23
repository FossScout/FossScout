package com.fossscout.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.CollectionsRepository
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.ImportConflictMode
import com.fossscout.core.data.ImportPreview
import com.fossscout.core.data.ShareCodec
import com.fossscout.core.data.ShareExportFactory
import com.fossscout.core.data.ShareFormatException
import com.fossscout.core.data.ShareFormatReason
import com.fossscout.core.data.ShareImportService
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.data.shareFileBaseName
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.ShareEnvelope
import com.fossscout.core.model.StoreId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SharingError {
    NOTHING_TO_SHARE,
    COLLECTION_MISSING,
    IMPORT_MALFORMED,
    IMPORT_UNSUPPORTED_VERSION,
    IMPORT_READ_FAILED,
    IMPORT_APPLY_FAILED,
}

data class ShareDraft(
    val envelope: ShareEnvelope,
    val includeNotes: Boolean = false,
)

/**
 * A ready-to-hand-to-the-app export. The app owns file writing, FileProvider
 * URIs, and the Android chooser so the feature module stays platform-light.
 */
data class ShareExportRequest(
    val subject: String,
    val fileName: String,
    val mimeType: String,
    val content: String,
)

data class SharingUiState(
    val shareDraft: ShareDraft? = null,
    val exportRequest: ShareExportRequest? = null,
    val importPreview: ImportPreview? = null,
    val keepImportedNotes: Boolean = false,
    val isApplyingImport: Boolean = false,
    val error: SharingError? = null,
    val importCompleted: Boolean = false,
)

/**
 * Offline sharing and previewed import for F12. Nothing is written until the
 * user confirms an [ImportPreview].
 */
class SharingViewModel(
    private val favouritesRepository: FavouritesRepository,
    private val collectionsRepository: CollectionsRepository,
    private val importService: ShareImportService,
    private val storeRegistry: StoreRegistryRepository? = null,
    private val exportFactory: ShareExportFactory = ShareExportFactory(),
    private val codec: ShareCodec = ShareCodec(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SharingUiState())
    val uiState: StateFlow<SharingUiState> = _uiState.asStateFlow()

    private var shareSource: ShareSource? = null

    fun shareFavourite(appKey: AppKey) {
        startShare(ShareSource.Favourite(appKey))
    }

    fun shareCollection(collectionId: String) {
        startShare(ShareSource.Collection(collectionId))
    }

    fun shareAllFavourites() {
        startShare(ShareSource.All)
    }

    fun setShareIncludeNotes(includeNotes: Boolean) {
        val source = shareSource ?: return
        viewModelScope.launch {
            try {
                val envelope = buildEnvelope(source, includeNotes)
                _uiState.update { state ->
                    state.copy(
                        shareDraft = ShareDraft(envelope = envelope, includeNotes = includeNotes),
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to rebuild the share draft", throwable)
                _uiState.update { state -> state.copy(error = errorFor(throwable)) }
            }
        }
    }

    fun exportJson() {
        val draft = _uiState.value.shareDraft ?: return
        val base = shareFileBaseName(draft.envelope.name)
        _uiState.update { state ->
            state.copy(
                exportRequest = ShareExportRequest(
                    subject = draft.envelope.name,
                    fileName = "$base.fossscout.json",
                    mimeType = "application/json",
                    content = codec.encodeJson(draft.envelope),
                ),
                shareDraft = null,
            )
        }
        shareSource = null
    }

    fun exportText() {
        val draft = _uiState.value.shareDraft ?: return
        val base = shareFileBaseName(draft.envelope.name)
        _uiState.update { state ->
            state.copy(
                exportRequest = ShareExportRequest(
                    subject = draft.envelope.name,
                    fileName = "$base.txt",
                    mimeType = "text/plain",
                    content = codec.encodeText(draft.envelope),
                ),
                shareDraft = null,
            )
        }
        shareSource = null
    }

    fun dismissShare() {
        shareSource = null
        _uiState.update { state ->
            state.copy(shareDraft = null, exportRequest = null)
        }
    }

    fun consumeExportRequest() {
        _uiState.update { state -> state.copy(exportRequest = null) }
    }

    fun startImport(encoded: String) {
        if (encoded.isBlank()) {
            _uiState.update { state -> state.copy(error = SharingError.IMPORT_MALFORMED) }
            return
        }
        viewModelScope.launch {
            try {
                val envelope = codec.decode(encoded)
                val preview = importService.preview(
                    envelope = envelope,
                    mode = ImportConflictMode.MERGE,
                    knownStoreIds = knownStoreIds(),
                )
                _uiState.update { state ->
                    state.copy(
                        importPreview = preview,
                        keepImportedNotes = false,
                        importCompleted = false,
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: ShareFormatException) {
                val error = when (exception.reason) {
                    ShareFormatReason.UNSUPPORTED_VERSION ->
                        SharingError.IMPORT_UNSUPPORTED_VERSION

                    ShareFormatReason.MALFORMED -> SharingError.IMPORT_MALFORMED
                }
                _uiState.update { state -> state.copy(error = error) }
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to preview an imported list", throwable)
                _uiState.update { state -> state.copy(error = SharingError.IMPORT_MALFORMED) }
            }
        }
    }

    fun setImportMode(mode: ImportConflictMode) {
        val preview = _uiState.value.importPreview ?: return
        viewModelScope.launch {
            try {
                val updated = importService.preview(
                    envelope = preview.envelope,
                    mode = mode,
                    knownStoreIds = knownStoreIds(),
                )
                _uiState.update { state ->
                    state.copy(importPreview = updated, error = null)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to update the import preview", throwable)
                _uiState.update { state -> state.copy(error = SharingError.IMPORT_MALFORMED) }
            }
        }
    }

    fun setKeepImportedNotes(keep: Boolean) {
        _uiState.update { state -> state.copy(keepImportedNotes = keep) }
    }

    fun applyImport() {
        val state = _uiState.value
        val preview = state.importPreview ?: return
        if (state.isApplyingImport) return
        _uiState.update { current -> current.copy(isApplyingImport = true, error = null) }
        viewModelScope.launch {
            try {
                importService.apply(
                    envelope = preview.envelope,
                    mode = preview.mode,
                    includeNotes = state.keepImportedNotes,
                    knownStoreIds = knownStoreIds(),
                )
                _uiState.update { current ->
                    current.copy(
                        importPreview = null,
                        keepImportedNotes = false,
                        isApplyingImport = false,
                        importCompleted = true,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to apply an imported list", throwable)
                _uiState.update { current ->
                    current.copy(
                        isApplyingImport = false,
                        error = SharingError.IMPORT_APPLY_FAILED,
                    )
                }
            }
        }
    }

    fun dismissImport() {
        if (_uiState.value.isApplyingImport) return
        _uiState.update { state -> state.copy(importPreview = null, keepImportedNotes = false) }
    }

    fun reportReadFailure() {
        _uiState.update { state -> state.copy(error = SharingError.IMPORT_READ_FAILED) }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    fun acknowledgeImportCompleted() {
        _uiState.update { state -> state.copy(importCompleted = false) }
    }

    private fun startShare(source: ShareSource) {
        shareSource = source
        viewModelScope.launch {
            try {
                val envelope = buildEnvelope(source, includeNotes = false)
                _uiState.update { state ->
                    state.copy(
                        shareDraft = ShareDraft(envelope = envelope),
                        error = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to build a share draft", throwable)
                _uiState.update { state -> state.copy(error = errorFor(throwable)) }
            }
        }
    }

    private suspend fun buildEnvelope(
        source: ShareSource,
        includeNotes: Boolean,
    ): ShareEnvelope {
        val favourites = favouritesRepository.favourites.first()
        return when (source) {
            is ShareSource.Favourite -> {
                val favourite = favourites.firstOrNull { it.appKey == source.appKey }
                    ?: throw NothingToShareException()
                exportFactory.forFavourite(favourite, includeNotes)
            }

            is ShareSource.Collection -> {
                val collection = collectionsRepository.collections.first()
                    .firstOrNull { it.id == source.id }
                    ?: throw MissingCollectionException()
                exportFactory.forCollection(
                    collection = collection,
                    favourites = favourites,
                    includeNotes = includeNotes,
                )
            }

            ShareSource.All -> {
                if (favourites.isEmpty()) throw NothingToShareException()
                exportFactory.forAllFavourites(favourites, includeNotes)
            }
        }
    }

    private suspend fun knownStoreIds(): Set<StoreId> {
        val registry = storeRegistry ?: return emptySet()
        return try {
            registry.entries.first().map { it.descriptor.id }.toSet()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to read stores for import validation", throwable)
            emptySet()
        }
    }

    private fun errorFor(throwable: Throwable): SharingError = when (throwable) {
        is MissingCollectionException -> SharingError.COLLECTION_MISSING
        is NothingToShareException -> SharingError.NOTHING_TO_SHARE
        else -> SharingError.NOTHING_TO_SHARE
    }

    private sealed interface ShareSource {
        data class Favourite(val appKey: AppKey) : ShareSource
        data class Collection(val id: String) : ShareSource
        data object All : ShareSource
    }

    private class MissingCollectionException : Exception()
    private class NothingToShareException : Exception()

    private companion object {
        const val TAG = "SharingViewModel"
    }
}
