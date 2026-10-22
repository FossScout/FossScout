package com.fossscout.feature.catalog.data

/**
 * Single access point for project data. Keeping this behind a repository makes it
 * straightforward to swap the bundled catalog for a network or database source.
 */
class ProjectRepository(
    private val source: List<Project> = SampleProjects,
) {
    fun getAll(): List<Project> = source
}
