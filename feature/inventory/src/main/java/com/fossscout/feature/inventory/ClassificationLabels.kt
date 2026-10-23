package com.fossscout.feature.inventory

import androidx.annotation.StringRes
import com.fossscout.core.inventory.Classification
import com.fossscout.core.inventory.InventoryStatus

@StringRes
internal fun Classification.labelRes(): Int = when (this) {
    Classification.FOSS -> R.string.inventory_classification_foss
    Classification.PROPRIETARY -> R.string.inventory_classification_proprietary
    Classification.UNKNOWN -> R.string.inventory_classification_unknown
    Classification.UNAVAILABLE -> R.string.inventory_classification_unavailable
}

@StringRes
internal fun Classification.explanationRes(): Int = when (this) {
    Classification.FOSS -> R.string.inventory_explanation_foss
    Classification.PROPRIETARY -> R.string.inventory_explanation_proprietary
    Classification.UNKNOWN -> R.string.inventory_explanation_unknown
    Classification.UNAVAILABLE -> R.string.inventory_explanation_unavailable
}

@StringRes
internal fun InventoryStatus.labelRes(): Int = when (this) {
    InventoryStatus.COMPLETE -> R.string.inventory_status_complete
    InventoryStatus.PARTIAL -> R.string.inventory_status_partial
}
