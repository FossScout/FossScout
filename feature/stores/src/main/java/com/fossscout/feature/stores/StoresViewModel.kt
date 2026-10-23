package com.fossscout.feature.stores

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.model.StoreId
import com.fossscout.core.store.AdapterType
import com.fossscout.core.store.StoreDescriptor
import com.fossscout.core.store.StoreDraft
import com.fossscout.core.store.StoreEntry
import com.fossscout.core.store.StoreValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StoresError { READ_FAILED, WRITE_FAILED, INVALID_STORE, HTTP_OPT_IN_REQUIRED }

data class StoreEditor(
    val id: StoreId? = null,
    val name: String = "",
    val homepage: String = "",
    val adapterType: AdapterType = AdapterType.FDROID_V2,
    val indexUrl: String = "",
    val apiUrl: String = "",
    val repositoryUrl: String = "",
    val fingerprint: String = "",
    val trustNote: String = "",
    val allowInsecureHttp: Boolean = false,
) {
    val isEditing: Boolean get() = id != null

    fun toDraft(): StoreDraft = StoreDraft(
        name = name,
        homepage = homepage,
        adapterType = adapterType,
        indexUrl = indexUrl,
        apiUrl = apiUrl,
        repositoryUrl = repositoryUrl,
        fingerprint = fingerprint,
        trustNote = trustNote,
        allowInsecureHttp = allowInsecureHttp,
    )

    fun usesInsecureHttp(): Boolean = StoreValidation.hasInsecureHttp(toDraft())

    companion object {
        fun from(descriptor: StoreDescriptor): StoreEditor = StoreEditor(
            id = descriptor.id,
            name = descriptor.name,
            homepage = descriptor.homepage,
            adapterType = descriptor.adapterType,
            indexUrl = descriptor.indexUrl.orEmpty(),
            apiUrl = descriptor.apiUrl.orEmpty(),
            repositoryUrl = descriptor.repositoryUrl.orEmpty(),
            fingerprint = descriptor.fingerprint.orEmpty(),
            trustNote = descriptor.trustNote,
            allowInsecureHttp = descriptor.allowInsecureHttp,
        )
    }
}

data class StoreDeleteRequest(
    val id: StoreId,
    val name: String,
)

data class StoreExportRequest(
    val name: String,
    val json: String,
)

data class StoresUiState(
    val entries: List<StoreEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: StoresError? = null,
    val editor: StoreEditor? = null,
    val deleteRequest: StoreDeleteRequest? = null,
    val exportRequest: StoreExportRequest? = null,
)

class StoresViewModel(
    private val repository: StoreRegistryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StoresUiState())
    val uiState: StateFlow<StoresUiState> = _uiState.asStateFlow()

    private var readJob: Job? = null

    init {
        collectStores()
    }

    fun retry() {
        collectStores()
    }

    fun startAdd() {
        _uiState.update { state ->
            state.copy(
                editor = StoreEditor(),
                error = state.error.takeUnless { it == StoresError.READ_FAILED },
            )
        }
    }

    fun startEdit(descriptor: StoreDescriptor) {
        _uiState.update { state ->
            state.copy(
                editor = StoreEditor.from(descriptor),
                error = state.error.takeUnless { it == StoresError.READ_FAILED },
            )
        }
    }

    fun updateEditor(editor: StoreEditor) {
        _uiState.update { state -> state.copy(editor = editor) }
    }

    fun dismissEditor() {
        _uiState.update { state -> state.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (editor.usesInsecureHttp() && !editor.allowInsecureHttp) {
            _uiState.update { state ->
                state.copy(error = StoresError.HTTP_OPT_IN_REQUIRED)
            }
            return
        }

        write {
            if (editor.isEditing) {
                repository.update(checkNotNull(editor.id), editor.toDraft())
            } else {
                repository.add(editor.toDraft())
            }
            _uiState.update { state ->
                state.copy(
                    editor = null,
                    error = state.error?.takeUnless {
                        it == StoresError.WRITE_FAILED ||
                            it == StoresError.INVALID_STORE ||
                            it == StoresError.HTTP_OPT_IN_REQUIRED
                    },
                )
            }
        }
    }

    fun setEnabled(id: StoreId, enabled: Boolean) = write {
        repository.setEnabled(id, enabled)
    }

    fun moveUp(id: StoreId) = write {
        repository.move(id, offset = -1)
    }

    fun moveDown(id: StoreId) = write {
        repository.move(id, offset = 1)
    }

    fun requestDelete(descriptor: StoreDescriptor) {
        _uiState.update { state ->
            state.copy(
                deleteRequest = StoreDeleteRequest(
                    id = descriptor.id,
                    name = descriptor.name,
                ),
            )
        }
    }

    fun dismissDelete() {
        _uiState.update { state -> state.copy(deleteRequest = null) }
    }

    fun confirmDelete() {
        val request = _uiState.value.deleteRequest ?: return
        write {
            repository.delete(request.id)
            _uiState.update { state -> state.copy(deleteRequest = null) }
        }
    }

    fun export(id: StoreId) = write {
        val descriptor = _uiState.value.entries
            .firstOrNull { it.descriptor.id == id }
            ?.descriptor
            ?: return@write
        val json = repository.exportDescriptor(id)
        _uiState.update { state ->
            state.copy(
                exportRequest = StoreExportRequest(
                    name = descriptor.name,
                    json = json,
                ),
            )
        }
    }

    fun dismissExport() {
        _uiState.update { state -> state.copy(exportRequest = null) }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    private fun collectStores() {
        readJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                error = state.error.takeUnless { it == StoresError.READ_FAILED },
            )
        }
        readJob = viewModelScope.launch {
            repository.entries
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read store registry", throwable)
                    _uiState.update { state ->
                        state.copy(
                            entries = emptyList(),
                            isLoading = false,
                            error = StoresError.READ_FAILED,
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update { state ->
                        state.copy(
                            entries = entries,
                            isLoading = false,
                            error = state.error
                                ?.takeUnless { it == StoresError.READ_FAILED },
                        )
                    }
                }
        }
    }

    private fun write(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                _uiState.update { state ->
                    state.copy(
                        error = state.error
                            ?.takeUnless { it == StoresError.WRITE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (throwable is IllegalArgumentException) {
                    Log.e(TAG, "Rejected invalid store change", throwable)
                    _uiState.update { state -> state.copy(error = StoresError.INVALID_STORE) }
                } else {
                    Log.e(TAG, "Failed to update store registry", throwable)
                    _uiState.update { state -> state.copy(error = StoresError.WRITE_FAILED) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "StoresViewModel"
    }
}
