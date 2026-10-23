package com.fossscout.core.inventory

import com.fossscout.core.model.InstalledState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledAppStateProviderTest {
    private val installed = InstalledApp(
        packageName = "org.example.app",
        label = "Example",
    )

    @Test
    fun `visible package is installed`() = runTest {
        val source = FakeInstalledAppSource(apps = listOf(installed))
        val provider = InstalledAppStateProvider(source)

        assertEquals(InstalledState.INSTALLED, provider.stateFor("org.example.app"))
    }

    @Test
    fun `missing package is not installed when visibility is reliable`() = runTest {
        val provider = InstalledAppStateProvider(FakeInstalledAppSource(supportsFullScan = true))

        assertEquals(InstalledState.NOT_INSTALLED, provider.stateFor("org.example.gone"))
    }

    @Test
    fun `missing package is unavailable without broad visibility`() = runTest {
        val provider = InstalledAppStateProvider(FakeInstalledAppSource(supportsFullScan = false))

        assertEquals(InstalledState.UNAVAILABLE, provider.stateFor("org.example.hidden"))
    }

    @Test
    fun `source-only keys stay unknown without a package lookup`() = runTest {
        val source = FakeInstalledAppSource(apps = listOf(installed))
        val provider = InstalledAppStateProvider(source)

        assertEquals(InstalledState.UNKNOWN, provider.stateFor("signal"))
        assertEquals(0, source.findCalls)
    }

    @Test
    fun `security failure is unavailable and other failures are unknown`() = runTest {
        val blocked = InstalledAppStateProvider(
            FakeInstalledAppSource(findError = SecurityException("hidden")),
        )
        val broken = InstalledAppStateProvider(
            FakeInstalledAppSource(findError = IllegalStateException("boom")),
        )

        assertEquals(InstalledState.UNAVAILABLE, blocked.stateFor("org.example.app"))
        assertEquals(InstalledState.UNKNOWN, broken.stateFor("org.example.app"))
    }

    @Test
    fun `cancellation propagates`() = runTest {
        val provider = InstalledAppStateProvider(
            FakeInstalledAppSource(findError = CancellationException("cancelled")),
        )

        val failure = runCatching { provider.stateFor("org.example.app") }.exceptionOrNull()

        assertTrue(failure is CancellationException)
    }
}
