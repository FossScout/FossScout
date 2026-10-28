package com.fossscout.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fossscout.app.AppContainer
import com.fossscout.app.R
import com.fossscout.app.ui.about.AboutScreen
import com.fossscout.core.contribution.ContributionContext
import com.fossscout.core.contribution.ReportStoreInput
import com.fossscout.core.contribution.forMissingStore
import com.fossscout.core.contribution.forWrongMatch
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.handoff.AppHandoff
import com.fossscout.core.matching.MatchDecision
import com.fossscout.feature.backup.BackupExportRequest
import com.fossscout.feature.backup.BackupScreen
import com.fossscout.feature.backup.BackupViewModel
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectRepository
import com.fossscout.feature.catalog.discover.DiscoverScreen
import com.fossscout.feature.catalog.discover.DiscoverViewModel
import com.fossscout.feature.companion.CompanionScreen
import com.fossscout.feature.companion.CompanionViewModel
import com.fossscout.feature.contribution.ContributionScreen
import com.fossscout.feature.contribution.ContributionViewModel
import com.fossscout.feature.favourites.CollectionsScreen
import com.fossscout.feature.favourites.CollectionsViewModel
import com.fossscout.feature.favourites.FavouritesScreen
import com.fossscout.feature.favourites.FavouritesViewModel
import com.fossscout.feature.favourites.ImportCompleteDialog
import com.fossscout.feature.favourites.ImportPreviewDialog
import com.fossscout.feature.favourites.ShareExportDialog
import com.fossscout.feature.favourites.ShareExportRequest
import com.fossscout.feature.favourites.SharingErrorDialog
import com.fossscout.feature.favourites.SharingViewModel
import com.fossscout.feature.inventory.InventoryScreen
import com.fossscout.feature.inventory.InventoryViewModel
import com.fossscout.feature.matching.MatchScreen
import com.fossscout.feature.matching.MatchingViewModel
import com.fossscout.feature.settings.FeatureSettingsScreen
import com.fossscout.feature.settings.FeatureSettingsViewModel
import com.fossscout.feature.stores.StoreEditor
import com.fossscout.feature.stores.StoreExportRequest
import com.fossscout.feature.stores.StoresScreen
import com.fossscout.feature.stores.StoresViewModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val sharingEnabled = FeatureId.SHARING in enabledFeatures
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val collectionsViewModel: CollectionsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CollectionsViewModel(
                    repository = container.collectionsRepository,
                    favouritesRepository = container.favouritesRepository,
                )
            }
        },
    )
    val collectionsState by collectionsViewModel.uiState.collectAsStateWithLifecycle()

    val sharingViewModel: SharingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SharingViewModel(
                    favouritesRepository = container.favouritesRepository,
                    collectionsRepository = container.collectionsRepository,
                    importService = container.shareImportService,
                    storeRegistry = container.storeRegistryRepository,
                    exportFactory = container.shareExportFactory,
                )
            }
        },
    )
    val sharingState by sharingViewModel.uiState.collectAsStateWithLifecycle()

    val backupViewModel: BackupViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BackupViewModel(service = container.backupService)
            }
        },
    )
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

    val contributionViewModel: ContributionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ContributionViewModel(
                    environment = container.contributionEnvironment,
                    builder = container.contributionReportBuilder,
                )
            }
        },
    )
    val contributionState by contributionViewModel.uiState.collectAsStateWithLifecycle()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { reader -> reader.readText() }
                    }.getOrNull()
                }
                if (text.isNullOrBlank()) {
                    sharingViewModel.reportReadFailure()
                } else {
                    sharingViewModel.startImport(text)
                }
            }
        }
    }
    val pickImportFile: () -> Unit = {
        importLauncher.launch(
            arrayOf("application/json", "text/plain", "application/octet-stream"),
        )
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { reader -> reader.readText() }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }
                }
                if (text.isNullOrBlank()) {
                    backupViewModel.reportReadFailure()
                } else {
                    backupViewModel.startImport(text, uri.lastPathSegment)
                }
            }
        }
    }
    val pickBackupFile: () -> Unit = {
        backupImportLauncher.launch(
            arrayOf("application/json", "text/plain", "application/octet-stream"),
        )
    }

    LaunchedEffect(sharingState.exportRequest) {
        val request = sharingState.exportRequest ?: return@LaunchedEffect
        context.shareExport(request)
        sharingViewModel.consumeExportRequest()
    }

    LaunchedEffect(backupState.exportRequest) {
        val request = backupState.exportRequest ?: return@LaunchedEffect
        context.shareBackup(request)
        backupViewModel.consumeExportRequest()
    }

    LaunchedEffect(contributionState.clipboardRequest) {
        val request = contributionState.clipboardRequest ?: return@LaunchedEffect
        context.copyContributionText(request.label, request.text)
        contributionViewModel.consumeClipboardRequest()
    }

    LaunchedEffect(contributionState.shareRequest) {
        val request = contributionState.shareRequest ?: return@LaunchedEffect
        context.shareContribution(request.subject, request.text)
        contributionViewModel.consumeShareRequest()
    }

    LaunchedEffect(contributionState.issueRequest) {
        val request = contributionState.issueRequest ?: return@LaunchedEffect
        context.openContributionIssue(request.url)
        contributionViewModel.consumeIssueRequest()
    }

    val destinations = remember(enabledFeatures) {
        AppDestination.entries.filter { destination ->
            destination.featureId == null || destination.featureId in enabledFeatures
        }
    }

    var selectedDestination by rememberSaveable { mutableStateOf(AppDestination.DISCOVER.name) }
    var showCollections by rememberSaveable { mutableStateOf(false) }
    var showBackup by rememberSaveable { mutableStateOf(false) }
    var showContribution by rememberSaveable { mutableStateOf(false) }
    val destination = destinations.firstOrNull { it.name == selectedDestination }
        ?: destinations.first()
    val inventoryEnabled = FeatureId.INVENTORY in enabledFeatures
    val backupEnabled = FeatureId.BACKUP in enabledFeatures
    val contributionEnabled = FeatureId.CONTRIBUTION in enabledFeatures
    LaunchedEffect(backupEnabled) {
        if (!backupEnabled) showBackup = false
    }
    LaunchedEffect(contributionEnabled) {
        if (!contributionEnabled) showContribution = false
    }
    LaunchedEffect(showBackup) {
        if (showBackup) backupViewModel.refreshExport()
    }
    val openContribution: (ContributionContext) -> Unit = { reportContext ->
        contributionViewModel.reset(reportContext)
        showContribution = true
    }
    val closeContribution: () -> Unit = {
        if (contributionState.preview != null) {
            contributionViewModel.dismissPreview()
        } else {
            showContribution = false
        }
    }
    BackHandler(enabled = showContribution) { closeContribution() }
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

    val openProject: (Project) -> Unit = { project -> context.openWebsite(project.website) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { item ->
                    val label = stringResource(item.labelRes)
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = {
                            showContribution = false
                            selectedDestination = item.name
                        },
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

        if (showContribution) {
            ContributionScreen(
                state = contributionState,
                onBack = closeContribution,
                onKindSelected = contributionViewModel::selectKind,
                onDraftChange = contributionViewModel::updateDraft,
                onPreview = contributionViewModel::previewReport,
                onDismissPreview = contributionViewModel::dismissPreview,
                onCopy = contributionViewModel::requestCopy,
                onShare = contributionViewModel::requestShare,
                onOpenIssue = contributionViewModel::requestOpenIssue,
                onCopyJson = contributionViewModel::requestCopyJson,
                onShareJson = contributionViewModel::requestShareJson,
                onDismissError = contributionViewModel::dismissError,
                modifier = screenModifier,
            )
            return@Scaffold
        }

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

                if (showCollections) {
                    CollectionsScreen(
                        state = collectionsState,
                        showSharing = sharingEnabled,
                        onBack = {
                            collectionsViewModel.closeCollection()
                            showCollections = false
                        },
                        onOpenCollection = collectionsViewModel::openCollection,
                        onCloseCollection = collectionsViewModel::closeCollection,
                        onStartCreate = collectionsViewModel::startCreate,
                        onStartEdit = collectionsViewModel::startEdit,
                        onEditorNameChange = collectionsViewModel::updateEditorName,
                        onEditorDescriptionChange =
                            collectionsViewModel::updateEditorDescription,
                        onSaveEditor = collectionsViewModel::saveEditor,
                        onDismissEditor = collectionsViewModel::dismissEditor,
                        onRequestDelete = collectionsViewModel::requestDelete,
                        onConfirmDelete = collectionsViewModel::confirmDelete,
                        onDismissDelete = collectionsViewModel::dismissDelete,
                        onMoveItem = collectionsViewModel::moveItem,
                        onRemoveItem = collectionsViewModel::removeItem,
                        onOpenPicker = collectionsViewModel::openPicker,
                        onDismissPicker = collectionsViewModel::dismissPicker,
                        onAddItem = collectionsViewModel::addItem,
                        onShareCollection = sharingViewModel::shareCollection,
                        onShareAll = sharingViewModel::shareAllFavourites,
                        onImport = pickImportFile,
                        onRetry = collectionsViewModel::retry,
                        onDismissError = collectionsViewModel::dismissError,
                        modifier = screenModifier,
                    )
                } else {
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
                        showSharing = sharingEnabled,
                        onOpenCollections = { showCollections = true },
                        onShareAll = sharingViewModel::shareAllFavourites,
                        onImport = pickImportFile,
                        onShareFavourite = sharingViewModel::shareFavourite,
                        modifier = screenModifier,
                    )
                }
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

                var showMatch by remember { mutableStateOf(false) }
                var showCompanion by remember { mutableStateOf(false) }
                val alternativesEnabled = FeatureId.ALTERNATIVES in enabledFeatures
                val replacementGuidanceEnabled =
                    FeatureId.REPLACEMENT_GUIDANCE in enabledFeatures
                val companionHandoffEnabled = FeatureId.COMPANION_HANDOFF in enabledFeatures
                val companionEnabled =
                    replacementGuidanceEnabled || companionHandoffEnabled
                val matchingViewModel: MatchingViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            MatchingViewModel(
                                repository = container.matchingRepository,
                                appSource = container.installedAppSource,
                                includeAlternatives = alternativesEnabled,
                                setCorrection = { appKey, target, note ->
                                    if (target == null) {
                                        container.matchOverridesRepository.setNoMatch(appKey, note)
                                    } else {
                                        container.matchOverridesRepository.setTarget(
                                            appKey,
                                            target,
                                            note,
                                        )
                                    }
                                },
                                removeCorrection = container.matchOverridesRepository::remove,
                            )
                        }
                    },
                )
                LaunchedEffect(alternativesEnabled) {
                    matchingViewModel.setIncludeAlternatives(alternativesEnabled)
                }
                val matchingState by
                    matchingViewModel.uiState.collectAsStateWithLifecycle()

                val companionViewModel: CompanionViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            CompanionViewModel(
                                appSource = container.installedAppSource,
                                stores = container.storeRegistryRepository.entries,
                                detector = container.companionStoreDetector,
                                preferences = container.handoffPreferencesRepository,
                                showReplacementGuidance = replacementGuidanceEnabled,
                                showHandoff = companionHandoffEnabled,
                            )
                        }
                    },
                )
                LaunchedEffect(replacementGuidanceEnabled, companionHandoffEnabled) {
                    companionViewModel.setFeatureVisibility(
                        showReplacementGuidance = replacementGuidanceEnabled,
                        showHandoff = companionHandoffEnabled,
                    )
                }
                val companionState by
                    companionViewModel.uiState.collectAsStateWithLifecycle()

                if (showMatch) {
                    if (showCompanion) {
                        CompanionScreen(
                            state = companionState,
                            onBack = { showCompanion = false },
                            onSelectClient = companionViewModel::selectClient,
                            onConfirmReplace = companionViewModel::setReplaceConfirmed,
                            onToggleQr = companionViewModel::toggleQr,
                            onOpenStoreApp = context::openCompanionStore,
                            onOpenRepository = context::openCompanionUri,
                            onOpenBrowser = { url -> context.openCompanionUri(url, null) },
                            onCopy = context::copyCompanionText,
                            onDismissError = companionViewModel::dismissError,
                            modifier = screenModifier,
                        )
                    } else {
                        MatchScreen(
                            state = matchingState,
                            onQueryChange = matchingViewModel::onQueryChange,
                            onCheck = matchingViewModel::checkInput,
                            onSelectCandidate = matchingViewModel::selectCandidate,
                            onOpenSource = { url -> context.openWebsite(url) },
                            onHideAlternative = matchingViewModel::hideAlternative,
                            onUndoHide = matchingViewModel::undoHide,
                            onDismissError = matchingViewModel::dismissError,
                            onBack = {
                                showMatch = false
                                showCompanion = false
                            },
                            onOpenCompanion = if (companionEnabled) {
                                { decision: MatchDecision ->
                                    companionViewModel.open(decision)
                                    showCompanion = true
                                }
                            } else {
                                null
                            },
                            onReportWrongMatch = if (contributionEnabled) {
                                { decision: MatchDecision ->
                                    openContribution(
                                        ContributionContext.forWrongMatch(decision),
                                    )
                                }
                            } else {
                                null
                            },
                            onStartCorrection = matchingViewModel::startCorrection,
                            onUndoCorrection = matchingViewModel::undoCorrection,
                            onCorrectionQueryChange = matchingViewModel::onCorrectionQueryChange,
                            onSearchCorrectionTargets =
                                matchingViewModel::searchCorrectionTargets,
                            onSelectCorrectionTarget =
                                matchingViewModel::selectCorrectionTarget,
                            onCorrectionNoteChange = matchingViewModel::onCorrectionNoteChange,
                            onSaveCorrection = matchingViewModel::saveCorrection,
                            onSaveNoMatch = matchingViewModel::saveNoMatch,
                            onDismissCorrection = matchingViewModel::dismissCorrection,
                            modifier = screenModifier,
                            showCoverage = FeatureId.COVERAGE in enabledFeatures,
                            showAlternatives = alternativesEnabled,
                        )
                    }
                } else {
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
                        onCheckMatch = { entry ->
                            matchingViewModel.matchEntry(entry.identity, entry.app)
                            showCompanion = false
                            showMatch = true
                        },
                        onDismissError = inventoryViewModel::dismissError,
                        modifier = screenModifier,
                        showMatchAction = FeatureId.MATCHING in enabledFeatures,
                    )
                }
            }

            AppDestination.ABOUT -> AboutScreen(
                modifier = screenModifier,
                onOpenDonation = context::openWebsite,
            )

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
                    onOpenSecurityDetails = storesViewModel::openSecurityDetails,
                    onDismissSecurityDetails = storesViewModel::dismissSecurityDetails,
                    onApproveFingerprint = storesViewModel::approveFingerprintRotation,
                    onCleanupStorage = storesViewModel::cleanupStorage,
                    onRetry = storesViewModel::retry,
                    onDismissError = storesViewModel::dismissError,
                    modifier = screenModifier,
                    onReportProblem = if (contributionEnabled) {
                        { editor: StoreEditor? ->
                            openContribution(
                                ContributionContext.forMissingStore(
                                    store = editor?.toReportStoreInput(),
                                ),
                            )
                        }
                    } else {
                        null
                    },
                )
            }

            AppDestination.SETTINGS -> {
                if (showBackup && backupEnabled) {
                    BackupScreen(
                        state = backupState,
                        onBack = {
                            if (!backupState.isApplying) {
                                backupViewModel.dismissImport()
                                showBackup = false
                            }
                        },
                        onToggleEncryption = backupViewModel::setEncryption,
                        onPassphraseChange = backupViewModel::setPassphrase,
                        onConfirmPassphraseChange =
                            backupViewModel::setConfirmPassphrase,
                        onExport = backupViewModel::exportBackup,
                        onChooseFile = pickBackupFile,
                        onImportPassphraseChange =
                            backupViewModel::setImportPassphrase,
                        onUnlockImport = backupViewModel::unlockImport,
                        onModeChange = backupViewModel::setImportMode,
                        onRestore = backupViewModel::applyImport,
                        onDismissImport = backupViewModel::dismissImport,
                        onDismissError = backupViewModel::dismissError,
                        onAcknowledgeRestore =
                            backupViewModel::acknowledgeRestoreCompleted,
                        onRetryExport = backupViewModel::refreshExport,
                        modifier = screenModifier,
                    )
                } else {
                    FeatureSettingsScreen(
                        uiState = featureSettingsState,
                        onToggle = featureSettingsViewModel::setEnabled,
                        onSettingValueChange = featureSettingsViewModel::setSettingValue,
                        onResetFeature = featureSettingsViewModel::resetFeature,
                        onResetAll = featureSettingsViewModel::resetAll,
                        onToggleAdvanced = featureSettingsViewModel::toggleAdvanced,
                        onDismissError = featureSettingsViewModel::dismissError,
                        onOpenBackup = if (backupEnabled) {
                            { showBackup = true }
                        } else {
                            null
                        },
                        onOpenContribution = if (contributionEnabled) {
                            { openContribution(ContributionContext.forMissingStore()) }
                        } else {
                            null
                        },
                        modifier = screenModifier,
                    )
                }
            }
        }
    }

    sharingState.shareDraft?.let { draft ->
        ShareExportDialog(
            draft = draft,
            onIncludeNotesChange = sharingViewModel::setShareIncludeNotes,
            onShareJson = sharingViewModel::exportJson,
            onShareText = sharingViewModel::exportText,
            onDismiss = sharingViewModel::dismissShare,
        )
    }

    sharingState.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            keepImportedNotes = sharingState.keepImportedNotes,
            isApplying = sharingState.isApplyingImport,
            onModeChange = sharingViewModel::setImportMode,
            onKeepNotesChange = sharingViewModel::setKeepImportedNotes,
            onApply = sharingViewModel::applyImport,
            onDismiss = sharingViewModel::dismissImport,
        )
    }

    sharingState.error?.let { error ->
        SharingErrorDialog(
            error = error,
            onDismiss = sharingViewModel::dismissError,
        )
    }

    if (sharingState.importCompleted) {
        ImportCompleteDialog(onDismiss = sharingViewModel::acknowledgeImportCompleted)
    }
}

private fun StoreEditor.toReportStoreInput(): ReportStoreInput = ReportStoreInput(
    name = name,
    homepage = homepage,
    adapterType = adapterType,
    indexUrl = indexUrl,
    apiUrl = apiUrl,
    repositoryUrl = repositoryUrl,
    hasPinnedFingerprint = fingerprint.isNotBlank(),
)

private fun Context.copyContributionText(label: String, text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(this, R.string.contribution_copy_failed, Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, R.string.contribution_copied, Toast.LENGTH_SHORT).show()
}

private fun Context.shareContribution(subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        startActivity(
            Intent.createChooser(send, getString(R.string.contribution_share_chooser)),
        )
    }
}

private fun Context.openContributionIssue(url: String) {
    val opened = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.isSuccess
    if (!opened) {
        Toast.makeText(this, R.string.contribution_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openWebsite(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun Context.openCompanionStore(handoff: AppHandoff) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(handoff.marketUri))
        .setPackage(handoff.client.packageName)
    if (!runCatching { startActivity(intent) }.isSuccess) {
        openCompanionUri(handoff.browserUri, null)
    }
}

private fun Context.openCompanionUri(uri: String, packageName: String?) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
        if (packageName != null) setPackage(packageName)
    }
    if (!runCatching { startActivity(intent) }.isSuccess) {
        Toast.makeText(this, R.string.companion_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun Context.copyCompanionText(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(this, R.string.companion_open_failed, Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(
        ClipData.newPlainText(getString(R.string.companion_repository_link_label), text),
    )
    Toast.makeText(this, R.string.companion_copied, Toast.LENGTH_SHORT).show()
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

/**
 * Writes the export into the app cache and shares a FileProvider URI. The
 * receiving app gets read access only to this one file; no storage permission
 * or broad URI grant is requested.
 */
private fun Context.shareExport(request: ShareExportRequest) {
    shareExportContent(
        subject = request.subject,
        fileName = request.fileName,
        mimeType = request.mimeType,
        content = request.content,
    )
}

private fun Context.shareBackup(request: BackupExportRequest) {
    shareExportContent(
        subject = request.subject,
        fileName = request.fileName,
        mimeType = request.mimeType,
        content = request.content,
    )
}

private fun Context.shareExportContent(
    subject: String,
    fileName: String,
    mimeType: String,
    content: String,
) {
    val exportDir = File(cacheDir, "exports").apply { mkdirs() }
    val file = File(exportDir, fileName)
    file.writeText(content)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_STREAM, uri)
        if (mimeType.startsWith("text/")) {
            putExtra(Intent.EXTRA_TEXT, content)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        startActivity(
            Intent.createChooser(send, getString(R.string.share_export_chooser)),
        )
    }
}
