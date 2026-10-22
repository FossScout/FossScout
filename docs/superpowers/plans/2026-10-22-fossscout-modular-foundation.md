# FossScout Modular Foundation and Favourites — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the single-module FossScout scaffold into a reusable multi-module app with capability-based build profiles, local favourites with notes, and user-facing feature toggles — without adding standalone install or accessibility features.

**Architecture:** Pure-Kotlin `core:model` and `core:feature` modules hold reusable contracts. Compose feature modules (`feature:catalog`, `feature:favourites`, `feature:settings`) depend only on core contracts and `core:ui`. The `app` module is the composition root and owns `playstore`/`fdroid` product flavors. Persistence uses DataStore with kotlinx.serialization JSON for v1; no server, no account, no network.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3, AndroidX Lifecycle, DataStore Preferences 1.1.1, kotlinx.serialization 1.7.3, kotlinx-coroutines-test, JUnit 4. Min SDK 24, target/compile SDK 35, JDK 17.

**Spec:** `docs/superpowers/specs/2026-10-22-fossscout-feature-scope-design.md` and `docs/superpowers/specs/features/`

## Global Constraints

- v1 does **not** download or install APKs. F09 is deferred.
- v1 does **not** include an AccessibilityService. F10 is deferred.
- `playstore` profile has no `BROAD_PACKAGE_VISIBILITY` and no restricted capabilities.
- `fdroid` profile has `BROAD_PACKAGE_VISIBILITY`; F01 full scan is available there.
- No account, device identifier, analytics, or network call in this plan.
- All new user-facing text goes in `res/values/strings.xml`, not hardcoded Kotlin.
- New module packages use `com.fossscout.<module>`; keep `com.fossscout.app` for the application module.
- Every task ends with a passing test or build command and a commit.
- Do not add Room, Hilt, Koin, Retrofit, or a navigation library in this plan. YAGNI.

## Review Focus

- A favourite must survive repository recreation (the persistence boundary that stands in for app restart).
- Adding the same app twice must not overwrite or clear its private note.
- The Play profile must not expose a feature that requires `BROAD_PACKAGE_VISIBILITY`.
- A note longer than `MAX_NOTE_LENGTH` must fail instead of being silently truncated.
- Removing one favourite must not remove or corrupt the others.
- Importing/migrating data is **not** in this plan; do not add partial sharing code.

---

## File Structure

```text
settings.gradle.kts
gradle/libs.versions.toml
core/
  model/
    build.gradle.kts
    src/main/java/com/fossscout/core/model/Favourite.kt
    src/main/java/com/fossscout/core/model/AppKey.kt
    src/main/java/com/fossscout/core/model/Collection.kt
    src/test/java/com/fossscout/core/model/FavouriteTest.kt
  feature/
    build.gradle.kts
    src/main/java/com/fossscout/core/feature/Capability.kt
    src/main/java/com/fossscout/core/feature/FeatureDescriptor.kt
    src/main/java/com/fossscout/core/feature/FeatureRegistry.kt
    src/main/java/com/fossscout/core/feature/FeatureCatalog.kt
    src/test/java/com/fossscout/core/feature/FeatureRegistryTest.kt
  data/
    build.gradle.kts
    src/main/java/com/fossscout/core/data/FavouritesStore.kt
    src/main/java/com/fossscout/core/data/DataStoreFavouritesStore.kt
    src/main/java/com/fossscout/core/data/FavouritesRepository.kt
    src/main/java/com/fossscout/core/data/FeatureSettingsStore.kt
    src/main/java/com/fossscout/core/data/FeatureSettingsRepository.kt
    src/test/java/com/fossscout/core/data/FavouritesRepositoryTest.kt
    src/test/java/com/fossscout/core/data/FeatureSettingsRepositoryTest.kt
  ui/
    build.gradle.kts
    src/main/java/com/fossscout/core/ui/theme/*
    src/main/java/com/fossscout/core/ui/components/*
feature/
  catalog/
    build.gradle.kts
    src/main/java/com/fossscout/feature/catalog/*
  favourites/
    build.gradle.kts
    src/main/java/com/fossscout/feature/favourites/*
    src/main/java/com/fossscout/feature/favourites/NoteValidation.kt
  settings/
    build.gradle.kts
    src/main/java/com/fossscout/feature/settings/*
app/
  build.gradle.kts
  src/main/java/com/fossscout/app/*
  src/main/java/com/fossscout/app/AppContainer.kt
  src/playstore/java/com/fossscout/app/profile/ProfileFeatures.kt
  src/fdroid/java/com/fossscout/app/profile/ProfileFeatures.kt
```

---

### Task 1: Create the multi-module skeleton and core model

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `core/model/build.gradle.kts`
- Create: `core/feature/build.gradle.kts`
- Create: `core/data/build.gradle.kts`
- Create: `core/ui/build.gradle.kts`
- Create: `feature/catalog/build.gradle.kts`
- Create: `feature/favourites/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Create: `core/model/src/main/java/com/fossscout/core/model/AppKey.kt`
- Create: `core/model/src/main/java/com/fossscout/core/model/Favourite.kt`
- Create: `core/model/src/main/java/com/fossscout/core/model/Collection.kt`
- Test: `core/model/src/test/java/com/fossscout/core/model/FavouriteTest.kt`

**Interfaces:**
- Produces: `AppKey(value: String)`, `StoreId(value: String)`, `Favourite`, `AppCollection`, `InstalledState`, `MAX_NOTE_LENGTH`.

- [ ] **Step 1: Add the module build files and settings includes**

`settings.gradle.kts` includes:

```kotlin
include(
    ":app",
    ":core:model",
    ":core:feature",
    ":core:data",
    ":core:ui",
    ":feature:catalog",
    ":feature:favourites",
    ":feature:settings",
)
```

`core/model` and `core/feature` are `org.jetbrains.kotlin.jvm` modules. `core/data`, `core/ui`, and the feature modules are `com.android.library` + Kotlin Android + Compose where they contain UI. Add the serialization plugin to `core:model` and the Android modules that serialize.

- [ ] **Step 2: Write the failing model test**

```kotlin
package com.fossscout.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FavouriteTest {
    @Test
    fun `app key rejects blank values`() {
        assertThrows(IllegalArgumentException::class.java) { AppKey(" ") }
    }

    @Test
    fun `note length is capped`() {
        val favourite = Favourite(
            appKey = AppKey("org.example"),
            displayName = "Example",
            note = "x".repeat(MAX_NOTE_LENGTH + 1),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        assertThrows(IllegalArgumentException::class.java) { favourite.validate() }
    }
}
```

- [ ] **Step 3: Run the test and confirm it fails**

Run: `./gradlew :core:model:test`
Expected: compilation failure because the model types do not exist.

- [ ] **Step 4: Implement the models**

```kotlin
@Serializable
@JvmInline
value class AppKey(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
@JvmInline
value class StoreId(val value: String) {
    init { require(value.isNotBlank()) }
}

const val MAX_NOTE_LENGTH = 4_000

@Serializable
data class Favourite(
    val appKey: AppKey,
    val displayName: String,
    val preferredStoreId: StoreId? = null,
    val note: String = "",
    val tags: Set<String> = emptySet(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun validate() {
        require(note.length <= MAX_NOTE_LENGTH) { "Note is too long" }
        require(displayName.isNotBlank()) { "Display name is blank" }
    }
}

enum class InstalledState { INSTALLED, NOT_INSTALLED, UNAVAILABLE, UNKNOWN }

@Serializable
data class AppCollection(
    val id: String,
    val name: String,
    val description: String = "",
    val itemKeys: List<AppKey> = emptyList(),
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
```

- [ ] **Step 5: Run the test and confirm it passes**

Run: `./gradlew :core:model:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml core feature
git commit -m "build: add modular project skeleton and core models"
```

---

### Task 2: Add capability and feature registry contracts

**Files:**
- Create: `core/feature/src/main/java/com/fossscout/core/feature/Capability.kt`
- Create: `core/feature/src/main/java/com/fossscout/core/feature/FeatureDescriptor.kt`
- Create: `core/feature/src/main/java/com/fossscout/core/feature/FeatureRegistry.kt`
- Create: `core/feature/src/main/java/com/fossscout/core/feature/FeatureCatalog.kt`
- Test: `core/feature/src/test/java/com/fossscout/core/feature/FeatureRegistryTest.kt`

**Interfaces:**
- Produces: `Capability`, `Availability`, `FeatureId`, `FeatureDescriptor`, `BuildProfile`, `FeatureState`, `FeatureRegistry`, `FeatureCatalog.all`.
- Consumes: nothing from Task 1.

- [ ] **Step 1: Write the failing registry test**

```kotlin
package com.fossscout.core.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureRegistryTest {
    private val inventory = FeatureDescriptor(
        id = FeatureId.INVENTORY,
        availability = Availability.GENERAL_WITH_RESTRICTED_ENHANCEMENT,
        requiredCapabilities = setOf(Capability.MANUAL_PACKAGE_SELECTION),
        enhancedBy = setOf(Capability.BROAD_PACKAGE_VISIBILITY),
    )

    @Test
    fun `restricted enhancement is unavailable without the capability`() {
        val play = BuildProfile("playstore", setOf(Capability.MANUAL_PACKAGE_SELECTION))
        val registry = FeatureRegistry(play, listOf(inventory))
        val state = registry.state(FeatureId.INVENTORY)
        assertTrue(state.available)
        assertFalse(state.enhanced)
    }

    @Test
    fun `restricted feature is unavailable in the play profile`() {
        val standalone = FeatureDescriptor(
            id = FeatureId.STANDALONE,
            availability = Availability.RESTRICTED,
            requiredCapabilities = setOf(Capability.DIRECT_APK_INSTALL),
        )
        val play = BuildProfile("playstore", setOf(Capability.MANUAL_PACKAGE_SELECTION))
        val state = FeatureRegistry(play, listOf(standalone)).state(FeatureId.STANDALONE)
        assertFalse(state.available)
        assertTrue(state.missingCapabilities.contains(Capability.DIRECT_APK_INSTALL))
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:feature:test`
Expected: compilation failure.

- [ ] **Step 3: Implement the contracts**

```kotlin
enum class Capability {
    BROAD_PACKAGE_VISIBILITY,
    MANUAL_PACKAGE_SELECTION,
    ACCESSIBILITY_INSTALL_PROMPT,
    DIRECT_APK_INSTALL,
    BACKGROUND_UPDATE_CHECK,
    STORE_INDEX_NETWORK,
    SHARE_IMPORT_EXPORT,
    CUSTOM_STORE_ADAPTERS,
    SOURCE_REPOSITORY_ADAPTERS,
    ACCESSIBILITY_SERVICE,
}

enum class Availability { GENERAL, RESTRICTED, GENERAL_WITH_RESTRICTED_ENHANCEMENT }

enum class FeatureId {
    INVENTORY, MATCHING, ALTERNATIVES, STORES, INDEX_CACHE, COVERAGE,
    REPLACEMENT_GUIDANCE, COMPANION_HANDOFF, STANDALONE, INSTALL_PROMPT,
    FAVOURITES, SHARING, BACKUP, SECURITY, UPDATES, CONTRIBUTION, SETTINGS,
}

data class FeatureDescriptor(
    val id: FeatureId,
    val availability: Availability,
    val requiredCapabilities: Set<Capability>,
    val enhancedBy: Set<Capability> = emptySet(),
    val defaultEnabled: Boolean = true,
)

data class BuildProfile(val name: String, val capabilities: Set<Capability>)

data class FeatureState(
    val descriptor: FeatureDescriptor,
    val available: Boolean,
    val enhanced: Boolean,
    val enabled: Boolean,
    val missingCapabilities: Set<Capability>,
)

class FeatureRegistry(
    private val profile: BuildProfile,
    private val descriptors: List<FeatureDescriptor>,
    private val userDisabled: Set<FeatureId> = emptySet(),
) {
    fun state(id: FeatureId): FeatureState { /* implement */ }
    fun availableFeatures(): List<FeatureDescriptor> { /* implement */ }
}
```

`FeatureCatalog` lists all 17 descriptors with the availability and capabilities from `features/README.md`. `FeatureDescriptor` contains no user-facing text; the settings UI maps `FeatureId` and `Capability` to `strings.xml` resources. `FeatureRegistry.state` must return `available = requiredCapabilities.all(profile.capabilities::contains)`, `enhanced = enhancedBy.isNotEmpty() && enhancedBy.all(profile.capabilities::contains)`, `enabled = available && descriptor.defaultEnabled && id !in userDisabled`, and `missingCapabilities = requiredCapabilities - profile.capabilities`. An unknown `FeatureId` throws `IllegalArgumentException` naming the ID.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :core:feature:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/feature
git commit -m "feat: add capability-based feature registry"
```

---

### Task 3: Wire Play and F-Droid profiles

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/playstore/java/com/fossscout/app/profile/ProfileFeatures.kt`
- Create: `app/src/fdroid/java/com/fossscout/app/profile/ProfileFeatures.kt`
- Modify: `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`

**Interfaces:**
- Consumes: `BuildProfile`, `Capability`, `FeatureCatalog`.
- Produces: `ProfileFeatures.profile: BuildProfile`, `ProfileFeatures.enabledFeatures: Set<FeatureId>`.

- [ ] **Step 1: Add the distribution flavor dimension**

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("playstore") {
        dimension = "distribution"
        applicationId = "com.fossscout.app"
    }
    create("fdroid") {
        dimension = "distribution"
        applicationId = "com.fossscout.app.fdroid"
        versionNameSuffix = "-fdroid"
    }
}
```

- [ ] **Step 2: Add profile declarations**

`playstore` profile capabilities:

```kotlin
BuildProfile(
    name = "playstore",
    capabilities = setOf(
        Capability.MANUAL_PACKAGE_SELECTION,
        Capability.STORE_INDEX_NETWORK,
        Capability.SHARE_IMPORT_EXPORT,
        Capability.CUSTOM_STORE_ADAPTERS,
    ),
)
```

`fdroid` adds `Capability.BROAD_PACKAGE_VISIBILITY`. Neither profile includes `DIRECT_APK_INSTALL`, `ACCESSIBILITY_SERVICE`, or `ACCESSIBILITY_INSTALL_PROMPT` in v1.

`enabledFeatures` includes F01–F08, F11–F14, F16, F17; excludes F09, F10, F15.

In `FossScoutApp`, build the registry from the profile and the enabled set:

```kotlin
val descriptors = FeatureCatalog.all.filter { it.id in ProfileFeatures.enabledFeatures }
val registry = FeatureRegistry(ProfileFeatures.profile, descriptors)
```

The registry's `userDisabled` set is wired from settings in Task 7. Until then, use the empty default.

- [ ] **Step 3: Build both profiles**

Run: `./gradlew assemblePlaystoreDebug assembleFdroidDebug`
Expected: both builds succeed.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/playstore app/src/fdroid app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt
git commit -m "build: add playstore and fdroid capability profiles"
```

---

### Task 4: Move the existing catalog and theme into modules

**Files:**
- Move: `app/src/main/java/com/fossscout/app/data/*` → `feature/catalog/src/main/java/com/fossscout/feature/catalog/data/`
- Move: `app/src/main/java/com/fossscout/app/ui/discover/*` → `feature/catalog/src/main/java/com/fossscout/feature/catalog/discover/`
- Move: `app/src/main/java/com/fossscout/app/ui/components/*` → `core/ui/src/main/java/com/fossscout/core/ui/components/`
- Move: `app/src/main/java/com/fossscout/app/ui/theme/*` → `core/ui/src/main/java/com/fossscout/core/ui/theme/`
- Move: `app/src/test/java/com/fossscout/app/ui/discover/DiscoverUiStateTest.kt` → `feature/catalog/src/test/java/com/fossscout/feature/catalog/discover/DiscoverUiStateTest.kt`
- Modify: imports in moved files and `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`

**Interfaces:**
- Produces: `com.fossscout.feature.catalog.data.Project`, `ProjectCategory`, `SampleProjects`, `ProjectRepository`; `com.fossscout.feature.catalog.discover.DiscoverScreen`, `DiscoverViewModel`, `DiscoverUiState`; `com.fossscout.core.ui.theme.FossScoutTheme`; `com.fossscout.core.ui.components.ProjectCard`.

- [ ] **Step 1: Move files and update package declarations**

Use the package names above. Keep class names unchanged. Do not change behavior in this task.

- [ ] **Step 2: Update module dependencies**

`feature:catalog` depends on `core:model`, `core:ui`, Compose, Lifecycle. `core:ui` depends on Compose, Material 3, Material icons. `app` depends on `feature:catalog`, `core:ui`.

- [ ] **Step 3: Run the existing tests**

Run: `./gradlew :feature:catalog:test :app:testPlaystoreDebugUnitTest`
Expected: the existing `DiscoverUiStateTest` passes from its new module.

- [ ] **Step 4: Build both profiles**

Run: `./gradlew assemblePlaystoreDebug assembleFdroidDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core feature app
git commit -m "refactor: extract catalog and design system modules"
```

---

### Task 5: Add local favourites persistence

**Files:**
- Create: `core/data/src/main/java/com/fossscout/core/data/FavouritesStore.kt`
- Create: `core/data/src/main/java/com/fossscout/core/data/DataStoreFavouritesStore.kt`
- Create: `core/data/src/main/java/com/fossscout/core/data/FavouritesRepository.kt`
- Modify: `core/data/build.gradle.kts`
- Test: `core/data/src/test/java/com/fossscout/core/data/FavouritesRepositoryTest.kt`

**Interfaces:**
- Consumes: `Favourite`, `AppKey`, `StoreId`, `MAX_NOTE_LENGTH`.
- Produces: `FavouritesStore.favourites: Flow<List<Favourite>>`, `FavouritesStore.save(List<Favourite>)`, `FavouritesRepository.add`, `remove`, `updateNote`, `toggle`, `favourites: Flow<List<Favourite>>`.

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.fossscout.core.data

import com.fossscout.core.model.AppKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouritesRepositoryTest {
    private val store = FakeFavouritesStore()
    private val repository = FavouritesRepository(store, now = { 100L })

    @Test
    fun `adding a favourite does not require installation`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        assertEquals(listOf("org.example"), repository.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `favourite survives repository recreation`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        val recreated = FavouritesRepository(store, now = { 200L })
        assertEquals(listOf("org.example"), recreated.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `adding the same app twice keeps one entry and preserves the note`() = runTest {
        repository.add(AppKey("org.example"), "Example")
        repository.updateNote(AppKey("org.example"), "try this")
        repository.add(AppKey("org.example"), "Example renamed")
        val favourite = repository.favourites.first().single()
        assertEquals("try this", favourite.note)
        assertEquals("Example renamed", favourite.displayName)
    }

    @Test
    fun `removing a favourite removes only that app`() = runTest {
        repository.add(AppKey("a"), "A")
        repository.add(AppKey("b"), "B")
        repository.remove(AppKey("a"))
        assertEquals(listOf("b"), repository.favourites.first().map { it.appKey.value })
    }

    @Test
    fun `note update is capped`() = runTest {
        repository.add(AppKey("a"), "A")
        val result = runCatching { repository.updateNote(AppKey("a"), "x".repeat(4_001)) }
        assertTrue(result.isFailure)
    }
}
```

`FakeFavouritesStore` lives in the test source set and stores a `MutableStateFlow<List<Favourite>>`.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the store and repository**

```kotlin
interface FavouritesStore {
    val favourites: Flow<List<Favourite>>
    suspend fun save(favourites: List<Favourite>)
}

class FavouritesRepository(
    private val store: FavouritesStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    val favourites: Flow<List<Favourite>> = store.favourites

    suspend fun add(appKey: AppKey, displayName: String, preferredStoreId: StoreId? = null): Favourite
    suspend fun remove(appKey: AppKey)
    suspend fun updateNote(appKey: AppKey, note: String)
    suspend fun toggle(appKey: AppKey, displayName: String)
}
```

`add` updates an existing entry in place (preserving `createdAtEpochMillis`, note, tags) or appends a new one with `now()`. `updateNote` validates `MAX_NOTE_LENGTH` and updates `updatedAtEpochMillis`. `remove` filters by `appKey`. All writes call `store.save`.

`DataStoreFavouritesStore` uses a `DataStore<Preferences>` with one string key and `Json { ignoreUnknownKeys = true }`. It decodes to `List<Favourite>` and encodes on save. A corrupt value returns an empty list rather than crashing.

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: five tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/data
git commit -m "feat: persist favourites and notes locally"
```

---

### Task 6: Add the favourites UI and connect the saved tab

**Files:**
- Create: `feature/favourites/src/main/java/com/fossscout/feature/favourites/FavouritesScreen.kt`
- Create: `feature/favourites/src/main/java/com/fossscout/feature/favourites/FavouritesViewModel.kt`
- Create: `feature/favourites/src/main/java/com/fossscout/feature/favourites/FavouriteNoteDialog.kt`
- Create: `feature/favourites/src/main/java/com/fossscout/feature/favourites/NoteValidation.kt`
- Create: `app/src/main/java/com/fossscout/app/AppContainer.kt`
- Modify: `feature/catalog/src/main/java/com/fossscout/feature/catalog/discover/DiscoverViewModel.kt`
- Modify: `feature/catalog/src/main/java/com/fossscout/feature/catalog/discover/DiscoverScreen.kt`
- Modify: `app/src/main/java/com/fossscout/app/MainActivity.kt`
- Modify: `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`

**Interfaces:**
- Consumes: `FavouritesRepository`, `Favourite`, `AppKey`.
- Produces: `AppContainer.favouritesRepository`, `FavouritesScreen(state, onRemove, onNoteChange)`, `FavouritesViewModel`, `FavouritesUiState`.

- [ ] **Step 1: Add a note validation test**

In `feature:favourites` test source set:

```kotlin
@Test
fun `blank note is stored as empty`() {
    assertEquals("", normalizeNote("   "))
}

@Test
fun `note over the limit is rejected`() {
    assertTrue(runCatching { normalizeNote("x".repeat(4_001)) }.isFailure)
}
```

`normalizeNote(raw: String): String` lives in `NoteValidation.kt`. It trims whitespace, returns `""` for a blank value, and throws `IllegalArgumentException` when the trimmed length exceeds `MAX_NOTE_LENGTH`.

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :feature:favourites:testDebugUnitTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the composition root and UI**

`AppContainer` is the only place that constructs DataStore-backed repositories:

```kotlin
private val Context.favouritesDataStore by preferencesDataStore(name = "favourites")

class AppContainer(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    val favouritesRepository = FavouritesRepository(
        DataStoreFavouritesStore(context.favouritesDataStore, json),
    )
}
```

`MainActivity` creates one `AppContainer` and passes it to `FossScoutApp`. `FossScoutApp` uses `viewModel(factory = viewModelFactory { initializer { ... } })` to construct `DiscoverViewModel(container.favouritesRepository, ProjectRepository())`; do not add a global service locator.

`FavouritesViewModel` exposes `StateFlow<FavouritesUiState>` with favourites, loading, and error. It collects `FavouritesRepository.favourites`. `FavouritesScreen` shows a list of `FavouriteCard` items with app name, optional note preview, a note edit action, and a remove action. `FavouriteNoteDialog` edits the note with a character counter and a save button.

Connect the saved tab in `FossScoutApp`: replace the `DiscoverScreen(savedOnly = true)` branch with `FavouritesScreen` when `FeatureId.FAVOURITES` is enabled. If disabled, hide the Saved destination.

`DiscoverViewModel` takes `FavouritesRepository` and derives `savedIds` from its favourites flow. `onToggleSaved` calls `add`/`remove` instead of mutating an in-memory set.

- [ ] **Step 4: Run foreground tests and build**

Run: `./gradlew :feature:favourites:testDebugUnitTest :feature:catalog:test assemblePlaystoreDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add feature/favourites feature/catalog app
git commit -m "feat: add favourites UI with private notes"
```

---

### Task 7: Add feature settings and runtime toggles

**Files:**
- Create: `core/data/src/main/java/com/fossscout/core/data/FeatureSettingsStore.kt`
- Create: `core/data/src/main/java/com/fossscout/core/data/FeatureSettingsRepository.kt`
- Create: `feature/settings/src/main/java/com/fossscout/feature/settings/FeatureSettingsScreen.kt`
- Create: `feature/settings/src/main/java/com/fossscout/feature/settings/FeatureSettingsViewModel.kt`
- Modify: `core/data/build.gradle.kts`
- Test: `core/data/src/test/java/com/fossscout/core/data/FeatureSettingsRepositoryTest.kt`
- Modify: `app/src/main/java/com/fossscout/app/AppContainer.kt`
- Modify: `app/src/main/java/com/fossscout/app/ui/FossScoutApp.kt`

**Interfaces:**
- Consumes: `FeatureRegistry`, `FeatureId`, `FeatureState`.
- Produces: `FeatureSettingsStore.disabled: Flow<Set<String>>`, `FeatureSettingsStore.save(Set<String>)`, `FeatureSettingsRepository.disabled: Flow<Set<FeatureId>>`, `setEnabled(FeatureId, Boolean)`, `FeatureSettingsScreen(states, onToggle)`.

- [ ] **Step 1: Write the failing settings test**

```kotlin
@Test
fun `disabling and re-enabling a feature preserves the disabled set only`() = runTest {
    val store = FakeFeatureSettingsStore()
    val repository = FeatureSettingsRepository(store)
    repository.setEnabled(FeatureId.SHARING, false)
    assertEquals(setOf(FeatureId.SHARING), repository.disabled.first())
    repository.setEnabled(FeatureId.SHARING, true)
    assertTrue(repository.disabled.first().isEmpty())
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: compilation failure.

- [ ] **Step 3: Implement the settings store and UI**

`FeatureSettingsStore` persists a `Set<String>` of disabled feature IDs in DataStore. `FeatureSettingsRepository` maps strings to `FeatureId`, ignores unknown IDs, and writes on each change.

```kotlin
interface FeatureSettingsStore {
    val disabled: Flow<Set<String>>
    suspend fun save(disabled: Set<String>)
}

class FeatureSettingsRepository(private val store: FeatureSettingsStore) {
    val disabled: Flow<Set<FeatureId>>
    suspend fun setEnabled(id: FeatureId, enabled: Boolean)
}
```

Add `DataStoreFeatureSettingsStore` beside `DataStoreFavouritesStore` and expose it from `AppContainer` as `featureSettingsRepository`.

`FeatureSettingsScreen` lists `FeatureState`s. Available features get a Switch; unavailable features show a localized title from `FeatureId`, an “Unavailable in this build” string from `strings.xml`, and localized labels for `state.missingCapabilities`. Do not display raw enum names. Use `FeatureRegistry` with the current profile and the repository's disabled set.

Add a Settings destination to the bottom navigation. If `FeatureId.SETTINGS` is disabled, hide it. Changing a toggle updates the registry state immediately.

- [ ] **Step 4: Run tests and build both profiles**

Run: `./gradlew :core:data:testDebugUnitTest :feature:settings:testDebugUnitTest assemblePlaystoreDebug assembleFdroidDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/data feature/settings app
git commit -m "feat: add feature settings and runtime toggles"
```

---

### Task 8: Verify the foundation and document it

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-10-22-fossscout-feature-scope-design.md` (status only)

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: all JVM unit tests pass.

- [ ] **Step 2: Build both profiles**

Run: `./gradlew assemblePlaystoreDebug assembleFdroidDebug`
Expected: both APKs build.

- [ ] **Step 3: Inspect the Play manifest**

Run: `./gradlew :app:processPlaystoreDebugMainManifest` then inspect the merged manifest.
Expected: no `AccessibilityService`, no `REQUEST_INSTALL_PACKAGES`, no `QUERY_ALL_PACKAGES`.

- [ ] **Step 4: Update README**

Document the module layout, the `playstore` and `fdroid` flavors, and that v1 does not install APKs.

- [ ] **Step 5: Commit**

```bash
git add README.md docs app
git commit -m "docs: document modular foundation and build profiles"
```

---

## Follow-up plans

This plan stops at a working foundation with favourites, notes, and feature
toggles. Separate plans are required for:

1. Store registry, F-Droid index adapter, matching, alternatives, coverage, and
   companion handoff.
2. Collections, sharing, import/export, and backup/migration.
3. Play/F-Droid policy verification and release metadata.

Do not implement those subsystems inside this plan.
