package com.fossscout.core.data

import com.fossscout.core.model.MatchOverride
import kotlinx.coroutines.flow.Flow

/** Local persistence contract for user match corrections. */
interface MatchOverridesStore {
    val overrides: Flow<List<MatchOverride>>

    suspend fun save(overrides: List<MatchOverride>)
}
