# Flavors - FossScout

FossScout supports two build flavors with different distribution channels.
The GitHub beta is the only enabled release path; Play and F-Droid releases
remain intentionally disabled.

## Flavors
| Flavor | Application ID | Purpose | Notes |
|---|---|---|---|
| `playstore` | `com.fossscout.app` | Google Play Store | Play-safe feature set |
| `fdroid` | `com.fossscout.app.fdroid` | F-Droid | F-Droid-safe feature set; release path not enabled yet |

## Beta Variant
- `playstoreBeta` variant uses application ID `com.fossscout.app.beta` with `-beta` version suffix
- Allows installation alongside production
- The GitHub release workflow runs only for tags matching `v*-beta*`; the variant can still be built locally

## Build Commands
```bash
# PlayStore
./gradlew assemblePlaystoreDebug
./gradlew assemblePlaystoreRelease

# F-Droid
./gradlew assembleFdroidDebug
./gradlew assembleFdroidRelease

# Beta (PlayStore beta)
./gradlew assemblePlaystoreBeta
```

## Versioning
Version code/name can be overridden via CI environment variables (`CI_VERSION_CODE`, `CI_VERSION_NAME`).

## Signing
Release signing is configured conditionally via environment variables/secrets. Debug builds use default debug signing.
