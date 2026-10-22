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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fossscout.app.data.Project
import com.fossscout.app.profile.ProfileFeatures
import com.fossscout.app.ui.about.AboutScreen
import com.fossscout.app.ui.discover.DiscoverScreen
import com.fossscout.app.ui.discover.DiscoverViewModel
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureRegistry

private enum class AppDestination(
    val label: String,
    val icon: ImageVector,
) {
    DISCOVER("Discover", Icons.Filled.Search),
    SAVED("Saved", Icons.Filled.Favorite),
    ABOUT("About", Icons.Filled.Info),
}

@Composable
fun FossScoutApp(
    discoverViewModel: DiscoverViewModel = viewModel(),
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val destinations = AppDestination.entries
    val destination = destinations[selectedIndex]
    val state by discoverViewModel.uiState.collectAsStateWithLifecycle()

    val descriptors = FeatureCatalog.all.filter { it.id in ProfileFeatures.enabledFeatures }
    val registry = FeatureRegistry(ProfileFeatures.profile, descriptors)

    val context = LocalContext.current
    val openProject: (Project) -> Unit = { project -> context.openWebsite(project.website) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = index == selectedIndex,
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
            )

            AppDestination.SAVED -> DiscoverScreen(
                state = state,
                savedOnly = true,
                onQueryChange = discoverViewModel::onQueryChange,
                onCategorySelected = discoverViewModel::onCategorySelected,
                onToggleSaved = discoverViewModel::onToggleSaved,
                onClearFilters = discoverViewModel::clearFilters,
                onOpenProject = openProject,
                modifier = screenModifier,
            )

            AppDestination.ABOUT -> AboutScreen(modifier = screenModifier)
        }
    }
}

private fun Context.openWebsite(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
