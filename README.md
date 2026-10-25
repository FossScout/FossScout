# FossScout

FossScout is an Android app for discovering free and open source software. It
combines a bundled project catalog with installed-app inventory, FOSS matching
against verified store indexes, replacement guidance and companion-store
handoff, favourites and collections, and local sharing/import, all in a Material
3 Compose UI.

## Status

The modular foundation for v1 is implemented. The app is split into core and
feature modules, ships as `playstore` and `fdroid` build profiles, and persists
favourites, collections, and settings locally with DataStore.

Current implementation status:
[`docs/superpowers/specs/features/STATUS.md`](docs/superpowers/specs/features/STATUS.md).
Remaining feature work is split into independent sessions:
[`docs/superpowers/sessions/README.md`](docs/superpowers/sessions/README.md).

Installed-app inventory, the store registry, verified offline index caching,
FOSS matching, coverage reporting, alternative suggestions, replacement
guidance, companion-store handoff, collections, and local sharing/import are
implemented. Backup, security details, and contribution reports remain scheduled
sessions.

**v1 does not download or install APKs, and it has no AccessibilityService.**
Direct APK install and the install-time accessibility prompt are deferred.

## Features

- Browse a curated catalog of FOSS projects
- Search by project name, tagline, or tag
- Filter by category
- Favourite projects and keep a private note on each one; favourites persist
  across app restarts
- Build local collections and share/import them as JSON, plain text, or files
- Inspect installed apps on the device and match them against verified F-Droid
  and IzzyOnDroid indexes, with coverage gaps and clearly labelled alternatives
- See whether a store build is an update, a replacement, or a different app,
  including signer, version, installer, and data-loss warnings
- Hand off to F-Droid, Droid-ify, or Neo Store, or open a browser fallback, with
  repository URL/fingerprint copy and QR codes; FossScout never installs or
  uninstalls anything
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
| `fdroid` | `com.fossscout.app.fdroid` | Version name suffixed `-fdroid`. Profile adds the broad package visibility capability and declares `QUERY_ALL_PACKAGES` for the full inventory scan. |

Both profiles currently enable the same features. Each flavor supplies its own
`ProfileFeatures` object under `app/src/<flavor>/java/`, which maps the build
profile's capabilities to the feature registry. Optional features are separate
modules; the current app composition includes all of them, and a future profile
can exclude a module at the dependency boundary.

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
│   ├── model/                  # Domain types: AppKey, Favourite, AppCollection
│   ├── feature/                # Capability, feature descriptors, registry, feature catalog
│   ├── inventory/              # Installed-app contracts and inventory repository
│   ├── store-api/              # Store/adapter contracts and registry models
│   ├── index/                  # Signed F-Droid index sync, verification, and snapshots
│   ├── matching/               # Pure-JVM matching, alternatives, and coverage engine
│   ├── data/                   # Favourites, collections, settings, and DataStore stores
│   └── ui/                     # Shared Compose theme (colors, typography, theming)
├── feature/
│   ├── catalog/                # Bundled catalog, discover screen and view model
│   ├── favourites/             # Saved-projects screen, notes and validation
│   ├── inventory/              # Installed-app inventory and scan UI
│   ├── matching/               # Match result, evidence, coverage, and alternatives UI
│   ├── stores/                 # Store registry, sync, and trust evidence UI
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

- Implement replacement guidance and companion store handoff
- Add backup and migration for favourites, collections, and settings
- Add security/trust details and contribution hooks
- Add project detail screens
- Expand device/emulator verification and set up CI for builds and tests

Direct APK download/install and the install-time accessibility prompt are
post-v1 and restricted to builds where the platform policy allows them.

## License

Released under the [MIT License](LICENSE).
