# FossScout

FossScout is an Android app for discovering free and open source software. The
current `v0.1.0` foundation is a Material 3 Compose app with a bundled catalog,
search and category filters, locally persisted favourites with private notes,
runtime feature toggles, and an about screen.

## Status

The modular foundation for v1 is implemented. The app is split into core and
feature modules, ships as `playstore` and `fdroid` build profiles, and persists
favourites and feature settings locally with DataStore.

The catalog is still bundled with the app. Inventory scanning, matching,
alternatives, the store registry, index caching, coverage reporting, replacement
guidance, and companion handoff are declared in the feature registry but are not
implemented yet.

**v1 does not download or install APKs, and it has no AccessibilityService.**
Direct APK install and the install-time accessibility prompt are deferred.

## Features

- Browse a curated catalog of FOSS projects
- Search by project name, tagline, or tag
- Filter by category
- Favourite projects and keep a private note on each one; favourites persist
  across app restarts
- Enable or disable available features from the Settings screen; the choice
  persists across app restarts
- Light and dark themes with a custom green palette
- Edge-to-edge Material 3 UI built entirely with Jetpack Compose

## Tech stack

- Kotlin 2.0
- Jetpack Compose + Material 3
- AndroidX Lifecycle and ViewModel with StateFlow
- DataStore Preferences with kotlinx.serialization for local persistence
- Gradle version catalog (`gradle/libs.versions.toml`)
- Min SDK 24, target/compile SDK 35

## Build profiles

The app has two product flavors on the `distribution` dimension:

| Flavor | Application ID | Notes |
| --- | --- | --- |
| `playstore` | `com.fossscout.app` | Play-safe capability set. No install-packages permission, no broad package visibility, and no accessibility service. |
| `fdroid` | `com.fossscout.app.fdroid` | Version name suffixed `-fdroid`. Profile adds the broad package visibility capability; no matching manifest permission is declared yet. |

Both profiles currently enable the same features. Each flavor supplies its own
`ProfileFeatures` object under `app/src/<flavor>/java/`, which maps the build
profile's capabilities to the feature registry. Optional features are separate
modules so a future profile can exclude them.

## Getting started

Requirements:

- Android Studio (or a command-line Android SDK) with **SDK 35** and
  **Build-Tools 35.0.0**
- JDK 17 or newer

Build a debug APK for a specific profile:

```sh
./gradlew assemblePlaystoreDebug
./gradlew assembleFdroidDebug
```

The APKs are written under `app/build/outputs/apk/<flavor>/debug/`.

Run all JVM unit tests:

```sh
./gradlew test
```

Install a profile on a connected device or emulator:

```sh
./gradlew installPlaystoreDebug
./gradlew installFdroidDebug
```

If you build outside Android Studio, create a `local.properties` file with the
path to your SDK:

```properties
sdk.dir=/path/to/Android/Sdk
```

`local.properties` is intentionally ignored by git.

## Project structure

```text
FossScout/
├── app/                        # Composition root: activity, navigation shell, DI, flavors
│   ├── src/main/
│   ├── src/playstore/          # Play profile capabilities and enabled features
│   └── src/fdroid/             # F-Droid profile capabilities and enabled features
├── core/
│   ├── model/                  # Domain types: AppKey, Favourite, Collection
│   ├── feature/                # Capability, feature descriptors, registry, feature catalog
│   ├── data/                   # Favourites and feature-settings repositories + DataStore stores
│   └── ui/                     # Shared Compose theme (colors, typography, theming)
├── feature/
│   ├── catalog/                # Bundled catalog, discover screen and view model
│   ├── favourites/             # Saved-projects screen, notes and validation
│   └── settings/               # Feature toggle screen and labels
├── docs/                       # Design specs and implementation plans
├── gradle/                     # Version catalog and wrapper
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

Dependencies point inward: `feature:*` modules depend on `core:*`, and `:app`
depends on the feature modules and wires them together. Feature modules do not
depend on `:app` or on each other.

## Roadmap

- Implement installed-app inventory and FOSS matching, using manual package
  selection on Play and the broader scan on F-Droid
- Add the store registry, index cache, coverage reporting, replacement guidance,
  and companion handoff
- Add sharing, import/export, and backup for favourites and settings
- Add project detail screens and contribution hooks
- Add tests around remaining view models and repositories
- Set up CI for builds and tests

Direct APK download/install and the install-time accessibility prompt are
post-v1 and restricted to builds where the platform policy allows them.

## License

Released under the [MIT License](LICENSE).
