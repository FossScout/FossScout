package com.fossscout.feature.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fossscout.core.ui.theme.FossScoutTheme
import com.fossscout.feature.catalog.R
import com.fossscout.feature.catalog.components.LicensePill
import com.fossscout.feature.catalog.components.ProjectMonogram
import com.fossscout.feature.catalog.components.formatStars
import com.fossscout.feature.catalog.data.Project
import com.fossscout.feature.catalog.data.SampleProjects

@Composable
fun ProjectDetailScreen(
    project: Project,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onOpenWebsite: () -> Unit,
    modifier: Modifier = Modifier,
    showSaveAction: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.project_detail_back))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectMonogram(name = project.name, size = 64.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = project.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = project.tagline,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ProjectFacts(project = project)
            }
            item {
                DetailSection(title = stringResource(R.string.project_detail_about)) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                DetailSection(title = stringResource(R.string.project_detail_tags)) {
                    Text(
                        text = project.tags.joinToString("  ·  ") { "#$it" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            item {
                WebsiteCard(project = project, onOpenWebsite = onOpenWebsite)
            }
            if (showSaveAction) {
                item {
                    OutlinedButton(
                        onClick = onToggleSaved,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (isSaved) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (isSaved) {
                                    R.string.project_detail_saved
                                } else {
                                    R.string.project_detail_save
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectFacts(project: Project) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LicensePill(license = project.license)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.project_detail_stars, formatStars(project.stars)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        content()
    }
}

@Composable
private fun WebsiteCard(
    project: Project,
    onOpenWebsite: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.project_detail_website),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = project.website,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onOpenWebsite,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.project_detail_visit_website))
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ProjectDetailScreenPreview() {
    FossScoutTheme {
        ProjectDetailScreen(
            project = SampleProjects.first(),
            isSaved = true,
            onBack = {},
            onToggleSaved = {},
            onOpenWebsite = {},
        )
    }
}
