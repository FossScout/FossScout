package com.fossscout.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fossscout.app.AppContainer
import com.fossscout.app.profile.ProfileFeatures
import com.fossscout.app.ui.about.AboutScreen
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureRegistry
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.ProjectRepository
import com.fossscout.feature.catalog.discover.DiscoverScreen
import com.fossscout.feature.catalog.discover.DiscoverViewModel
import com.fossscout.feature.favourites.FavouritesScreen
import com.fossscout.feature.favourites.FavouritesViewModel

private enum class AppDestination(
    val featureId: FeatureId?,
    val label: String,
    val icon: ImageVector,
) {
    DISCOVER(null, "Discover", Icons.Filled.Search),
    SAVED(FeatureId.FAVOURITES, "Saved", Icons.Filled.Favorite),
    ABOUT(null, "About", Icons.Filled.Info),
}

@Composable
fun FossScoutApp(
    container: AppContainer,
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
    val registry = remember {
        FeatureRegistry(
            profile = ProfileFeatures.profile,
            descriptors = FeatureCatalog.all.filter { it.id in ProfileFeatures.enabledFeatures },
        )
    }
    val enabledFeatures = remember(registry) {
        FeatureCatalog.all
            .filter { it.id in ProfileFeatures.enabledFeatures }
            .mapNotNull { descriptor ->
                descriptor.id.takeIf { registry.state(descriptor.id).enabled }
            }
            .toSet()
    }
    val favouritesEnabled = FeatureId.FAVOURITES in enabledFeatures
    val destinations = remember(enabledFeatures) {
        AppDestination.entries.filter { destination ->
            destination.featureId == null || destination.featureId in enabledFeatures
        }
    }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(destinations.size) {
        if (selectedIndex > destinations.lastIndex) {
            selectedIndex = destinations.lastIndex
        }
    }
    val safeIndex = selectedIndex.coerceIn(0, destinations.lastIndex)
    val destination = destinations[safeIndex]
    val state by discoverViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val openProject: (Project) -> Unit = { project -> context.openWebsite(project.website) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == safeIndex,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
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
            )

            AppDestination.SAVED -> {
                val favouritesViewModel: FavouritesViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            FavouritesViewModel(container.favouritesRepository)
                        }
                    },
                )
                val favouritesState by favouritesViewModel.uiState.collectAsStateWithLifecycle()

                FavouritesScreen(
                    state = favouritesState,
                    onRemove = favouritesViewModel::remove,
                    onSaveNote = favouritesViewModel::saveNote,
                    modifier = screenModifier,
                )
            }

            AppDestination.ABOUT -> AboutScreen(modifier = screenModifier)
        }
    }
}

private fun Context.openWebsite(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
