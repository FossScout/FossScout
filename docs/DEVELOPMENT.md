# Development Guide - FossScout

This guide covers local setup and development for FossScout.

## Prerequisites
- JDK 17 (recommended)
- Android SDK
- Android Studio (optional but recommended)
- For KMP targets: platform toolchains as needed

## Setup
1. Clone the repository with submodules:
```bash
git clone --recurse-submodules https://github.com/FossScout/FossScout.git
```
For an existing clone, initialize the shared docs submodule:
```bash
git submodule update --init --recursive
```
On Windows, enable Developer Mode or run `git config --global core.symlinks true` before cloning so the documentation symlinks are created correctly.
2. Create `local.properties` in project root (gitignored):
```properties
sdk.dir=/path/to/android/sdk
```
3. Sync Gradle

## Flavors
FossScout has two flavors:
- `playstore` - Play Store build
- `fdroid` - F-Droid build

See [docs/FLAVORS.md](FLAVORS.md) for details.

## Build Commands
```bash
# Debug builds
./gradlew assemblePlaystoreDebug
./gradlew assembleFdroidDebug

# Both flavors debug
./gradlew assemblePlaystoreDebug assembleFdroidDebug

# Tests
./gradlew test
./gradlew testDebugUnitTest

# Lint
./gradlew lint
```

## Running
Use Android Studio or:
```bash
./gradlew installPlaystoreDebug
```

## Beta Builds
See [docs/RELEASING.md](RELEASING.md) for beta release process.

## Common Issues
- If Gradle sync fails, check JDK version
- For signing issues, ensure keystore is configured or use debug builds
- Clear `.gradle` and build caches if needed

## Contributing
See the shared [contributing guide](https://github.com/FossScout/best-practices/blob/main/guides/contributing.md).
