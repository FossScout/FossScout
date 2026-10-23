package com.fossscout.core.inventory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassificationSourceTest {
    @Test
    fun `stub classifier returns unknown for every app`() = runTest {
        val app = InstalledApp(packageName = "org.example.app", label = "Example")

        assertEquals(
            ClassificationResult(Classification.UNKNOWN),
            UnknownClassificationSource.classify(app),
        )
    }

    @Test
    fun `classification has the four required labels`() {
        assertEquals(
            listOf("FOSS", "PROPRIETARY", "UNKNOWN", "UNAVAILABLE"),
            Classification.entries.map { it.name },
        )
    }
}
