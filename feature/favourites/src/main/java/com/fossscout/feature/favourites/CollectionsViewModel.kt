package com.fossscout.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.CollectionsRepository
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.model.AppCollection
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CollectionsError { READ_FAILED, SAVE_FAILED, DELETE_FAILED }

data class CollectionEditor(
    val id: String? = null,
    val name: String = "",
    val description: String = "",
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && !isSaving
}

data class CollectionDeleteRequest(
    val id: String,
    val name: String,
)

data class CollectionPicker(
    val collectionId: String,
    val collectionName: String,
)

data class CollectionsUiState(
    val collections: List<AppCollection> = emptyList(),
    val favourites: List<Favourite> = emptyList(),
    val selectedCollectionId: String? = null,
    val isLoading: Boolean = true,
    val error: CollectionsError? = null,
    val editor: CollectionEditor? = null,
    val deleteRequest: CollectionDeleteRequest? = null,
    val picker: CollectionPicker? = null,
) {
    val selectedCollection: AppCollection?
        get() = collections.firstOrNull { it.id == selectedCollectionId }

    fun favouriteName(appKey: AppKey): String =
        favourites.firstOrNull { it.appKey == appKey }?.displayName ?: appKey.value

    val availableFavourites: List<Favourite>
        get() {
            val selectedKeys = selectedCollection?.itemKeys?.toSet().orEmpty()
            return favourites.filterNot { it.appKey in selectedKeys }
        }
}

class CollectionsViewModel(
    private val repository: CollectionsRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionsUiState())
    val uiState: StateFlow<CollectionsUiState> = _uiState.asStateFlow()

    init {
        collectCollections()
        collectFavourites()
    }

    fun retry() {
        collectCollections()
        collectFavourites()
    }

    fun openCollection(id: String) {
        _uiState.update { state ->
            state.copy(selectedCollectionId = id.takeIf { candidate ->
                state.collections.any { it.id == candidate }
            })
        }
    }

    fun closeCollection() {
        _uiState.update { state ->
            state.copy(
                selectedCollectionId = null,
                picker = null,
                deleteRequest = null,
            )
        }
    }

    fun startCreate() {
        _uiState.update { state ->
            state.copy(
                editor = CollectionEditor(),
                error = state.error.takeUnless { it == CollectionsError.SAVE_FAILED },
            )
        }
    }

    fun startEdit(collection: AppCollection) {
        _uiState.update { state ->
            state.copy(
                editor = CollectionEditor(
                    id = collection.id,
                    name = collection.name,
                    description = collection.description,
                ),
                error = state.error.takeUnless { it == CollectionsError.SAVE_FAILED },
            )
        }
    }

    fun updateEditorName(name: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(name = name, saveFailed = false),
            )
        }
    }

    fun updateEditorDescription(description: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(description = description, saveFailed = false),
            )
        }
    }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canSave) return
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(isSaving = true, saveFailed = false))
        }
        viewModelScope.launch {
            try {
                if (editor.id == null) {
                    val created = repository.create(
                        name = editor.name,
                        description = editor.description,
                    )
                    _uiState.update { state ->
                        state.copy(
                            editor = null,
                            selectedCollectionId = created.id,
                            error = state.error.takeUnless {
                                it == CollectionsError.SAVE_FAILED
                            },
                        )
                    }
                } else {
                    repository.update(
                        id = editor.id,
                        name = editor.name,
                        description = editor.description,
                    )
                    _uiState.update { state ->
                        state.copy(
                            editor = null,
                            error = state.error.takeUnless {
                                it == CollectionsError.SAVE_FAILED
                            },
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to save a collection", throwable)
                _uiState.update { state ->
                    state.copy(
                        editor = state.editor?.copy(isSaving = false, saveFailed = true),
                        error = CollectionsError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    fun dismissEditor() {
        _uiState.update { state ->
            state.copy(
                editor = null,
                error = state.error.takeUnless { it == CollectionsError.SAVE_FAILED },
            )
        }
    }

    fun requestDelete(collection: AppCollection) {
        _uiState.update { state ->
            state.copy(
                deleteRequest = CollectionDeleteRequest(
                    id = collection.id,
                    name = collection.name,
                ),
            )
        }
    }

    fun confirmDelete() {
        val request = _uiState.value.deleteRequest ?: return
        viewModelScope.launch {
            try {
                repository.delete(request.id)
                _uiState.update { state ->
                    state.copy(
                        deleteRequest = null,
                        selectedCollectionId = state.selectedCollectionId
                            ?.takeUnless { it == request.id },
                        error = state.error.takeUnless {
                            it == CollectionsError.DELETE_FAILED
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to delete collection ${request.id}", throwable)
                _uiState.update { state ->
                    state.copy(
                        deleteRequest = null,
                        error = CollectionsError.DELETE_FAILED,
                    )
                }
            }
        }
    }

    fun dismissDelete() {
        _uiState.update { state -> state.copy(deleteRequest = null) }
    }

    fun moveItem(collectionId: String, appKey: AppKey, offset: Int) {
        viewModelScope.launch {
            try {
                repository.move(collectionId, appKey, offset)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to reorder ${appKey.value}", throwable)
                _uiState.update { state -> state.copy(error = CollectionsError.SAVE_FAILED) }
            }
        }
    }

    fun removeItem(collectionId: String, appKey: AppKey) {
        viewModelScope.launch {
            try {
                repository.removeItem(collectionId, appKey)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to remove ${appKey.value} from a collection", throwable)
                _uiState.update { state -> state.copy(error = CollectionsError.SAVE_FAILED) }
            }
        }
    }

    fun openPicker() {
        val collection = _uiState.value.selectedCollection ?: return
        _uiState.update { state ->
            state.copy(
                picker = CollectionPicker(
                    collectionId = collection.id,
                    collectionName = collection.name,
                ),
            )
        }
    }

    fun dismissPicker() {
        _uiState.update { state -> state.copy(picker = null) }
    }

    fun addItem(collectionId: String, appKey: AppKey) {
        viewModelScope.launch {
            try {
                repository.addItem(collectionId, appKey)
                _uiState.update { state -> state.copy(picker = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to add ${appKey.value} to a collection", throwable)
                _uiState.update { state -> state.copy(error = CollectionsError.SAVE_FAILED) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    private fun collectCollections() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    error = state.error.takeUnless { it == CollectionsError.READ_FAILED },
                )
            }
            repository.collections
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read collections", throwable)
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = CollectionsError.READ_FAILED,
                        )
                    }
                }
                .collect { collections ->
                    _uiState.update { state ->
                        state.copy(
                            collections = collections,
                            selectedCollectionId = state.selectedCollectionId
                                ?.takeIf { id -> collections.any { it.id == id } },
                            isLoading = false,
                            error = state.error.takeUnless {
                                it == CollectionsError.READ_FAILED
                            },
                        )
                    }
                }
        }
    }

    private fun collectFavourites() {
        viewModelScope.launch {
            favouritesRepository.favourites
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read favourites for collections", throwable)
                    _uiState.update { state -> state.copy(favourites = emptyList()) }
                }
                .collect { favourites ->
                    _uiState.update { state -> state.copy(favourites = favourites) }
                }
        }
    }

    private companion object {
        const val TAG = "CollectionsViewModel"
    }
}
