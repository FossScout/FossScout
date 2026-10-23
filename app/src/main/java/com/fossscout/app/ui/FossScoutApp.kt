package com.fossscout.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fossscout.app.AppContainer
import com.fossscout.app.R
import com.fossscout.app.ui.about.AboutScreen
import com.fossscout.core.feature.FeatureId
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectRepository
import com.fossscout.feature.catalog.discover.DiscoverScreen
import com.fossscout.feature.catalog.discover.DiscoverViewModel
import com.fossscout.feature.favourites.FavouritesScreen
import com.fossscout.feature.favourites.FavouritesViewModel
import com.fossscout.feature.inventory.InventoryScreen
import com.fossscout.feature.inventory.InventoryViewModel
import com.fossscout.feature.settings.FeatureSettingsScreen
import com.fossscout.feature.settings.FeatureSettingsViewModel
import com.fossscout.feature.stores.StoreExportRequest
import com.fossscout.feature.stores.StoresScreen
import com.fossscout.feature.stores.StoresViewModel

private enum class AppDestination(
    val featureId: FeatureId?,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    DISCOVER(null, R.string.nav_discover, Icons.Filled.Search),
    INVENTORY(FeatureId.INVENTORY, R.string.nav_inventory, Icons.AutoMirrored.Filled.List),
    SAVED(FeatureId.FAVOURITES, R.string.nav_saved, Icons.Filled.Favorite),
    STORES(FeatureId.STORES, R.string.nav_stores, Icons.Filled.ShoppingCart),
    SETTINGS(FeatureId.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    ABOUT(null, R.string.nav_about, Icons.Filled.Info),
}

@Composable
fun FossScoutApp(
    container: AppContainer,
    initialInventoryQuery: String? = null,
    discoverViewModel: DiscoverViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DiscoverViewModel(
                    favouritesRepository = container.favouritesRepository,
                    projectRepository = ProjectRepository(),
                )
            }
        },
    ),
) {
    val featureSettingsViewModel: FeatureSettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FeatureSettingsViewModel(
                    repository = container.featureSettingsRepository,
                )
            }
        },
    )
    val featureSettingsState by featureSettingsViewModel.uiState.collectAsStateWithLifecycle()
    val featureStates = featureSettingsState.states
    val enabledFeatures = remember(featureStates) {
        featureStates.filter { it.enabled }.map { it.descriptor.id }.toSet()
    }
    val favouritesEnabled = FeatureId.FAVOURITES in enabledFeatures
    val destinations = remember(enabledFeatures) {
        AppDestination.entries.filter { destination ->
            destination.featureId == null || destination.featureId in enabledFeatures
        }
    }

    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.DISCOVER.name) }
    val destination = destinations.firstOrNull { it.name == selectedDestination }
        ?: destinations.first()
    val inventoryEnabled = FeatureId.INVENTORY in enabledFeatures
    var handledSharedQuery by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialInventoryQuery, inventoryEnabled) {
        val shared = initialInventoryQuery?.trim().orEmpty()
        if (
            inventoryEnabled &&
            shared.isNotEmpty() &&
            handledSharedQuery != initialInventoryQuery
        ) {
            selectedDestination = AppDestination.INVENTORY.name
            handledSharedQuery = initialInventoryQuery
        }
    }

    val state by discoverViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val openProject: (Project) -> Unit = { project -> context.openWebsite(project.website) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { selectedDestination = item.name },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (destination) {
            AppDestination.DISCOVER -> DiscoverScreen(
                state = state,
                savedOnly = false,
                onQueryChange = discoverViewModel::onQueryChange,
                onCategorySelected = discoverViewModel::onCategorySelected,
                onToggleSaved = discoverViewModel::onToggleSaved,
                onClearFilters = discoverViewModel::clearFilters,
                onOpenProject = openProject,
                modifier = screenModifier,
                showSaveAction = favouritesEnabled,
                onRetrySavedState = discoverViewModel::retrySavedState,
            )

            AppDestination.SAVED -> {
                val favouritesViewModel: FavouritesViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            FavouritesViewModel(
                                repository = container.favouritesRepository,
                                storeRegistry = container.storeRegistryRepository,
                                installedStateProvider = container.installedStateProvider,
                            )
                        }
                    },
                )
                val favouritesState by favouritesViewModel.uiState.collectAsStateWithLifecycle()

                FavouritesScreen(
                    state = favouritesState,
                    onRemove = favouritesViewModel::remove,
                    onEditNote = favouritesViewModel::openNote,
                    onNoteDraftChange = favouritesViewModel::updateNoteDraft,
                    onTagDraftChange = favouritesViewModel::updateTagDraft,
                    onAddTag = favouritesViewModel::addTag,
                    onRemoveTag = favouritesViewModel::removeTag,
                    onPreferredStoreSelected = favouritesViewModel::selectPreferredStore,
                    onSaveNote = favouritesViewModel::saveNote,
                    onDismissEditor = favouritesViewModel::dismissEditor,
                    onQueryChange = favouritesViewModel::onQueryChange,
                    onTagSelected = favouritesViewModel::selectTag,
                    onClearFilters = favouritesViewModel::clearFilters,
                    onRefreshInstalled = favouritesViewModel::refreshInstalledStates,
                    onRetry = favouritesViewModel::retry,
                    onDismissError = favouritesViewModel::dismissError,
                    modifier = screenModifier,
                )
            }

            AppDestination.INVENTORY -> {
                val inventoryViewModel: InventoryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            InventoryViewModel(
                                repository = container.inventoryRepository,
                                initialQuery = initialInventoryQuery,
                            )
                        }
                    },
                )
                LaunchedEffect(initialInventoryQuery) {
                    val shared = initialInventoryQuery?.trim().orEmpty()
                    if (
                        shared.isNotEmpty() &&
                        inventoryViewModel.uiState.value.query != shared
                    ) {
                        inventoryViewModel.submitSharedInput(shared)
                    }
                }
                val inventoryState by
                    inventoryViewModel.uiState.collectAsStateWithLifecycle()

                InventoryScreen(
                    state = inventoryState,
                    onQueryChange = inventoryViewModel::onQueryChange,
                    onSearch = inventoryViewModel::search,
                    onCheck = inventoryViewModel::checkInput,
                    onSelectCandidate = inventoryViewModel::selectCandidate,
                    onFullScan = inventoryViewModel::startFullScan,
                    onCancelScan = inventoryViewModel::cancelScan,
                    onIncludeSystemChange = inventoryViewModel::setIncludeSystemApps,
                    onIncludeDisabledChange = inventoryViewModel::setIncludeDisabledApps,
                    onIncludeWorkProfileChange =
                        inventoryViewModel::setIncludeWorkProfileApps,
                    onIncludeHiddenChange = inventoryViewModel::setIncludeHiddenApps,
                    onHide = inventoryViewModel::hide,
                    onUnhide = inventoryViewModel::unhide,
                    onDismissError = inventoryViewModel::dismissError,
                    modifier = screenModifier,
                )
            }

            AppDestination.ABOUT -> AboutScreen(modifier = screenModifier)

            AppDestination.STORES -> {
                val storesViewModel: StoresViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            StoresViewModel(
                                repository = container.storeRegistryRepository,
                                indexSyncRepository = container.indexSyncRepository,
                            )
                        }
                    },
                )
                val storesState by storesViewModel.uiState.collectAsStateWithLifecycle()

                StoresScreen(
                    state = storesState,
                    onAdd = storesViewModel::startAdd,
                    onEdit = storesViewModel::startEdit,
                    onEditorChange = storesViewModel::updateEditor,
                    onSaveEditor = storesViewModel::saveEditor,
                    onDismissEditor = storesViewModel::dismissEditor,
                    onToggle = storesViewModel::setEnabled,
                    onMoveUp = storesViewModel::moveUp,
                    onMoveDown = storesViewModel::moveDown,
                    onRequestDelete = storesViewModel::requestDelete,
                    onConfirmDelete = storesViewModel::confirmDelete,
                    onDismissDelete = storesViewModel::dismissDelete,
                    onExport = storesViewModel::export,
                    onShareExport = context::shareStoreDescriptor,
                    onDismissExport = storesViewModel::dismissExport,
                    onOpenHomepage = context::openWebsite,
                    onSync = storesViewModel::sync,
                    onCancelSync = storesViewModel::cancelSync,
                    onCleanupStorage = storesViewModel::cleanupStorage,
                    onRetry = storesViewModel::retry,
                    onDismissError = storesViewModel::dismissError,
                    modifier = screenModifier,
                )
            }

            AppDestination.SETTINGS -> FeatureSettingsScreen(
                uiState = featureSettingsState,
                onToggle = featureSettingsViewModel::setEnabled,
                onSettingValueChange = featureSettingsViewModel::setSettingValue,
                onResetFeature = featureSettingsViewModel::resetFeature,
                onResetAll = featureSettingsViewModel::resetAll,
                onToggleAdvanced = featureSettingsViewModel::toggleAdvanced,
                onDismissError = featureSettingsViewModel::dismissError,
                modifier = screenModifier,
            )
        }
    }
}

private fun Context.openWebsite(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun Context.shareStoreDescriptor(request: StoreExportRequest) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_store_subject, request.name))
        putExtra(Intent.EXTRA_TEXT, request.json)
    }
    runCatching {
        startActivity(
            Intent.createChooser(send, getString(R.string.share_store_descriptor)),
        )
    }
}
