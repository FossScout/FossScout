# FossScout

FossScout is an Android app for discovering free and open source software. This
repository contains the initial application scaffold: a Material 3 Compose UI, a
small bundled catalog, search and category filters, saved projects, and an about
screen.

## Status

Early foundation (`v0.1.0`). The catalog is bundled with the app and saved
projects are kept in memory, so they reset when the app process is recreated.
The structure is ready for a real data source and local persistence.

## Features

- Browse a curated catalog of FOSS projects
- Search by project name, tagline, or tag
- Filter by category
- Save projects for later
- Light and dark themes with a custom green palette
- Edge-to-edge Material 3 UI built entirely with Jetpack Compose

## Tech stack

- Kotlin 2.0
- Jetpack Compose + Material 3
- AndroidX Lifecycle and ViewModel with StateFlow
- Gradle version catalog (`gradle/libs.versions.toml`)
- Min SDK 24, target/compile SDK 35

## Getting started

Requirements:

- Android Studio (or a command-line Android SDK) with **SDK 35** and
  **Build-Tools 35.0.0**
- JDK 17 or newer

Build a debug APK:

```sh
./gradlew assembleDebug
```

Run unit tests:

```sh
./gradlew testDebugUnitTest
```

Install on a connected device or emulator:

```sh
./gradlew installDebug
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
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/fossscout/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── data/          # Project model, sample catalog, repository
│       │   │   └── ui/
│       │   │       ├── FossScoutApp.kt
│       │   │       ├── about/
│       │   │       ├── components/
│       │   │       ├── discover/
│       │   │       └── theme/
│       │   └── res/               # Strings, themes, launcher icon
│       └── test/                  # JVM unit tests
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Roadmap

- Replace the bundled catalog with a remote, community-maintained data source
- Persist saved projects with DataStore or Room
- Add project detail screens
- Add tests around the ViewModel and repository
- Set up CI for builds and tests

## License

Released under the [MIT License](LICENSE).
