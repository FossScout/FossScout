package com.fossscout.feature.inventory

import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InventoryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ClassificationLabelsTest {
    @Test
    fun `every classification has a distinct label and explanation`() {
        val labels = Classification.entries.map { it.labelRes() }
        val explanations = Classification.entries.map { it.explanationRes() }

        assertEquals(Classification.entries.size, labels.toSet().size)
        assertEquals(Classification.entries.size, explanations.toSet().size)
        Classification.entries.forEach { classification ->
            assertNotEquals(0, classification.labelRes())
            assertNotEquals(0, classification.explanationRes())
        }
    }

    @Test
    fun `both coverage statuses have a label`() {
        assertEquals(
            InventoryStatus.entries.size,
            InventoryStatus.entries.map { it.labelRes() }.toSet().size,
        )
    }
}
