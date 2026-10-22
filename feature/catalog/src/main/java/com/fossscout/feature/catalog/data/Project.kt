package com.fossscout.feature.catalog.data

/**
 * Broad groups used to filter the catalog. [ALL] is a UI-only sentinel and is not
 * assigned to any project.
 */
enum class ProjectCategory(val label: String) {
    ALL("All"),
    DEVELOPER_TOOLS("Developer tools"),
    DESIGN("Design"),
    MEDIA("Media"),
    PRODUCTIVITY("Productivity"),
    PRIVACY("Privacy"),
    EDUCATION("Education"),
}

/**
 * A free and open source project that can be browsed in FossScout.
 */
data class Project(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val category: ProjectCategory,
    val license: String,
    val website: String,
    val tags: List<String>,
    val stars: Int,
)
