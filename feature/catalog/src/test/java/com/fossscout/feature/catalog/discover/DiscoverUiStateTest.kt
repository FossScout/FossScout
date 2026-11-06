package com.fossscout.feature.catalog.discover

import com.fossscout.feature.catalog.data.ProjectCategory
import com.fossscout.feature.catalog.data.SampleProjects
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverUiStateTest {

    private val state = DiscoverUiState(projects = SampleProjects)

    @Test
    fun `query matches names, taglines, and tags`() {
        assertEquals(listOf("Signal"), state.copy(query = "signal").visibleProjects(false).map { it.name })
        assertEquals(listOf("Joplin"), state.copy(query = "markdown").visibleProjects(false).map { it.name })
        assertEquals(2, state.copy(query = "offline").visibleProjects(false).size)
    }

    @Test
    fun `query matches project descriptions`() {
        assertEquals(
            listOf("F-Droid"),
            state.copy(query = "reproducible").visibleProjects(false).map { it.name },
        )
    }

    @Test
    fun `multi keyword query requires every keyword across fields`() {
        assertEquals(
            listOf("Blender"),
            state.copy(query = "3d animation").visibleProjects(false).map { it.name },
        )
        assertEquals(
            listOf("Organic Maps"),
            state.copy(query = "offline maps").visibleProjects(false).map { it.name },
        )
        assertTrue(state.copy(query = "offline qwerty").visibleProjects(false).isEmpty())
    }

    @Test
    fun `tag query accepts hash prefixes and comma separators`() {
        assertEquals(
            listOf("Anki"),
            state.copy(query = "#flashcards").visibleProjects(false).map { it.name },
        )
        assertEquals(
            listOf("Signal"),
            state.copy(query = "#privacy").visibleProjects(false).map { it.name },
        )
        assertEquals(
            listOf("Organic Maps"),
            state.copy(query = "offline, maps").visibleProjects(false).map { it.name },
        )
    }

    @Test
    fun `blank query keeps every project`() {
        assertEquals(SampleProjects, state.copy(query = "   ").visibleProjects(false))
    }

    @Test
    fun `category filter narrows the catalog`() {
        val designProjects = state.copy(category = ProjectCategory.DESIGN).visibleProjects(false)

        assertEquals(listOf("Blender", "Krita"), designProjects.map { it.name })
    }

    @Test
    fun `saved tab only shows saved projects`() {
        val savedState = state.copy(savedIds = setOf("godot", "anki"))

        assertTrue(savedState.visibleProjects(true).all { it.id in savedState.savedIds })
        assertEquals(2, savedState.visibleProjects(true).size)
        assertEquals(SampleProjects.size, savedState.visibleProjects(false).size)
    }

    @Test
    fun `filters combine`() {
        val filtered = state.copy(
            query = "offline",
            category = ProjectCategory.EDUCATION,
        ).visibleProjects(false)

        assertEquals(listOf("Kiwix"), filtered.map { it.name })
    }
}
