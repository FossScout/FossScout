package com.fossscout.feature.favourites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fossscout.core.data.FavouritesRepository
import com.fossscout.core.data.StoreRegistryRepository
import com.fossscout.core.inventory.InstalledStateProvider
import com.fossscout.core.model.AppKey
import com.fossscout.core.model.Favourite
import com.fossscout.core.model.InstalledState
import com.fossscout.core.model.MAX_TAGS
import com.fossscout.core.model.StoreId
import com.fossscout.core.model.favouriteTags
import com.fossscout.core.model.filterFavourites
import com.fossscout.core.model.normalizeTag
import com.fossscout.core.model.normalizeTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FavouritesError { READ_FAILED, SAVE_FAILED, REMOVE_FAILED }

enum class FavouriteTagError { TOO_LONG, TOO_MANY }

data class FavouriteStoreOption(
    val id: StoreId,
    val name: String,
)

data class FavouriteEditor(
    val appKey: AppKey,
    val displayName: String,
    val draft: String,
    val tags: List<String> = emptyList(),
    val tagDraft: String = "",
    val tagError: FavouriteTagError? = null,
    val preferredStoreId: StoreId? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

data class FavouritesUiState(
    val favourites: List<Favourite> = emptyList(),
    val query: String = "",
    val selectedTag: String? = null,
    val storeOptions: List<FavouriteStoreOption> = emptyList(),
    val installedStates: Map<AppKey, InstalledState> = emptyMap(),
    val isInstalledStateLoading: Boolean = false,
    val isLoading: Boolean = true,
    val error: FavouritesError? = null,
    val editor: FavouriteEditor? = null,
) {
    val tags: List<String>
        get() = favourites.favouriteTags()

    val visibleFavourites: List<Favourite>
        get() = favourites.filterFavourites(query = query, tag = selectedTag)

    val hasActiveFilters: Boolean
        get() = query.isNotBlank() || selectedTag != null

    fun storeName(id: StoreId?): String? =
        id?.let { storeId -> storeOptions.firstOrNull { it.id == storeId }?.name }
}

class FavouritesViewModel(
    private val repository: FavouritesRepository,
    private val storeRegistry: StoreRegistryRepository? = null,
    private val installedStateProvider: InstalledStateProvider? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FavouritesUiState())
    val uiState: StateFlow<FavouritesUiState> = _uiState.asStateFlow()

    private var readJob: Job? = null
    private var storeJob: Job? = null
    private var installedJob: Job? = null

    init {
        collectStoreOptions()
        collectFavourites()
    }

    fun retry() {
        collectFavourites()
        collectStoreOptions()
    }

    fun onQueryChange(query: String) {
        _uiState.update { state -> state.copy(query = query) }
    }

    fun selectTag(tag: String?) {
        _uiState.update { state -> state.copy(selectedTag = tag) }
    }

    fun clearFilters() {
        _uiState.update { state -> state.copy(query = "", selectedTag = null) }
    }

    fun openNote(favourite: Favourite) {
        _uiState.update { state ->
            state.copy(
                editor = FavouriteEditor(
                    appKey = favourite.appKey,
                    displayName = favourite.displayName,
                    draft = favourite.note,
                    tags = favourite.tags.toList(),
                    preferredStoreId = favourite.preferredStoreId,
                ),
                error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
            )
        }
    }

    fun updateNoteDraft(draft: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(draft = draft, saveFailed = false))
        }
    }

    fun updateTagDraft(draft: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(tagDraft = draft, tagError = null))
        }
    }

    fun addTag() {
        val editor = _uiState.value.editor ?: return
        val tag = try {
            normalizeTag(editor.tagDraft)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Refusing an oversized tag", exception)
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(tagError = FavouriteTagError.TOO_LONG))
            }
            return
        }
        if (tag == null) {
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(tagDraft = "", tagError = null))
            }
            return
        }
        if (editor.tags.any { it.equals(tag, ignoreCase = true) }) {
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(tagDraft = "", tagError = null))
            }
            return
        }
        if (editor.tags.size >= MAX_TAGS) {
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(tagError = FavouriteTagError.TOO_MANY))
            }
            return
        }
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(
                    tags = editor.tags + tag,
                    tagDraft = "",
                    tagError = null,
                ),
            )
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor?.copy(
                    tags = state.editor?.tags.orEmpty().filterNot {
                        it.equals(tag, ignoreCase = true)
                    },
                    tagError = null,
                ),
            )
        }
    }

    fun selectPreferredStore(storeId: StoreId?) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(preferredStoreId = storeId))
        }
    }

    fun saveNote() {
        val editor = _uiState.value.editor ?: return
        if (editor.isSaving) return

        val normalized = try {
            normalizeNote(editor.draft)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Refusing to save an invalid note", exception)
            return
        }
        val tags = try {
            normalizeTags(editor.tags)
        } catch (exception: IllegalArgumentException) {
            Log.e(TAG, "Refusing to save invalid tags", exception)
            _uiState.update { state ->
                state.copy(editor = state.editor?.copy(tagError = FavouriteTagError.TOO_MANY))
            }
            return
        }

        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(isSaving = true, saveFailed = false))
        }
        viewModelScope.launch {
            try {
                repository.updateDetails(
                    appKey = editor.appKey,
                    note = normalized,
                    tags = tags,
                    preferredStoreId = editor.preferredStoreId,
                )
                _uiState.update { state ->
                    state.copy(
                        editor = null,
                        error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to save note for ${editor.appKey.value}", throwable)
                _uiState.update { state ->
                    state.copy(
                        editor = state.editor?.copy(isSaving = false, saveFailed = true),
                        error = FavouritesError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    fun dismissEditor() {
        _uiState.update { state ->
            state.copy(
                editor = null,
                error = state.error.takeUnless { it == FavouritesError.SAVE_FAILED },
            )
        }
    }

    fun remove(appKey: AppKey) {
        viewModelScope.launch {
            try {
                repository.remove(appKey)
                _uiState.update { state ->
                    state.copy(
                        error = state.error.takeUnless { it == FavouritesError.REMOVE_FAILED },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to remove ${appKey.value}", throwable)
                _uiState.update { state -> state.copy(error = FavouritesError.REMOVE_FAILED) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { state -> state.copy(error = null) }
    }

    /** Recomputes advisory badges without touching the stored favourites. */
    fun refreshInstalledStates() {
        refreshInstalledStates(_uiState.value.favourites)
    }

    private fun collectFavourites() {
        readJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                error = state.error.takeUnless { it == FavouritesError.READ_FAILED },
            )
        }
        readJob = viewModelScope.launch {
            repository.favourites
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read favourites", throwable)
                    installedJob?.cancel()
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            installedStates = emptyMap(),
                            isInstalledStateLoading = false,
                            error = FavouritesError.READ_FAILED,
                        )
                    }
                }
                .collect { favourites ->
                    _uiState.update { state ->
                        val tags = favourites.favouriteTags()
                        state.copy(
                            favourites = favourites,
                            selectedTag = state.selectedTag?.takeIf(tags::contains),
                            isLoading = false,
                            error = state.error.takeUnless { it == FavouritesError.READ_FAILED },
                        )
                    }
                    refreshInstalledStates(favourites)
                }
        }
    }

    private fun collectStoreOptions() {
        val registry = storeRegistry ?: return
        storeJob?.cancel()
        storeJob = viewModelScope.launch {
            registry.entries
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Log.e(TAG, "Failed to read the store registry", throwable)
                    _uiState.update { it.copy(storeOptions = emptyList()) }
                }
                .collect { entries ->
                    _uiState.update { state ->
                        state.copy(
                            storeOptions = entries.map { entry ->
                                FavouriteStoreOption(
                                    id = entry.descriptor.id,
                                    name = entry.descriptor.name,
                                )
                            },
                        )
                    }
                }
        }
    }

    private fun refreshInstalledStates(favourites: List<Favourite>) {
        val provider = installedStateProvider ?: return
        installedJob?.cancel()
        if (favourites.isEmpty()) {
            _uiState.update { state ->
                state.copy(installedStates = emptyMap(), isInstalledStateLoading = false)
            }
            return
        }
        _uiState.update { state -> state.copy(isInstalledStateLoading = true) }
        installedJob = viewModelScope.launch {
            val states = coroutineScope {
                favourites.map { favourite ->
                    async { favourite.appKey to stateFor(provider, favourite.appKey) }
                }.awaitAll()
            }.toMap()
            _uiState.update { state ->
                state.copy(installedStates = states, isInstalledStateLoading = false)
            }
        }
    }

    private suspend fun stateFor(
        provider: InstalledStateProvider,
        appKey: AppKey,
    ): InstalledState = try {
        provider.stateFor(appKey.value)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Log.e(TAG, "Failed to read installed state for ${appKey.value}", throwable)
        InstalledState.UNKNOWN
    }

    private companion object {
        const val TAG = "FavouritesViewModel"
    }
}
