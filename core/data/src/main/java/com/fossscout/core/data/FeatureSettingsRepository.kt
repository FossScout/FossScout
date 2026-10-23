package com.fossscout.core.data

import com.fossscout.core.feature.BuildProfile
import com.fossscout.core.feature.FeatureCatalog
import com.fossscout.core.feature.FeatureDescriptor
import com.fossscout.core.feature.FeatureId
import com.fossscout.core.feature.FeatureRegistry
import com.fossscout.core.feature.FeatureSettingValue
import com.fossscout.core.feature.FeatureSettings
import com.fossscout.core.feature.FeatureState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FeatureSettingsRepository(
    private val store: FeatureSettingsStore,
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor> = FeatureCatalog.all,
) {
    private val mutex = Mutex()
    private val descriptorsById = descriptors.associateBy(FeatureDescriptor::id)

    /**
     * Read-only, sanitized, versioned snapshot for backup (F13). Values that no
     * longer match a declared setting fall back to defaults in [states]; the
     * snapshot only contains declarations this build understands.
     */
    val snapshot: Flow<FeatureSettings> = store.settings.map(::sanitize)

    val states: Flow<List<FeatureState>> = snapshot.map(::statesFor)

    fun defaultSnapshot(): FeatureSettings = FeatureSettings(
        disabledFeatureIds = descriptors
            .filterNot(FeatureDescriptor::defaultEnabled)
            .map(FeatureDescriptor::id)
            .toSet(),
    )

    fun defaultStates(): List<FeatureState> = statesFor(defaultSnapshot())

    fun statesFor(settings: FeatureSettings): List<FeatureState> {
        val registry = registryFor(settings)
        return descriptors.map { registry.state(it.id) }
    }

    suspend fun setEnabled(id: FeatureId, enabled: Boolean) = mutex.withLock {
        descriptor(id)
        val current = currentSettings()
        val state = registryFor(current).state(id)
        require(!enabled || state.available) {
            "Feature ${id.name} is unavailable in profile ${profile.name}"
        }

        val updatedDisabled = if (enabled) {
            current.disabledFeatureIds - id
        } else {
            current.disabledFeatureIds + id
        }
        if (updatedDisabled != current.disabledFeatureIds) {
            store.save(current.copy(disabledFeatureIds = updatedDisabled))
        }
    }

    suspend fun setSettingValue(
        id: FeatureId,
        key: String,
        value: FeatureSettingValue,
    ) = mutex.withLock {
        val descriptor = descriptor(id)
        val setting = descriptor.settings.firstOrNull { it.key == key }
            ?: throw IllegalArgumentException("Unknown setting $key for feature ${id.name}")
        require(setting.validate(value)) { "Invalid value for setting $key" }

        val current = currentSettings()
        val state = registryFor(current).state(id)
        require(state.available) {
            "Feature ${id.name} is unavailable in profile ${profile.name}"
        }
        if (current.valueFor(id, setting) == value) return@withLock

        val updatedValues = current.values.toMutableMap()
        updatedValues[id] = current.valuesFor(id) + (key to value)
        store.save(current.copy(values = updatedValues))
    }

    suspend fun resetFeature(id: FeatureId) = mutex.withLock {
        val descriptor = descriptor(id)
        val current = currentSettings()
        val updatedDisabled = if (descriptor.defaultEnabled) {
            current.disabledFeatureIds - id
        } else {
            current.disabledFeatureIds + id
        }
        val updated = current.copy(
            disabledFeatureIds = updatedDisabled,
            values = current.values - id,
        )
        if (updated != current) store.save(updated)
    }

    suspend fun resetAll() = mutex.withLock {
        val current = currentSettings()
        val defaults = defaultSnapshot()
        if (defaults != current) store.save(defaults)
    }

    private suspend fun currentSettings(): FeatureSettings =
        sanitize(store.settings.first())

    private fun registryFor(settings: FeatureSettings): FeatureRegistry =
        FeatureRegistry(
            profile = profile,
            descriptors = descriptors,
            userDisabled = settings.disabledFeatureIds,
            storedValues = settings.values,
        )

    private fun descriptor(id: FeatureId): FeatureDescriptor =
        descriptorsById[id] ?: throw IllegalArgumentException("Unknown feature id: $id")

    private fun sanitize(settings: FeatureSettings): FeatureSettings {
        val disabled = settings.disabledFeatureIds
            .filterTo(mutableSetOf()) { id -> id in descriptorsById }

        val values = mutableMapOf<FeatureId, Map<String, FeatureSettingValue>>()
        settings.values.forEach { (featureId, storedValues) ->
            val descriptor = descriptorsById[featureId] ?: return@forEach
            val validValues = mutableMapOf<String, FeatureSettingValue>()
            storedValues.forEach { (key, value) ->
                val setting = descriptor.settings.firstOrNull { it.key == key }
                if (setting != null && setting.validate(value)) {
                    validValues[key] = value
                }
            }
            if (validValues.isNotEmpty()) {
                values[featureId] = validValues
            }
        }

        return settings.copy(disabledFeatureIds = disabled, values = values)
    }
}
